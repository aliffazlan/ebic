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

// Active effect on a unit, for the sidebar effects list (replaces rendering
// statusFlags directly - see API_CONTRACT.md's "Effects sidebar" section).
export interface EffectSnapshot {
  name: string;
  description: string;
  category: "BUFF" | "DEBUFF" | "NEUTRAL";
  permanent: boolean;
  remainingTurns: number;
  statusFlags: string[];
  // Dynamic per-instance state not captured by the static description, e.g.
  // "Next hit: 12 damage" (Doom), "Barrier: 8 HP remaining" - null when the
  // effect has nothing dynamic to add.
  extraInfo: string | null;
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
  // Effective (post-modifier) values, for the sidebar unit panel.
  strength: number;
  agility: number;
  intelligence: number;
  dead: boolean;
  hasMovedThisTurn: boolean;
  hasAttackedThisTurn: boolean;
  // Kept for internal blocks-this-action checks, but the UI should no longer
  // render these raw names directly - use `effects` below instead.
  statusFlags: string[];
  abilities: AbilitySnapshot[];
  effects: EffectSnapshot[];
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
  // Only set on "damage" events - human-readable damage source for the
  // combat log, e.g. "Attack", "Poison", "Counterstrike".
  causeLabel: string | null;
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

// Draft and placement run independently per player (no more synchronized
// "both players see this round together" moment) - see API_CONTRACT.md's
// WebSocket section. `draft_round` now carries only this player's own
// options, and receiving it doubles as the prompt to pick. `opponentOptions`
// is the opponent's same-round options shown alongside for transparency -
// the whole pool is already decided the moment both players join, so this
// needs no synchronization with the opponent's actual progress.
export interface DraftRoundSnapshot {
  roundLabel: string; // "Champion", "Elite 1/3", "Elite 2/3", "Elite 3/3"
  options: UnitDefinitionSnapshot[];
  opponentOptions: UnitDefinitionSnapshot[];
}

// Per unitId, per abilityId: which tiles/units are actually legal to target, so the
// board can highlight them instead of accept-then-reject on a bad click. Only present
// on "action" prompts, and only for the acting player's own ready active abilities.
export interface LegalTargets {
  noTarget: boolean;
  unitIds: string[];
  tiles: { q: number; r: number }[];
}
export type LegalTargetsByUnit = Record<string, Record<string, LegalTargets>>;

// "pick"/"placement" prompt kinds are gone - draft_round and placement_state
// double as their own prompts now (see below). Only the in-match action loop
// still uses this message. The `attribute` prompt is also the encounter
// trigger: it carries both `unitId` (whose choice this is) and
// `opponentUnitId` (the other party) so the client can look up both
// UnitSnapshots (already in the last "state" message, no fog of war once
// combat has started) and render the full encounter - see API_CONTRACT.md.
export type PromptPayload =
  | { kind: "action"; team: Team; legalTargets: LegalTargetsByUnit }
  | { kind: "attribute"; team: Team; unitId: string; opponentUnitId: string };

// This player's own working placement arrangement only (fog of war - the
// opponent's roster/positions are never sent here). Pushed once with the
// server's default layout, then again after every accepted edit.
export interface PlacementUnitSnapshot {
  unitId: string;
  name: string;
  definitionId: string;
  unitType: UnitType;
  q: number;
  r: number;
}

// `legalTiles` is the full set of tiles a `move` edit may target - constant
// for the whole placement phase, included on every push purely so the client
// can highlight the zone without a separate request.
export interface PlacementStateSnapshot {
  units: PlacementUnitSnapshot[];
  confirmed: boolean;
  legalTiles: { q: number; r: number }[];
}

export type ServerMessage =
  | { type: "state"; payload: GameStateSnapshot }
  | { type: "vfx"; payload: VfxEvent[] }
  | { type: "draft_round"; payload: DraftRoundSnapshot }
  | { type: "placement_state"; payload: PlacementStateSnapshot }
  | { type: "prompt"; payload: PromptPayload }
  | { type: "message"; text: string }
  | { type: "game_over"; payload: { winnerTeam: string; winnerName: string } };

// ---- WebSocket: client -> server ----

export type PlacementEdit =
  | { kind: "swap"; unitId: string; targetUnitId: string }
  | { kind: "move"; unitId: string; q: number; r: number }
  | { kind: "confirm" };

export type ClientMessage =
  | { type: "ping" }
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
  | ({ type: "placement_edit" } & PlacementEdit);
