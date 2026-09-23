import { Store } from "./Store";
import { buildCombatLogLines, type CombatLogEntry, type CombatLogSegment } from "./CombatLog";
import type {
  DraftRoundSnapshot,
  GameStateSnapshot,
  PlacementStateSnapshot,
  PromptPayload,
  Team,
  VfxEvent,
} from "../types/contract";

export type AttributePrompt = Extract<PromptPayload, { kind: "attribute" }>;

/**
 * A sandbox tool waiting on a board click. Spawn wants a tile, remove/heal want a unit;
 * a click on anything else cancels it.
 */
export type SandboxToolMode =
  | { kind: "spawn"; team: Team; definitionId: string; name: string }
  | { kind: "remove" }
  | { kind: "heal" };

export interface MatchUiState {
  // Null for a spectator - a read-only viewer who is not one of the two seated players.
  // Every existing comparison against a real Team is naturally always false for null, which
  // is what already disables ability/end-turn/attribute-prompt interaction for spectators
  // without needing its own separate gating - see isSpectator below for the cases (labels,
  // badges) that DO need an explicit branch.
  yourTeam: Team | null;
  isSpectator: boolean;
  playerOneName: string;
  playerTwoName: string;
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
  combatLogPages: CombatLogEntry[][];
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
  // One player holding both seats. `yourTeam` then follows whoever's turn it is (see
  // MatchScreen), which is what lets every existing "is it your turn / your unit" check
  // work unchanged for whichever side is acting.
  isSandbox: boolean;
  // Sandbox only: an encounter asks BOTH seats at once, and here both are this client - so
  // each side's prompt is kept by team rather than in the single `prompt` slot, which still
  // holds one of them so the usual "an encounter is open" gating keeps working.
  sandboxAttributePrompts: Partial<Record<Team, AttributePrompt>>;
  sandboxAttributeSubmitted: Team[];
  sandboxTool: SandboxToolMode | null;
  // Which side the unit picker is open for, or null while it's closed.
  sandboxPicker: Team | null;
}

const MAX_MESSAGES = 50;
// Cap on total *pages* kept in memory (not lines-per-page) - a real match
// won't come anywhere near this, it's just a defensive ceiling.
const MAX_COMBAT_LOG_PAGES = 100;


/** Holds the latest known state of one match; Board and Hud both subscribe to it. */
export class GameStateStore extends Store<MatchUiState> {
  // Tracks the currentTeam seen on the previous "state" message, purely to
  // detect a PLAYER_TWO -> PLAYER_ONE transition (a round just completed) for
  // combat-log pagination - see startNewCombatLogPageIfRoundJustCompleted.
  // Not part of MatchUiState since nothing renders off it directly.
  private lastSeenCurrentTeam: Team | null = null;
  // Lines built from a vfx batch, waiting for the state message that says whose
  // turn they belong to - see commitCombatLog.
  private pendingLogLines: CombatLogSegment[][] = [];

  constructor(yourTeam: Team | null, playerOneName: string, playerTwoName: string, isSandbox = false) {
    super({
      yourTeam,
      isSpectator: yourTeam === null,
      playerOneName,
      playerTwoName,
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
      isSandbox,
      sandboxAttributePrompts: {},
      sandboxAttributeSubmitted: [],
      sandboxTool: null,
      sandboxPicker: null,
    });
  }

  pushMessage(text: string): void {
    const messages = [...this.getState().messages, text].slice(-MAX_MESSAGES);
    this.setState({ messages });
  }

  /**
   * Turns a batch of "vfx" events into combat-log lines and buffers them until
   * the "state" message behind them lands.
   *
   * Must be called before that state is applied (vfx always arrives first over
   * the wire) since it resolves unit names against the *pre-update* snapshot -
   * the same assumption Board.playVfx makes. It cannot commit them, though:
   * which turn a line belongs to is only knowable from the state that follows.
   * See commitCombatLog.
   */
  appendCombatLog(events: VfxEvent[]): void {
    this.pendingLogLines.push(...buildCombatLogLines(events, (id) => this.findUnit(id)));
  }

  /**
   * Files every buffered line under `currentTeam` and, if a round just
   * completed, opens a new page first.
   *
   * Why the buffering: a turn's damage-over-time ticks are broadcast in the
   * same server render as the state that hands the turn over, so at
   * appendCombatLog time `snapshot.currentTeam` is still the *outgoing*
   * player. Tagging then would file every turn-start poison and burn under the
   * player who just finished. The state message that follows names the right
   * team, so lines wait for it.
   *
   * Page rollover happens before the commit on purpose: a PLAYER_TWO ->
   * PLAYER_ONE flip means a fresh round, and the pending lines are that new
   * round's turn-start ticks, so they belong on the new page.
   */
  commitCombatLog(currentTeam: Team): void {
    const previous = this.lastSeenCurrentTeam;
    this.lastSeenCurrentTeam = currentTeam;
    const roundCompleted = previous === "PLAYER_TWO" && currentTeam === "PLAYER_ONE";

    if (!roundCompleted && this.pendingLogLines.length === 0) return;

    let pages = this.getState().combatLogPages;
    let viewedLogPage = this.getState().viewedLogPage;
    if (roundCompleted) {
      pages = [...pages, []].slice(-MAX_COMBAT_LOG_PAGES);
      viewedLogPage = pages.length - 1;
    }

    if (this.pendingLogLines.length > 0) {
      const entries: CombatLogEntry[] = this.pendingLogLines.map((segments) => ({ segments, team: currentTeam }));
      this.pendingLogLines = [];
      const lastIndex = pages.length - 1;
      pages = pages.slice(0, lastIndex).concat([[...pages[lastIndex], ...entries]]);
    }

    this.setState({ combatLogPages: pages, viewedLogPage });
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
