// Hand-authored unit stat blocks and UnitSnapshot builder for the scripted tutorial.
// Stats are pulled from design_ideas/units/{champion,elite}/*.json so the tutorial's
// board reflects the real roster rather than made-up numbers. Ability kits are
// deliberately trimmed to Move/Attack (+ Cold Embrace for Auroth, the one spell the
// script actually casts) - the tutorial never interacts with a unit's full kit, and
// authoring every real ability for every unit would add a lot of data for zero payoff.

import type { AbilitySnapshot, Team, UnitSnapshot, UnitType } from "../../types/contract";

interface StatBlock {
  maxHp: number;
  strength: number;
  agility: number;
  intelligence: number;
  attackRange: number;
}

export const UNIT_STATS: Record<string, StatBlock> = {
  valor: { maxHp: 1280, strength: 68, agility: 54, intelligence: 60, attackRange: 1 },
  harbinger: { maxHp: 1200, strength: 66, agility: 30, intelligence: 86, attackRange: 2 },
  auroth: { maxHp: 530, strength: 20, agility: 30, intelligence: 70, attackRange: 2 },
  evayne: { maxHp: 480, strength: 15, agility: 120, intelligence: 30, attackRange: 1 },
  thaddeus: { maxHp: 850, strength: 30, agility: 9, intelligence: 20, attackRange: 1 },
  grivath: { maxHp: 750, strength: 60, agility: 54, intelligence: 48, attackRange: 1 },
  dirge: { maxHp: 750, strength: 60, agility: 20, intelligence: 45, attackRange: 1 },
  discharge: { maxHp: 900, strength: 30, agility: 40, intelligence: 10, attackRange: 1 },
  basic: { maxHp: 200, strength: 10, agility: 10, intelligence: 10, attackRange: 1 },
};

function moveAbility(moveCost: number): AbilitySnapshot {
  return {
    id: "move",
    name: "Move",
    description: "Move to an adjacent tile.",
    details: [],
    stats: {},
    passive: false,
    upgraded: false,
    ready: true,
    usedThisTurn: false,
    currentCooldown: 0,
    maxCooldown: 0,
    moveCost,
    range: 1,
    minRange: 0,
  };
}

function attackAbility(moveCost: number, range: number): AbilitySnapshot {
  return {
    id: "attack",
    name: "Attack",
    description: "Basic attack against an enemy within range.",
    details: [],
    stats: {},
    passive: false,
    upgraded: false,
    ready: true,
    usedThisTurn: false,
    currentCooldown: 0,
    maxCooldown: 0,
    moveCost,
    range,
    minRange: 0,
  };
}

/** Auroth's real ability (design_ideas/abilities/auroth/cold_embrace.json) - the one spell the script casts. */
export function coldEmbraceAbility(): AbilitySnapshot {
  return {
    id: "cold_embrace",
    name: "Cold Embrace",
    description:
      "Seals a unit within 4 tiles in ice for 3 turns. Each turn the ice heals an ally, or damages an enemy, for 30.",
    details: [],
    stats: { cooldown: 6, cast_range: 4, duration: 3, dmg_heal: 30 },
    passive: false,
    upgraded: false,
    ready: true,
    usedThisTurn: false,
    currentCooldown: 0,
    maxCooldown: 6,
    moveCost: 1,
    range: 4,
    minRange: 0,
  };
}

export interface MakeUnitOpts {
  id: string;
  name: string;
  definitionId: string;
  team: Team;
  unitType: UnitType;
  q: number;
  r: number;
  currentHp?: number;
  dead?: boolean;
  hasMovedThisTurn?: boolean;
  hasAttackedThisTurn?: boolean;
  extraAbilities?: AbilitySnapshot[];
}

export function makeUnit(opts: MakeUnitOpts): UnitSnapshot {
  const stats = UNIT_STATS[opts.definitionId];
  const moveCost = opts.unitType === "BASIC" ? 0 : 1;
  return {
    id: opts.id,
    name: opts.name,
    definitionId: opts.definitionId,
    team: opts.team,
    unitType: opts.unitType,
    q: opts.q,
    r: opts.r,
    currentHp: opts.currentHp ?? stats.maxHp,
    maxHp: stats.maxHp,
    currentBarrierHp: 0,
    maxBarrierHp: 0,
    strength: stats.strength,
    agility: stats.agility,
    intelligence: stats.intelligence,
    attackRange: stats.attackRange,
    minAttackRange: 0,
    dead: opts.dead ?? false,
    hasMovedThisTurn: opts.hasMovedThisTurn ?? false,
    hasAttackedThisTurn: opts.hasAttackedThisTurn ?? false,
    statusFlags: [],
    abilities: [moveAbility(moveCost), attackAbility(moveCost, stats.attackRange), ...(opts.extraAbilities ?? [])],
    effects: [],
  };
}
