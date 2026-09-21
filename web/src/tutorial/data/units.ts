// Hand-authored unit stat blocks and UnitSnapshot builder for the scripted tutorial.
// Stats are pulled from design_ideas/units/{champion,elite}/*.json so the tutorial's
// board reflects the real roster rather than made-up numbers. Every champion/elite's
// full real ability kit (design_ideas/abilities/*/*.json) is hand-authored below too,
// for display in their battle-phase unit panel - the tutorial only ever actually CASTS
// Move/Attack/Cold Embrace (the scripted steps' gates reject anything else), so every
// other ability here is display-only, matching Cold Embrace's own role beyond its one
// scripted cast.

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

/** Shared shape for every hand-authored real ability below - same fields
 * coldEmbraceAbility() fills in by hand, factored out since there are now many of
 * these. `details` is always [] and `upgraded`/`ready`/`usedThisTurn`/
 * `currentCooldown`/`minRange` are always the same "freshly available" values,
 * matching coldEmbraceAbility()'s own precedent (upgrade-tier stats are never
 * modeled here either). */
function ability(opts: {
  id: string;
  name: string;
  description: string;
  stats: Record<string, number>;
  passive: boolean;
  maxCooldown: number;
  range: number;
}): AbilitySnapshot {
  return {
    id: opts.id,
    name: opts.name,
    description: opts.description,
    details: [],
    stats: opts.stats,
    passive: opts.passive,
    upgraded: false,
    ready: true,
    usedThisTurn: false,
    currentCooldown: 0,
    maxCooldown: opts.maxCooldown,
    moveCost: opts.passive ? 0 : 1,
    range: opts.range,
    minRange: 0,
  };
}

/** Valor's real kit (design_ideas/abilities/valor/*.json). */
function counterstrikeAbility(): AbilitySnapshot {
  return ability({
    id: "counterstrike",
    name: "Counterstrike",
    description:
      "Being hit by an attack triggers an immediate counter-attack for 80% damage, granting a 3-turn barrier worth 25% of the damage dealt.",
    stats: { damage_multiplier: 0.8, lifesteal: 0.25, barrier_duration: 3 },
    passive: true,
    maxCooldown: 0,
    range: 0,
  });
}

function overwhelmingOddsAbility(): AbilitySnapshot {
  return ability({
    id: "overwhelming_odds",
    name: "Overwhelming Odds",
    description:
      "Counts allies against enemies within 3 tiles. Outnumbering them deals 16 damage per unit of advantage; being outnumbered instead heals allies in the area 16 per unit of deficit.",
    stats: { cooldown: 5, radius: 3, diff_dmg: 16, diff_heal: 16 },
    passive: false,
    maxCooldown: 5,
    range: 3,
  });
}

function duelAbility(): AbilitySnapshot {
  return ability({
    id: "duel",
    name: "Duel",
    description:
      "Locks an adjacent enemy into single combat for 3 turns - neither may act, and each is forced to attack the other at turn's end. The survivor permanently gains 20 to every attribute and heals 50% of its max health.",
    stats: {
      cooldown: 6,
      cast_range: 1,
      duration: 3,
      duel_bonus: 20,
      duel_heal: 0.5,
      win_multiplier: 2.5,
      duel_damage_multiplier: 2,
    },
    passive: false,
    maxCooldown: 6,
    range: 1,
  });
}

/** Thaddeus's real kit (design_ideas/abilities/thaddeus/*.json). */
function holyShieldAbility(): AbilitySnapshot {
  return ability({
    id: "holy_shield",
    name: "Holy Shield",
    description:
      "Wraps an ally within 2 tiles in a 50-health barrier for 3 turns. If it breaks early, it erupts, clearing debuffs and dealing 50 damage to enemies within 1 tile.",
    stats: { cooldown: 4, cast_range: 2, duration: 3, barrier_hp: 50, damage: 50, radius: 1 },
    passive: false,
    maxCooldown: 4,
    range: 2,
  });
}

function selflessAbility(): AbilitySnapshot {
  return ability({
    id: "selfless",
    name: "Selfless",
    description: "Allies within 1 tile pass 25% of the damage they take onto this unit instead.",
    stats: { radius: 1, redirect_dmg: 0.25 },
    passive: true,
    maxCooldown: 0,
    range: 1,
  });
}

/** Evayne's real kit (design_ideas/abilities/evayne/*.json). */
function cloakAndDaggerAbility(): AbilitySnapshot {
  return ability({
    id: "cloak_and_dagger",
    name: "Cloak and Dagger",
    description:
      "Vanishes onto a tile up to 2 tiles away for 2 turns, hidden and untouchable, ambushing anyone standing there or who steps on it.",
    stats: { cooldown: 5, cast_range: 2, duration: 2, dmg_penalty: 0.4, backstab_bonus: 1 },
    passive: false,
    maxCooldown: 5,
    range: 2,
  });
}

function backstabAbility(): AbilitySnapshot {
  return ability({
    id: "backstab",
    name: "Backstab",
    description: "Every point of agility adds 0.8 damage to this unit's attacks.",
    stats: { dmg_bonus: 0.8 },
    passive: true,
    maxCooldown: 0,
    range: 0,
  });
}

/** Harbinger's real kit (design_ideas/abilities/harbinger/*.json). */
function oblivionConfinementAbility(): AbilitySnapshot {
  return ability({
    id: "oblivion_confinement",
    name: "Oblivion Confinement",
    description:
      "Banishes an enemy within 2 tiles for 1 turn, stunning it but leaving it invulnerable, and steals 35% of its intelligence.",
    stats: { cooldown: 3, cast_range: 2, duration: 1, int_steal: 0.35 },
    passive: false,
    maxCooldown: 3,
    range: 2,
  });
}

function sanityEclipseAbility(): AbilitySnapshot {
  return ability({
    id: "sanity_eclipse",
    name: "Sanity's Eclipse",
    description:
      "Hurls a psionic orb at a tile within 4 tiles. After 1 turn it detonates, dealing 1.5 damage to every enemy within 1 tile for each point of intelligence this unit has over them.",
    stats: { cooldown: 9, cast_range: 4, duration: 1, delay: 1, radius: 1, int_diff_dmg: 1.5 },
    passive: false,
    maxCooldown: 9,
    range: 4,
  });
}

function objurgationAbility(): AbilitySnapshot {
  return ability({
    id: "objurgation",
    name: "Objurgation",
    description:
      "Below 40% health, the next blow is met first with a barrier, burning 20% of this unit's intelligence into shielding at 2.5 health per point. Triggers at most once every 4 turns.",
    stats: { cooldown: 4, int_to_hp: 2.5, int_consumed: 0.2, hp_threshold: 0.4, barrier_duration: 3 },
    passive: true,
    maxCooldown: 4,
    range: 0,
  });
}

/** Dirge's real kit (design_ideas/abilities/dirge/*.json). */
function decayAbility(): AbilitySnapshot {
  return ability({
    id: "decay",
    name: "Decay",
    description:
      "At the start of each of this unit's turns, every unit within 1 tile takes 5 damage and permanently loses 5 max health and 1 strength, all transferred to this unit.",
    stats: { radius: 1, health_steal: 5, str_steal: 1 },
    passive: true,
    maxCooldown: 0,
    range: 1,
  });
}

function soulRipAbility(): AbilitySnapshot {
  return ability({
    id: "soul_rip",
    name: "Soul Rip",
    description:
      "Tears at a unit within 2 tiles, dealing damage equal to 0.4x this unit's strength advantage over it - or heals an ally by the same amount.",
    stats: { cooldown: 3, cast_range: 2, str_multiplier: 0.4 },
    passive: false,
    maxCooldown: 3,
    range: 2,
  });
}

/** Grivath's real kit (design_ideas/abilities/grivath/*.json). */
function feastAbility(): AbilitySnapshot {
  return ability({
    id: "feast",
    name: "Feast",
    description:
      "Enters a feeding frenzy for 3 turns, making a free attack on an adjacent enemy each turn. Each free attack heals this unit for 25% of the damage dealt and roots the victim for 1 turn.",
    stats: { cooldown: 7, duration: 3, attacks: 1, lifesteal: 0.25, root_duration: 1, cast_range: 1 },
    passive: false,
    maxCooldown: 7,
    range: 1,
  });
}

function crippleAbility(): AbilitySnapshot {
  return ability({
    id: "cripple",
    name: "Cripple",
    description:
      "A landed attack drains 1 point of every attribute and 5 max/current health from the target, transferring it all to this unit. A further 3 points are taken from whichever attribute the target defended with.",
    stats: { stat_steal: 1, defended_bonus: 3, hp_steal: 5, non_basic_multiplier: 2 },
    passive: true,
    maxCooldown: 0,
    range: 0,
  });
}

/** Discharge's real kit (design_ideas/abilities/discharge/*.json). */
function staticLinkAbility(): AbilitySnapshot {
  return ability({
    id: "static_link",
    name: "Static Link",
    description:
      "Forms a link with a target within 1 tile, stealing 5 damage from them at the start of every turn and granting a free attack at the end of it.",
    stats: { cooldown: 7, cast_range: 1, link_range: 1, dmg_steal: 5, buff_linger_duration: 2 },
    passive: false,
    maxCooldown: 7,
    range: 1,
  });
}

function eyeOfTheStormAbility(): AbilitySnapshot {
  return ability({
    id: "eye_of_the_storm",
    name: "Eye of the Storm",
    description:
      "At the start of each of this unit's turns, 1 random enemy within 1 tile is struck by lightning for 2 damage, gaining a permanent +2 damage vulnerability.",
    stats: { count: 1, range: 1, damage: 2, bonus_damage: 2 },
    passive: true,
    maxCooldown: 0,
    range: 1,
  });
}

/** Every fixed unit id's real extra ability kit, in one place, so every battle/
 * mid-battle snapshot builder stays in sync instead of repeating the same per-unit
 * lookup three times (battle.ts x2, midBattle.ts x1). undefined (not []) for any
 * unit with none, matching makeUnit's own `opts.extraAbilities ?? []` default. */
export function extraAbilitiesFor(unitId: string): AbilitySnapshot[] | undefined {
  switch (unitId) {
    case "u-auroth":
      return [coldEmbraceAbility()];
    case "u-valor":
      return [counterstrikeAbility(), overwhelmingOddsAbility(), duelAbility()];
    case "u-thaddeus":
      return [holyShieldAbility(), selflessAbility()];
    case "u-evayne":
      return [cloakAndDaggerAbility(), backstabAbility()];
    case "e-harbinger":
      return [oblivionConfinementAbility(), sanityEclipseAbility(), objurgationAbility()];
    case "e-dirge":
      return [decayAbility(), soulRipAbility()];
    case "e-grivath":
      return [feastAbility(), crippleAbility()];
    case "e-discharge":
      return [staticLinkAbility(), eyeOfTheStormAbility()];
    default:
      return undefined;
  }
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
