// The "time skip to mid battle" snapshot - a fully predetermined tableau per
// temp/tutorial.txt: several basics dead each side, everyone injured, two opposing
// basics adjacent, Evayne critical next to Grivath, Auroth 2 tiles from Evayne, Valor
// critical and adjacent to Harbinger. Distances verified against HexMath.hexDistance:
//   Valor(0,0)-Harbinger(1,0) = 1        Evayne(1,-1)-Grivath(2,-1) = 1
//   Auroth(1,-3)-Evayne(1,-1) = 2        u-basic-1(-2,0)-e-basic-1(-2,-1) = 1

import { coldEmbraceAbility, makeUnit } from "./units";
import { MAP_RADIUS, MAP_ROW_LIMIT } from "./battle";
import type { GameStateSnapshot } from "../../types/contract";

export function midBattleSnapshot(): GameStateSnapshot {
  return {
    currentTeam: "PLAYER_ONE",
    remainingMoves: 3,
    gameOver: false,
    mapRadius: MAP_RADIUS,
    mapRowLimit: MAP_ROW_LIMIT,
    tileEffects: [],
    units: [
      makeUnit({ id: "u-valor", name: "Valor", definitionId: "valor", team: "PLAYER_ONE", unitType: "CHAMPION", q: 0, r: 0, currentHp: 90 }),
      makeUnit({ id: "u-thaddeus", name: "Thaddeus", definitionId: "thaddeus", team: "PLAYER_ONE", unitType: "ELITE", q: -1, r: 0, currentHp: 380 }),
      makeUnit({ id: "u-evayne", name: "Evayne", definitionId: "evayne", team: "PLAYER_ONE", unitType: "ELITE", q: 1, r: -1, currentHp: 22 }),
      makeUnit({
        id: "u-auroth",
        name: "Auroth",
        definitionId: "auroth",
        team: "PLAYER_ONE",
        unitType: "ELITE",
        q: 1,
        r: -3,
        currentHp: 480,
        extraAbilities: [coldEmbraceAbility()],
      }),
      makeUnit({ id: "u-basic-1", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -2, r: 0, currentHp: 12 }),
      makeUnit({ id: "u-basic-2", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -1, r: 1, currentHp: 18 }),
      makeUnit({ id: "u-basic-3", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -5, r: 1, currentHp: 0, dead: true }),
      makeUnit({ id: "u-basic-4", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -5, r: 2, currentHp: 0, dead: true }),
      makeUnit({ id: "u-basic-5", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -6, r: -1, currentHp: 0, dead: true }),
      makeUnit({ id: "u-basic-6", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -3, r: -1, currentHp: 45 }),
      makeUnit({ id: "u-basic-7", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -3, r: 1, currentHp: 50 }),
      makeUnit({ id: "u-basic-8", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -2, r: -2, currentHp: 35 }),
      makeUnit({ id: "u-basic-9", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -4, r: 0, currentHp: 40 }),
      makeUnit({ id: "u-basic-10", name: "Basic", definitionId: "basic", team: "PLAYER_ONE", unitType: "BASIC", q: -4, r: 1, currentHp: 42 }),

      // currentHp kept below all three of Valor's stats (strength 68, agility 54,
      // intelligence 60 - see UNIT_STATS.valor in units.ts) so the final "kill" step's
      // displayed damage (TutorialRunner.resolveAttribute reports the defender's
      // pre-attack currentHp as the damage dealt) reads as a plausible single-attribute
      // hit, not an implausible one-shot on a much higher HP pool.
      makeUnit({ id: "e-harbinger", name: "Harbinger", definitionId: "harbinger", team: "PLAYER_TWO", unitType: "CHAMPION", q: 1, r: 0, currentHp: 50 }),
      makeUnit({ id: "e-grivath", name: "Grivath", definitionId: "grivath", team: "PLAYER_TWO", unitType: "ELITE", q: 2, r: -1, currentHp: 150 }),
      makeUnit({ id: "e-dirge", name: "Dirge", definitionId: "dirge", team: "PLAYER_TWO", unitType: "ELITE", q: 0, r: -1, currentHp: 300 }),
      makeUnit({ id: "e-discharge", name: "Discharge", definitionId: "discharge", team: "PLAYER_TWO", unitType: "ELITE", q: 2, r: 1, currentHp: 400 }),
      makeUnit({ id: "e-basic-1", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: -2, r: -1, currentHp: 10 }),
      makeUnit({ id: "e-basic-2", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 0, r: 1, currentHp: 15 }),
      makeUnit({ id: "e-basic-3", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 5, r: -2, currentHp: 0, dead: true }),
      makeUnit({ id: "e-basic-4", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 5, r: 1, currentHp: 0, dead: true }),
      makeUnit({ id: "e-basic-5", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 5, r: 2, currentHp: 0, dead: true }),
      makeUnit({ id: "e-basic-6", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 3, r: -1, currentHp: 48 }),
      makeUnit({ id: "e-basic-7", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 3, r: 1, currentHp: 44 }),
      makeUnit({ id: "e-basic-8", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 2, r: -2, currentHp: 30 }),
      makeUnit({ id: "e-basic-9", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 4, r: 0, currentHp: 38 }),
      makeUnit({ id: "e-basic-10", name: "Basic", definitionId: "basic", team: "PLAYER_TWO", unitType: "BASIC", q: 4, r: -1, currentHp: 42 }),
    ],
  };
}
