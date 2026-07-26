import { Store } from "./Store";
import type {
  DraftRoundSnapshot,
  GameStateSnapshot,
  PromptPayload,
  Team,
} from "../types/contract";

export interface MatchUiState {
  yourTeam: Team;
  connected: boolean;
  snapshot: GameStateSnapshot | null;
  draftRound: DraftRoundSnapshot | null;
  prompt: PromptPayload | null;
  messages: string[];
  gameOver: { winnerTeam: string; winnerName: string } | null;
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
