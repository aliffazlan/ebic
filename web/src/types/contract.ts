// Shared DTO/message shapes mirroring /home/aliffazlan/projects/ebic/API_CONTRACT.md exactly.
// This file is the single source of truth for contract types on the client side —
// keep it in lockstep with API_CONTRACT.md if the contract ever changes.

export type Team = "PLAYER_ONE" | "PLAYER_TWO";
export type UnitType = "CHAMPION" | "ELITE" | "BASIC";
export type MatchStatus = "LOBBY" | "DRAFTING" | "IN_PROGRESS" | "FINISHED";
export type Attribute = "STRENGTH" | "AGILITY" | "INTELLIGENCE";

// ---- HTTP ----

export interface AuthUser {
  userId: number;
  username: string;
  // The hero this player has asked to always be offered in the draft, or null for none.
  // See "Favourite unit" in API_CONTRACT.md for what the guarantee actually promises.
  favouriteUnit: string | null;
}

export interface UnitsResponse {
  units: UnitDefinitionSnapshot[];
}

export interface FavouriteUnitResponse {
  favouriteUnit: string | null;
}

export interface CreateMatchRequest {
  isPublic: boolean;
}

export interface CreateMatchResponse {
  matchId: string;
  joinCode: string;
  status: "LOBBY";
  isPublic: boolean;
}

export interface JoinMatchResponse {
  matchId: string;
  status: "LOBBY";
}

export interface StartLobbyResponse {
  matchId: string;
  status: "DRAFTING";
}

export interface SetVisibilityRequest {
  isPublic: boolean;
}

export interface SetVisibilityResponse {
  matchId: string;
  isPublic: boolean;
}

/** Status the public lobby browser shows - each raw match status gets its own distinct label. */
export type PublicLobbyDisplayStatus = "LOBBY" | "DRAFTING" | "IN PROGRESS";

export interface PublicLobbySummary {
  matchId: string;
  joinCode: string;
  status: PublicLobbyDisplayStatus;
  playerOneName: string;
  /** True once both seats are filled - a LOBBY that's full isn't joinable even though it hasn't started. */
  full: boolean;
}

export interface PublicLobbiesResponse {
  lobbies: PublicLobbySummary[];
}

/** Status the rejoin list shows - LOBBY is deliberately excluded, since leaving a
 * not-yet-started lobby already has its own explicit leave flow. */
export type RejoinableMatchDisplayStatus = "DRAFTING" | "IN PROGRESS";

export interface RejoinableMatchSummary {
  matchId: string;
  status: RejoinableMatchDisplayStatus;
  team: Team;
  opponentName: string;
  /** True if this seat currently has a live connection elsewhere (e.g. another tab) -
   * the client should not offer to rejoin a match it's already connected to. */
  connected: boolean;
  /** A sandbox has no opponent - `opponentName` is just the caller's own name. */
  isSandbox: boolean;
}

export interface RejoinableMatchesResponse {
  matches: RejoinableMatchSummary[];
}

/**
 * Bot difficulty. Only one level exists today, but it travels through the API and is
 * stored on the match, so adding another is a server-side BotConfig entry rather than a
 * contract change.
 */
export type BotLevel = "STANDARD";

export interface CreateBotMatchResponse {
  matchId: string;
  status: "DRAFTING";
  level: BotLevel;
}

/** What a spectate request returns - both names, since a spectatable match is always IN_PROGRESS. */
export interface SpectateInfo {
  matchId: string;
  status: MatchStatus;
  playerOneName: string;
  playerTwoName: string;
}

export interface MatchInfo {
  matchId: string;
  status: MatchStatus;
  playerOneName: string;
  playerTwoName: string | null;
  yourTeam: Team;
  winnerName: string | null;
  isPublic: boolean;
  // One player holding both seats on an empty board - see the SANDBOX tab in Hud.
  isSandbox: boolean;
}

export interface CreateSandboxMatchResponse {
  matchId: string;
  status: MatchStatus;
}

export interface ApiErrorBody {
  error: string;
}

// ---- WebSocket: server -> client payload shapes ----

export interface AbilitySnapshot {
  id: string;
  name: string;
  // Human-readable, fully-resolved text (every %stat_key% placeholder already
  // substituted server-side) - shown on hover, see "Ability tooltips" in
  // API_CONTRACT.md.
  description: string;
  // The verbose half of the tooltip, shown only while the expand key is held: one bullet
  // per rule or interaction lifted out of the description, plus this ability's raw tuning
  // numbers. Both empty for Move/Attack and anything else not built from a JSON definition.
  details: string[];
  stats: Record<string, number>;
  passive: boolean;
  // True once Shawl's Hidden Potential has unlocked this ability. `description`, `details`
  // and `stats` above are already the UPGRADED values by then, so this only says to render
  // them as special. Deliberately no companion field describing what an un-upgraded ability
  // WOULD become - that text exists only inside Shawl's own dialogue.
  upgraded: boolean;
  ready: boolean;
  // True when this one ability is locked for the rest of the turn by an effect
  // (Joker's Superior Mastery lets each of his abilities be cast only once per
  // turn), even though `ready` may read true because the cooldown is clear.
  usedThisTurn: boolean;
  currentCooldown: number;
  maxCooldown: number;
  // Move points this cast actually spends, decided server-side. 0 for a passive, for a
  // BASIC unit's move/attack, and for anything a banked Capacitor Bank charge will pay
  // for. Render this rather than re-deriving the rule - that is what keeps the button's
  // "No moves remaining" state honest.
  moveCost: number;
  // How far this ability reaches, so the client can outline the castable band rather
  // than only marking currently-legal targets. For a basic Attack this is the owner's
  // effective attack range. `minRange` is 0 for everything except an Attack under a
  // minimum-range effect (Artemis's Steady Focus).
  // -1 means the ability can be aimed anywhere on the map.
  range: number;
  minRange: number;
}

// Static ability info for a not-yet-drafted unit (draft/opponent-options cards) -
// no live match instance yet, so no ready/currentCooldown, just the nominal
// cooldown (or passive) worth showing before picking.
export interface AbilityPreviewSnapshot {
  id: string;
  name: string;
  description: string;
  // See AbilitySnapshot.details/stats - the same verbose-tooltip payload, before the match.
  details: string[];
  stats: Record<string, number>;
  passive: boolean;
  cooldown: number;
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
  // The other unit this effect is paired with - only set for Duel and Static
  // Link, whose board visuals connect two specific units. Null otherwise.
  partnerUnitId: string | null;
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
  currentBarrierHp: number;
  maxBarrierHp: number;
  // Effective (post-modifier) values, for the sidebar unit panel.
  strength: number;
  agility: number;
  intelligence: number;
  // How far this unit's basic attack reaches, in tiles (default 1). `minAttackRange`
  // is 0 for almost everything - Artemis's Steady Focus is the one thing that sets it,
  // forbidding attacks on targets closer than that.
  attackRange: number;
  minAttackRange: number;
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
  // Largest |r| that exists on the board. Equal to mapRadius for a regular hexagon;
  // lower when the top and bottom rows are trimmed off into an elongated one, in which
  // case being inside the radius no longer means a tile exists.
  mapRowLimit: number;
  units: UnitSnapshot[];
  // Persistent effects painted on the board itself rather than on a unit -
  // currently just Ember's Eruption leaving burning ground behind.
  tileEffects: TileEffectSnapshot[];
}

export interface TileEffectSnapshot {
  q: number;
  r: number;
  // "burning" is Ember's ground fire and "acid" is Shawl's brew; "eclipse" and "missile"
  // are warnings about something that has not landed yet (a pending Sanity's Eclipse blast,
  // a homing missile's current impact tile). Open-ended: an unrecognised kind draws a
  // generic overlay.
  kind: "burning" | "acid" | "eclipse" | "missile" | string;
  name: string;
  remainingTurns: number;
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
  // Only set on a "damage" event whose damage was passed on by a redirect mechanic
  // (Refraction) - names the unit that redirected it, distinct from sourceUnitId (the
  // original attacker) and targetUnitId (who it landed on). Optional/undefined on
  // every other event, rather than a required null like causeLabel, so the many
  // existing VfxEvent literals across fixtures/tests don't all need updating for a
  // field that's genuinely rare rather than always-applicable.
  redirectedFromUnitId?: string | null;
}

export interface UnitDefinitionSnapshot {
  definitionId: string;
  name: string;
  type: "CHAMPION" | "ELITE";
  maxHp: number;
  strength: number;
  agility: number;
  intelligence: number;
  attackRange: number;
  abilities: AbilityPreviewSnapshot[];
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
  // Present only for two-part abilities (Translocation): pick a unit, then one of
  // *that unit's* destinations. Absent for every ordinary ability. The server
  // enumerates both stages up front, so the client still computes no legality -
  // it only decides which of the two sets to highlight right now.
  multi?: {
    // What can be picked FIRST. A cast picks a unit then a tile (Translocation) or two tiles
    // (Eruption, Snow Golem), so exactly one of these two is populated.
    primaryUnitIds: string[];
    primaryTiles: { q: number; r: number }[];
    // Keyed by the first half: a unit id, or a "q,r" tile key. The key is opaque - echo it
    // back rather than parsing it; the client only ever needs to look a choice up by it.
    destinationsByPrimary: Record<string, { q: number; r: number }[]>;
  };
}
export type LegalTargetsByUnit = Record<string, Record<string, LegalTargets>>;

// "pick"/"placement" prompt kinds are gone - draft_round and placement_state
// double as their own prompts now (see below). Only the in-match action loop
// still uses this message. The `attribute` prompt is also the encounter
// trigger: it carries both `unitId` (whose choice this is) and
// `opponentUnitId` (the other party) so the client can look up both
// UnitSnapshots (already in the last "state" message, no fog of war once
// combat has started) and render the full encounter - see API_CONTRACT.md.
// A `choice` prompt is a generic "pick one of these" dialogue raised from inside an
// ability's own cast (Maxwell's Eureka choosing which gadget to construct). It carries
// everything needed to render itself, so the client needs no per-ability knowledge -
// respond with a `choice` message naming the option's id.
export interface ChoiceOption {
  id: string;
  name: string;
  description: string;
  // Secondary line, e.g. "Cooldown: 4 turns" or "Passive". May be null. For a disabled
  // option this is the reason it cannot be taken ("Already upgraded").
  detail: string | null;
  // False for an option shown but not choosable. Render it greyed out WITH its reason
  // rather than hiding it - Shawl's dialogue lists an ally's already-unlocked abilities so
  // the player can see why they are missing. The server refuses a disabled id.
  enabled: boolean;
}

export type PromptPayload =
  // `sandboxSpawnTiles` only ever appears in a sandbox match: every tile a unit may be
  // spawned on right now. The client highlights these and cancels a spawn click anywhere else.
  | { kind: "action"; team: Team; legalTargets: LegalTargetsByUnit; sandboxSpawnTiles?: { q: number; r: number }[] }
  // `selectableAttributes` is the subset this unit can actually bring - an attribute it
  // has none of is not a legal answer. Render the others disabled rather than hiding them,
  // so the player can see *why* the choice is constrained. A unit with none at all is
  // never prompted, so this is never empty.
  | {
      kind: "attribute";
      team: Team;
      unitId: string;
      opponentUnitId: string;
      selectableAttributes: Attribute[];
    }
  | { kind: "choice"; team: Team; unitId: string; title: string; options: ChoiceOption[] };

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
  // Real matches never set these (the server's placement map is always its
  // fixed default shape) - only a client driving its own local map, like the
  // scripted tutorial, needs to override Board's PLACEMENT_MAP_RADIUS/
  // PLACEMENT_MAP_ROW_LIMIT fallback.
  mapRadius?: number;
  mapRowLimit?: number;
}

// One render tick's vfx batch (possibly empty) paired with the currentTeam its "state"
// snapshot named - sent only as replay history to a newly-connecting spectator (see
// ChannelHub.registerSpectator on the server), never as part of the live vfx/state stream a
// player sees. Lets MatchScreen rebuild a spectator's combat log via the exact same
// appendCombatLog/commitCombatLog calls a live player's client makes, without touching the
// board or its animations.
export interface CombatLogBatch {
  events: VfxEvent[];
  currentTeam: Team;
}

export type ServerMessage =
  | { type: "state"; payload: GameStateSnapshot }
  | { type: "vfx"; payload: VfxEvent[] }
  | { type: "draft_round"; payload: DraftRoundSnapshot }
  | { type: "placement_state"; payload: PlacementStateSnapshot }
  | { type: "prompt"; payload: PromptPayload }
  | { type: "message"; text: string }
  | { type: "game_over"; payload: { winnerTeam: string; winnerName: string } }
  | { type: "combat_log_batch"; payload: CombatLogBatch };

// ---- WebSocket: client -> server ----

export type PlacementEdit =
  | { kind: "swap"; unitId: string; targetUnitId: string }
  | { kind: "move"; unitId: string; q: number; r: number }
  | { kind: "confirm" };

// Sandbox-only tools. The server refuses these outside a sandbox match.
export type SandboxTool =
  | { tool: "spawn"; team: Team; definitionId: string; q: number; r: number }
  | { tool: "remove"; unitId: string }
  | { tool: "heal"; unitId: string }
  | { tool: "clear" }
  | { tool: "refill_moves" }
  | { tool: "reset_cooldowns" }
  | { tool: "switch_team" };

export type ClientMessage =
  | { type: "ping" }
  | { type: "action"; kind: "end_turn" }
  | {
      type: "action";
      kind: "ability";
      unitId: string;
      abilityId: string;
      // "multi" carries BOTH halves of a two-part cast in one message - targetUnitId
      // (who moves) and q/r (where to) - since the client has already picked both from
      // the prompt's own `multi` block by the time it submits.
      targetKind: "unit" | "tile" | "none" | "multi";
      // The first half of a "multi" cast: a unit, or - for a two-tile cast - a coordinate pair.
      // q/r are always the SECOND half.
      targetUnitId?: string;
      primaryQ?: number;
      primaryR?: number;
      q?: number;
      r?: number;
    }
  // `team` is only sent in a sandbox, where one socket answers for both seats and the
  // server can't otherwise tell whose pick this is.
  | { type: "attribute"; value: Attribute; team?: Team }
  // `cancel` closes a choice dialogue without picking anything, at no cost - always
  // available, including when every option is disabled.
  | { type: "choice"; optionId?: string; cancel?: boolean; team?: Team }
  | ({ type: "action"; kind: "sandbox" } & SandboxTool)
  | { type: "pick"; definitionId: string }
  | ({ type: "placement_edit" } & PlacementEdit);
