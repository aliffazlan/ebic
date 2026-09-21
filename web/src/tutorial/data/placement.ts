// The tutorial's placement zone, ported from the real game's rule rather than an
// invented shape: the server (WebInputHandler.computeLegalPlacementTiles) legalizes a
// hex disk of radius PLACEMENT_ZONE_RADIUS centered on a corner anchor
// (DefaultArrangement.anchorFor: PLAYER_ONE -> (-mapRadius, 0)), filtered to tiles that
// exist on the map and aren't occupied - recomputed after every accepted edit rather
// than fixed for the whole phase. "Frontmost" here is simply the highest q in that zone
// (FRONT_ROW_Q); the whole q=FRONT_ROW_Q column is kept empty by the initial layout so
// "place Thaddeus at the front" always has an unambiguous, always-available column.

import { axialKey, hexDistance, mapTiles } from "../../hex/HexMath";
import { MAP_RADIUS, MAP_ROW_LIMIT } from "./battle";
import type { PlacementStateSnapshot, PlacementUnitSnapshot, UnitType } from "../../types/contract";

/** Matches the real server's PLACEMENT_ZONE_RADIUS (WebInputHandler.java). */
const PLACEMENT_ZONE_RADIUS = 6;

/** Matches DefaultArrangement.anchorFor for PLAYER_ONE: (-mapRadius, 0). */
const PLACEMENT_ANCHOR = { q: -MAP_RADIUS, r: 0 };

/** The true frontmost column of the zone above - derived, not hand-copied, so it can
 * never drift out of sync with PLACEMENT_ANCHOR/PLACEMENT_ZONE_RADIUS again. */
export const FRONT_ROW_Q = PLACEMENT_ANCHOR.q + PLACEMENT_ZONE_RADIUS;

/** The player's legal placement zone for the given arrangement: a hex disk of radius
 * PLACEMENT_ZONE_RADIUS around the player's corner anchor, minus tiles occupied by any
 * OTHER unit. Pass the moving unit's own id as `excludeUnitId` so its current tile
 * counts as free (it's about to leave it), exactly like the server re-legalizing after
 * every edit. */
export function computePlacementLegalTiles(
  units: PlacementUnitSnapshot[],
  excludeUnitId?: string,
): { q: number; r: number }[] {
  const occupied = new Set(
    units.filter((u) => u.unitId !== excludeUnitId).map((u) => axialKey({ q: u.q, r: u.r })),
  );
  return mapTiles(MAP_RADIUS, MAP_ROW_LIMIT).filter(
    (t) => hexDistance(PLACEMENT_ANCHOR, t) <= PLACEMENT_ZONE_RADIUS && !occupied.has(axialKey(t)),
  );
}

function pu(unitId: string, name: string, definitionId: string, unitType: UnitType, q: number, r: number): PlacementUnitSnapshot {
  return { unitId, name, definitionId, unitType, q, r };
}

/** Default layout for the real roster size (1 champion + 3 elites + 10 basics = 14
 * units), filling the zone's back columns and leaving the whole front column
 * (q=FRONT_ROW_Q) empty for the scripted "place Thaddeus at the front" step. */
export function initialPlacement(): PlacementStateSnapshot {
  const units = [
    // Champion on the corner anchor, elites on its 3 in-bounds neighbors - mirrors the
    // real DefaultArrangement.compute shape. Thaddeus is kept on r=0 specifically so the
    // placement-free dev-jump fallback (which forces his q to FRONT_ROW_Q but leaves r
    // untouched) always lands inside the legal disk, not off its edge.
    pu("u-valor", "Valor", "valor", "CHAMPION", -7, 0),
    pu("u-thaddeus", "Thaddeus", "thaddeus", "ELITE", -6, 0),
    pu("u-evayne", "Evayne", "evayne", "ELITE", -6, -1),
    pu("u-auroth", "Auroth", "auroth", "ELITE", -7, 1),
    // The 10 nearest on-map tiles to the anchor by hex distance, matching the real
    // DefaultArrangement.nextFreePositions ring-by-ring expansion exactly (verified via
    // a faithful reimplementation of GameMap.getTilesInRadius/getAdjacentTiles): all 5
    // distance-2 tiles, then the first 5 (of 7) distance-3 tiles. Order within is
    // arbitrary (basics are visually identical), same as the real algorithm's own roster-
    // order tie-break.
    pu("u-basic-1", "Basic", "basic", "BASIC", -7, 2),
    pu("u-basic-2", "Basic", "basic", "BASIC", -6, 1),
    pu("u-basic-3", "Basic", "basic", "BASIC", -5, -2),
    pu("u-basic-4", "Basic", "basic", "BASIC", -5, -1),
    pu("u-basic-5", "Basic", "basic", "BASIC", -5, 0),
    pu("u-basic-6", "Basic", "basic", "BASIC", -7, 3),
    pu("u-basic-7", "Basic", "basic", "BASIC", -6, 2),
    pu("u-basic-8", "Basic", "basic", "BASIC", -5, 1),
    pu("u-basic-9", "Basic", "basic", "BASIC", -4, -3),
    pu("u-basic-10", "Basic", "basic", "BASIC", -4, -2),
  ];
  return {
    confirmed: false,
    legalTiles: computePlacementLegalTiles(units),
    units,
    mapRadius: MAP_RADIUS,
    mapRowLimit: MAP_ROW_LIMIT,
  };
}
