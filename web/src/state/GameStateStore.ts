import { Store } from "./Store";
import type {
  DraftRoundSnapshot,
  GameStateSnapshot,
  PlacementStateSnapshot,
  PromptPayload,
  Team,
} from "../types/contract";

export interface MatchUiState {
  yourTeam: Team;
  connected: boolean;
  snapshot: GameStateSnapshot | null;
  draftRound: DraftRoundSnapshot | null;
  placementState: PlacementStateSnapshot | null;
  prompt: PromptPayload | null;
  messages: string[];
  gameOver: { winnerTeam: string; winnerName: string } | null;
  // Dual-purpose: selected unit during the match (for ability targeting) and
  // selected unit during placement (for swap/move) - the two modes never
  // overlap in time, so one field covers both without any ambiguity.
  selectedUnitId: string | null;
  selectedAbilityId: string | null;
}

const MAX_MESSAGES = 50;

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
      gameOver: null,
      selectedUnitId: null,
      selectedAbilityId: null,
    });
  }

  pushMessage(text: string): void {
    const messages = [...this.getState().messages, text].slice(-MAX_MESSAGES);
    this.setState({ messages });
  }

  /** Find a unit by id in the latest snapshot, if any. */
  findUnit(unitId: string | null) {
    if (!unitId) return null;
    return this.getState().snapshot?.units.find((u) => u.id === unitId) ?? null;
  }
}
