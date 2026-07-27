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
  // Append-only combat log, built client-side off the "vfx" stream (see
  // API_CONTRACT.md's "Combat log" section) - deliberately a separate list
  // from `messages` (system/reject messages), survives across snapshots.
  combatLog: string[];
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
}

const MAX_MESSAGES = 50;
const MAX_COMBAT_LOG = 200;

/** Holds the latest known state of one match; Board and Hud both subscribe to it. */
export class GameStateStore extends Store<MatchUiState> {
  constructor(yourTeam: Team) {
    super({
      yourTeam,
      connected: false,
      snapshot: null,
      draftRound: null,
      placementState: null,
      prompt: null,
      messages: [],
      combatLog: [],
      attributeSubmitted: false,
      gameOver: null,
      selectedUnitId: null,
      selectedAbilityId: null,
    });
  }

  pushMessage(text: string): void {
    const messages = [...this.getState().messages, text].slice(-MAX_MESSAGES);
    this.setState({ messages });
  }

  /**
   * Turns a batch of "vfx" events into combat-log lines and appends them.
   * Must be called before the corresponding "state" message is applied (vfx
   * always arrives first over the wire) since it resolves unit names against
   * the *pre-update* snapshot - same assumption Board.playVfx already makes.
   */
  appendCombatLog(events: VfxEvent[]): void {
    const lines: string[] = [];
    for (const event of events) {
      if (event.type !== "damage") continue;
      const amount = event.amount ?? 0;
      const targetName = event.targetUnitId ? (this.findUnit(event.targetUnitId)?.name ?? "Unknown") : "Unknown";
      if (event.causeLabel === "Attack") {
        const sourceName = event.sourceUnitId ? (this.findUnit(event.sourceUnitId)?.name ?? "Unknown") : "Unknown";
        lines.push(`${sourceName} attacks ${targetName} for ${amount} damage`);
      } else if (event.causeLabel) {
        lines.push(`${targetName} takes ${amount} damage from ${event.causeLabel}`);
      } else {
        // Rare/never in practice per API_CONTRACT.md - no cause label at all.
        lines.push(`${targetName} takes ${amount} damage`);
      }
    }
    if (lines.length === 0) return;
    const combatLog = [...this.getState().combatLog, ...lines].slice(-MAX_COMBAT_LOG);
    this.setState({ combatLog });
  }

  /** Find a unit by id in the latest snapshot, if any. */
  findUnit(unitId: string | null) {
    if (!unitId) return null;
    return this.getState().snapshot?.units.find((u) => u.id === unitId) ?? null;
  }
}
