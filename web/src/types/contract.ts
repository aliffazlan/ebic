// Shared DTO/message shapes mirroring /home/aliffazlan/projects/ebic/API_CONTRACT.md exactly.
// This file is the single source of truth for contract types on the client side —
// keep it in lockstep with API_CONTRACT.md if the contract ever changes.

export type Team = "PLAYER_ONE" | "PLAYER_TWO";
export type UnitType = "CHAMPION" | "ELITE" | "BASIC";
export type MatchStatus = "WAITING" | "DRAFTING" | "IN_PROGRESS" | "FINISHED";
export type Attribute = "STRENGTH" | "AGILITY" | "INTELLIGENCE";

// ---- HTTP ----

export interface AuthUser {
  userId: number;
  username: string;
}

export interface CreateMatchResponse {
  matchId: string;
  joinCode: string;
  status: "WAITING";
}

export interface JoinMatchResponse {
  matchId: string;
  status: "DRAFTING";
}

export interface MatchInfo {
  matchId: string;
  status: MatchStatus;
  playerOneName: string;
  playerTwoName: string | null;
  yourTeam: Team;
  winnerName: string | null;
}

export interface ApiErrorBody {
  error: string;
}

// ---- WebSocket: server -> client payload shapes ----

export interface AbilitySnapshot {
  id: string;
  name: string;
  passive: boolean;
  ready: boolean;
  currentCooldown: number;
  maxCooldown: number;
}

export interface UnitSnapshot {
  id: string;
  name: string;
  definitionId: string;
  team: Team;
  unitType: UnitType;
  q: number;
  r: number;
  currentHp: number;
  maxHp: number;
  dead: boolean;
  hasMovedThisTurn: boolean;
  hasAttackedThisTurn: boolean;
  statusFlags: string[];
  abilities: AbilitySnapshot[];
}

export interface GameStateSnapshot {
  currentTeam: Team;
  remainingMoves: number;
  gameOver: boolean;
  mapRadius: number;
  units: UnitSnapshot[];
}

export type VfxType = "ability_used" | "damage" | "death" | "status_applied" | "heal";

export interface VfxEvent {
  type: VfxType;
  abilityId: string | null;
  sourceUnitId: string | null;
  targetUnitId: string | null;
  amount: number | null;
}

export interface UnitDefinitionSnapshot {
  definitionId: string;
  name: string;
  type: "CHAMPION" | "ELITE";
  maxHp: number;
  strength: number;
  agility: number;
  intelligence: number;
  abilities: string[];
}

export interface DraftRoundSnapshot {
  roundLabel: string;
  yourOptions: UnitDefinitionSnapshot[];
  opponentOptions: UnitDefinitionSnapshot[];
}

export type PromptKind = "action" | "attribute" | "pick" | "placement";

// The contract only pins down `kind` precisely; kind-specific fields are
// described loosely ("...kind-specific"). Treat the rest as an open bag of
// unknown fields and be defensive about reading anything beyond `kind`.
export interface PromptPayload {
  kind: PromptKind;
  [key: string]: unknown;
}

export type ServerMessage =
  | { type: "state"; payload: GameStateSnapshot }
  | { type: "vfx"; payload: VfxEvent[] }
  | { type: "draft_round"; payload: DraftRoundSnapshot }
  | { type: "prompt"; payload: PromptPayload }
  | { type: "message"; text: string }
  | { type: "game_over"; payload: { winnerTeam: string; winnerName: string } };

// ---- WebSocket: client -> server ----

export type ClientMessage =
  | { type: "action"; kind: "end_turn" }
  | {
      type: "action";
      kind: "ability";
      unitId: string;
      abilityId: string;
      targetKind: "unit" | "tile" | "none";
      targetUnitId?: string;
      q?: number;
      r?: number;
    }
  | { type: "attribute"; value: Attribute }
  | { type: "pick"; definitionId: string }
  | { type: "placement"; q: number; r: number };
