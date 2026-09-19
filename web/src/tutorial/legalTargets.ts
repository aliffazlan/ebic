// Generic (not gate-aware) legal-target computation for the tutorial's synthetic
// "action" prompts. Real legality is normally computed server-side; here it exists
// purely so Board/Hud highlight tiles/units the way a real match would - deciding
// whether a given click actually PROGRESSES the script is a separate concern, owned
// entirely by TutorialRunner's gates. This keeps "does it look like a normal match"
// decoupled from "is this the specific action the current step expects".

import { AXIAL_DIRECTIONS, axialKey, hexDistance, mapTiles } from "../hex/HexMath";
import type { GameStateSnapshot, LegalTargetsByUnit, PromptPayload, Team } from "../types/contract";

/** The full "action" prompt for a freshly-pushed snapshot - shared by TutorialRunner
 * (after every mutation) and TutorialScript's own step transitions (battle-start,
 * enemy-turn, mid-battle) so both build prompts the same way. */
export function buildActionPrompt(snapshot: GameStateSnapshot): PromptPayload {
  return { kind: "action", team: snapshot.currentTeam, legalTargets: computeLegalTargets(snapshot, snapshot.currentTeam) };
}

export function computeLegalTargets(snapshot: GameStateSnapshot, team: Team): LegalTargetsByUnit {
  const validTiles = new Set(mapTiles(snapshot.mapRadius, snapshot.mapRowLimit).map(axialKey));
  const occupied = new Set(snapshot.units.filter((u) => !u.dead).map((u) => axialKey({ q: u.q, r: u.r })));

  const result: LegalTargetsByUnit = {};
  for (const unit of snapshot.units) {
    if (unit.team !== team || unit.dead) continue;
    const perAbility: LegalTargetsByUnit[string] = {};

    for (const ability of unit.abilities) {
      if (ability.id === "move") {
        const tiles = AXIAL_DIRECTIONS.map((d) => ({ q: unit.q + d.q, r: unit.r + d.r })).filter(
          (t) => validTiles.has(axialKey(t)) && !occupied.has(axialKey(t)),
        );
        perAbility[ability.id] = { noTarget: false, unitIds: [], tiles };
      } else if (ability.id === "attack") {
        const min = Math.max(1, unit.minAttackRange);
        const inRange = snapshot.units.filter(
          (o) => o.team !== team && !o.dead && hexDistance(unit, o) >= min && hexDistance(unit, o) <= unit.attackRange,
        );
        perAbility[ability.id] = { noTarget: false, unitIds: inRange.map((o) => o.id), tiles: [] };
      } else if (!ability.passive) {
        // Any other active ability (e.g. Cold Embrace): approximate as "any living
        // unit within range", ally or enemy - good enough for board highlighting,
        // never the thing that decides whether a cast actually does anything.
        const inRange = snapshot.units.filter(
          (o) => !o.dead && (ability.range === -1 || hexDistance(unit, o) <= ability.range),
        );
        perAbility[ability.id] = { noTarget: ability.range === 0, unitIds: inRange.map((o) => o.id), tiles: [] };
      }
    }

    result[unit.id] = perAbility;
  }
  return result;
}
