// Battle-start GameStateSnapshot: both armies' opening formation. The player's half
// (battleSnapshotFromPlacement) is built from whatever the player actually left in
// PlacementStateSnapshot.units after the placement phase, so battle starts from their
// real arrangement rather than a re-scripted one. battleStartSnapshot() is only the
// fallback for a direct ?tutorialStep=N dev-jump into battle-basics/battle-elites,
// where no PlacementStateSnapshot exists yet - its player half is hand-copied from
// placement.ts's initialPlacement() (the default arrangement) so that fallback still
// renders a sensible board. Both builders share the same enemy half (enemyStartUnits),
// which is the player's shape rotated 180 degrees ((q,r) -> (-q,-r)), matching
// DefaultArrangement.anchorFor's PLAYER_TWO corner at (+radius, 0).
//
// mapRadius/mapRowLimit=7/5 exactly matches a real match's own map (Game.java's
// FULL_MATCH_MAP_RADIUS/FULL_MATCH_MAP_ROW_LIMIT), rather than an invented shape.
// Every coordinate below is verified against HexMath.mapTiles for this radius/rowLimit -
// a hex corner's near columns have an asymmetric r-range (e.g. q=-6 only allows r>=-1,
// not r>=-radius), so positions can't just be eyeballed against the radius alone.

import { coldEmbraceAbility, makeUnit } from "./units";
import type { GameStateSnapshot, PlacementUnitSnapshot, UnitSnapshot } from "../../types/contract";

export const MAP_RADIUS = 7;
export const MAP_ROW_LIMIT = 5;

function enemyStartUnits(): UnitSnapshot[] {
  return [
    makeUnit({ id: "e-harbinger", name: "Harbinger", definitionId: "harbinger", team: "PLAYER_TWO", unitType: "CHAMPION", q: 7, r: 0 }),
    makeUnit({ id: "e-dirge", name: "Dirge", definitionId: "dirge", team: "PLAYER_TWO", unitType: "ELITE", q: 6, r: 0 }),
    makeUnit({ id: "e-grivath", name: "Grivath", definitionId: "grivath", team: "PLAYER_TWO", unitType: "ELITE", q: 6, r: 1 }),
    makeUnit({ id: "e-discharge", name: "Discharge", definitionId: "discharge", team: "PLAYER_TWO", unitType: "ELITE", q: 7, r: -1 }),
    makeUnit({ id: "e-basic-1", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 7, r: -2 }),
    makeUnit({ id: "e-basic-2", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 7, r: -3 }),
    makeUnit({ id: "e-basic-3", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 7, r: -4 }),
    makeUnit({ id: "e-basic-4", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 7, r: -5 }),
    makeUnit({ id: "e-basic-5", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 6, r: -1 }),
    makeUnit({ id: "e-basic-6", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 6, r: -2 }),
    makeUnit({ id: "e-basic-7", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 6, r: -3 }),
    makeUnit({ id: "e-basic-8", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 6, r: -4 }),
    makeUnit({ id: "e-basic-9", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 6, r: -5 }),
    makeUnit({ id: "e-basic-10", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 5, r: 2 }),
  ];
}

export function battleStartSnapshot(): GameStateSnapshot {
  return {
    currentTeam: "PLAYER_ONE",
    remainingMoves: 3,
    gameOver: false,
    mapRadius: MAP_RADIUS,
    mapRowLimit: MAP_ROW_LIMIT,
    tileEffects: [],
    units: [
      makeUnit({ id: "u-valor", name: "Valor", definitionId: "valor", team: "PLAYER_ONE", unitType: "CHAMPION", q: -7, r: 0 }),
      makeUnit({ id: "u-thaddeus", name: "Thaddeus", definitionId: "thaddeus", team: "PLAYER_ONE", unitType: "ELITE", q: -6, r: 0 }),
      makeUnit({ id: "u-evayne", name: "Evayne", definitionId: "evayne", team: "PLAYER_ONE", unitType: "ELITE", q: -6, r: -1 }),
      makeUnit({
        id: "u-auroth",
        name: "Auroth",
        definitionId: "auroth",
        team: "PLAYER_ONE",
        unitType: "ELITE",
        q: -7,
        r: 1,
        extraAbilities: [coldEmbraceAbility()],
      }),
      makeUnit({ id: "u-basic-1", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -7, r: 2 }),
      makeUnit({ id: "u-basic-2", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -7, r: 3 }),
      makeUnit({ id: "u-basic-3", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -7, r: 4 }),
      makeUnit({ id: "u-basic-4", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -7, r: 5 }),
      makeUnit({ id: "u-basic-5", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -6, r: 1 }),
      makeUnit({ id: "u-basic-6", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -6, r: 2 }),
      makeUnit({ id: "u-basic-7", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -6, r: 3 }),
      makeUnit({ id: "u-basic-8", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -6, r: 4 }),
      makeUnit({ id: "u-basic-9", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -6, r: 5 }),
      makeUnit({ id: "u-basic-10", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -5, r: -2 }),
      ...enemyStartUnits(),
    ],
  };
}

/** Builds the battle-start snapshot from the player's actual PlacementStateSnapshot.units
 * (wherever they left each unit during placement-free), rather than a re-scripted layout.
 * The enemy half is always the same fixed enemyStartUnits() - only placed by the player. */
export function battleSnapshotFromPlacement(placementUnits: PlacementUnitSnapshot[]): GameStateSnapshot {
  return {
    currentTeam: "PLAYER_ONE",
    remainingMoves: 3,
    gameOver: false,
    mapRadius: MAP_RADIUS,
    mapRowLimit: MAP_ROW_LIMIT,
    tileEffects: [],
    units: [
      ...placementUnits.map((u) =>
        makeUnit({
          id: u.unitId,
          name: u.name,
          definitionId: u.definitionId,
          team: "PLAYER_ONE",
          unitType: u.unitType,
          q: u.q,
          r: u.r,
          extraAbilities: u.unitId === "u-auroth" ? [coldEmbraceAbility()] : undefined,
        }),
      ),
      ...enemyStartUnits(),
    ],
  };
}

/** Enemy positions after their scripted turn-1 advance (all units step 1 tile toward the player). */
export function enemyAdvancedPositions(): Record<string, { q: number; r: number }> {
  return {
    "e-harbinger": { q: 6, r: 0 },
    "e-dirge": { q: 5, r: 0 },
    "e-grivath": { q: 5, r: 1 },
    "e-discharge": { q: 6, r: -1 },
    "e-basic-1": { q: 6, r: -2 },
    "e-basic-2": { q: 6, r: -3 },
    "e-basic-3": { q: 6, r: -4 },
    "e-basic-4": { q: 6, r: -5 },
    "e-basic-5": { q: 5, r: -1 },
    "e-basic-6": { q: 5, r: -2 },
    "e-basic-7": { q: 5, r: -3 },
    "e-basic-8": { q: 5, r: -4 },
    "e-basic-9": { q: 5, r: -5 },
    "e-basic-10": { q: 4, r: 2 },
  };
}
