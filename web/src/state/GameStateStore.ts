import { Store } from "./Store";
import type {
  DraftRoundSnapshot,
  GameStateSnapshot,
  PlacementStateSnapshot,
  PromptPayload,
  Team,
  VfxEvent,
} from "../types/contract";

export interface MatchUiState {
  yourTeam: Team;
  connected: boolean;
  snapshot: GameStateSnapshot | null;
  draftRound: DraftRoundSnapshot | null;
  placementState: PlacementStateSnapshot | null;
  prompt: PromptPayload | null;
  messages: string[];
  // Combat log, built client-side off the "vfx" stream (see API_CONTRACT.md's
  // "Combat log" section) - deliberately a separate list from `messages`
  // (system/reject messages), survives across snapshots. Paginated one page
  // per full round (Player One's turn through the end of Player Two's turn -
  // see startNewCombatLogPageIfRoundJustCompleted) rather than one flat list.
  // New lines always append to the *last* page regardless of which page is
  // currently being viewed.
  combatLogPages: string[][];
  // Which page index renderCombatLog currently displays. Auto-follows the
  // latest page whenever a new one is created (a round just completed), but
  // is otherwise free for the user to navigate backward without snapping.
  viewedLogPage: number;
  // True once this client has sent its {"type":"attribute",...} response for
  // the currently-open `prompt` (kind "attribute") but hasn't yet seen a
  // message that supersedes it (a new prompt, or a vfx/state push signaling
  // the encounter resolved) - drives the "Waiting for other player..." UI
  // state while keeping the encounter card itself visible. See
  // API_CONTRACT.md's "Waiting for other player" state paragraph.
  attributeSubmitted: boolean;
  gameOver: { winnerTeam: string; winnerName: string } | null;
  // Dual-purpose: selected unit during the match (for ability targeting) and
  // selected unit during placement (for swap/move) - the two modes never
  // overlap in time, so one field covers both without any ambiguity.
  selectedUnitId: string | null;
  selectedAbilityId: string | null;
  // First half of a two-part cast (Translocation): the unit picked to be moved,
  // while the board waits for a destination click. Null at every other moment,
  // including for ordinary single-click abilities.
  multiPrimaryUnitId: string | null;
  /** The first tile of a two-tile cast, once picked. Mutually exclusive with the above. */
  multiPrimaryTile: { q: number; r: number } | null;
}

const MAX_MESSAGES = 50;
// Cap on total *pages* kept in memory (not lines-per-page) - a real match
// won't come anywhere near this, it's just a defensive ceiling.
const MAX_COMBAT_LOG_PAGES = 100;

// causeLabel values that come from the engine's rock-paper-scissors attribute
// resolution (see CLAUDE.md's "Encounter types" section) - a 0-damage event
// with one of these labels is a real RPS "miss" (attacker's attribute lost or
// tied unfavorably), not just an ability tick that happened to roll 0, so it
// gets the "missed their attack" phrasing instead of "takes 0 damage from X".
const ENCOUNTER_CAUSE_LABELS = new Set(["Attack", "Counterstrike", "Duel", "Cloak and Dagger"]);

/** Holds the latest known state of one match; Board and Hud both subscribe to it. */
export class GameStateStore extends Store<MatchUiState> {
  // Tracks the currentTeam seen on the previous "state" message, purely to
  // detect a PLAYER_TWO -> PLAYER_ONE transition (a round just completed) for
  // combat-log pagination - see startNewCombatLogPageIfRoundJustCompleted.
  // Not part of MatchUiState since nothing renders off it directly.
  private lastSeenCurrentTeam: Team | null = null;

  constructor(yourTeam: Team) {
    super({
      yourTeam,
      connected: false,
      snapshot: null,
      draftRound: null,
      placementState: null,
      prompt: null,
      messages: [],
      combatLogPages: [[]],
      viewedLogPage: 0,
      attributeSubmitted: false,
      gameOver: null,
      selectedUnitId: null,
      selectedAbilityId: null,
      multiPrimaryUnitId: null,
      multiPrimaryTile: null,
    });
  }

  pushMessage(text: string): void {
    const messages = [...this.getState().messages, text].slice(-MAX_MESSAGES);
    this.setState({ messages });
  }

  /**
   * Turns a batch of "vfx" events into combat-log lines and appends them to
   * the *last* page (regardless of which page is currently being viewed).
   * Must be called before the corresponding "state" message is applied (vfx
   * always arrives first over the wire) since it resolves unit names against
   * the *pre-update* snapshot - same assumption Board.playVfx already makes.
   */
  appendCombatLog(events: VfxEvent[]): void {
    const lines: string[] = [];
    for (const event of events) {
      if (event.type === "damage") {
        const amount = event.amount ?? 0;
        const targetName = event.targetUnitId ? (this.findUnit(event.targetUnitId)?.name ?? "Unknown") : "Unknown";
        const sourceName = event.sourceUnitId ? (this.findUnit(event.sourceUnitId)?.name ?? "Unknown") : "Unknown";
        if (amount === 0 && event.causeLabel && ENCOUNTER_CAUSE_LABELS.has(event.causeLabel)) {
          lines.push(`${sourceName} missed their attack on ${targetName}`);
        } else if (event.causeLabel === "Attack") {
          lines.push(`${sourceName} attacks ${targetName} for ${amount} damage`);
        } else if (event.causeLabel) {
          lines.push(`${targetName} takes ${amount} damage from ${event.causeLabel}`);
        } else {
          // Rare/never in practice per API_CONTRACT.md - no cause label at all.
          lines.push(`${targetName} takes ${amount} damage`);
        }
      } else if (event.type === "ability_used") {
        // "move" isn't interesting for a combat log; "attack" already gets
        // its own damage-line coverage above (the "Attack" causeLabel
        // branch) - logging both would be redundant.
        if (event.abilityId === "move" || event.abilityId === "attack") continue;
        const sourceUnit = event.sourceUnitId ? this.findUnit(event.sourceUnitId) : null;
        const sourceName = sourceUnit?.name ?? "Unknown";
        const abilityName =
          sourceUnit?.abilities.find((a) => a.id === event.abilityId)?.name ?? event.abilityId ?? "an ability";
        if (event.targetUnitId) {
          const targetName = this.findUnit(event.targetUnitId)?.name ?? "Unknown";
          lines.push(`${sourceName} cast ${abilityName} on ${targetName}`);
        } else {
          lines.push(`${sourceName} cast ${abilityName}`);
        }
      }
    }
    if (lines.length === 0) return;

    const pages = this.getState().combatLogPages;
    const lastIndex = pages.length - 1;
    const updatedPages = pages.slice(0, lastIndex).concat([[...pages[lastIndex], ...lines]]);
    this.setState({ combatLogPages: updatedPages });
  }

  /**
   * Call once per "state" message with its `currentTeam`, before it's applied
   * to `snapshot` - detects a PLAYER_TWO -> PLAYER_ONE transition (one full
   * round, Player One's turn through the end of Player Two's turn, just
   * completed) and pushes a fresh combat-log page, auto-following the view to
   * it. The very first "state" message a client ever sees just establishes
   * currentTeam = PLAYER_ONE for round 1 - not evidence a round completed -
   * so no page is pushed until an actual transition has been observed.
   */
  startNewCombatLogPageIfRoundJustCompleted(currentTeam: Team): void {
    const previous = this.lastSeenCurrentTeam;
    this.lastSeenCurrentTeam = currentTeam;
    if (previous !== "PLAYER_TWO" || currentTeam !== "PLAYER_ONE") return;

    const pages = [...this.getState().combatLogPages, []].slice(-MAX_COMBAT_LOG_PAGES);
    this.setState({ combatLogPages: pages, viewedLogPage: pages.length - 1 });
  }

  /** Moves the currently-viewed combat-log page by `delta`, clamped to valid bounds. */
  viewCombatLogPage(delta: number): void {
    const pages = this.getState().combatLogPages;
    const next = Math.min(pages.length - 1, Math.max(0, this.getState().viewedLogPage + delta));
    this.setState({ viewedLogPage: next });
  }

  /** Find a unit by id in the latest snapshot, if any. */
  findUnit(unitId: string | null) {
    if (!unitId) return null;
    return this.getState().snapshot?.units.find((u) => u.id === unitId) ?? null;
  }
}
