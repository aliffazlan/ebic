// The tutorial's 4 draft rounds (Champion, Elite 1/3-3/3), each offering the player a
// real pick and a decoy, plus the enemy's own (cosmetic, never actually drafted)
// options. Stats are the real values from design_ideas/units/{champion,elite}/*.json.
// Ability previews (name + real, resolved-numbers description, shown on hover) are
// hand-authored from design_ideas/abilities/*/*.json below, same style as
// units.ts's battle-ability functions (coldEmbraceAbility() etc.) - kept as its own
// lookup here rather than importing those, since 8 of the 16 units shown in the
// draft (Ember, Chronos, Lanaya, Mercurial, Maxwell, Spitter, Yuki, Wei) never
// battle at all, so a full battle-shaped AbilitySnapshot (moveCost/range/ready/...)
// would be pointless plumbing for them - only name/description/stats/passive/
// cooldown are needed here (AbilityPreviewSnapshot), so this table only carries that.

import type { AbilityPreviewSnapshot, DraftRoundSnapshot, UnitDefinitionSnapshot } from "../../types/contract";

const ABILITY_PREVIEWS: Record<string, Omit<AbilityPreviewSnapshot, "id">> = {
  // Valor (design_ideas/abilities/valor/*.json)
  counterstrike: {
    name: "Counterstrike",
    description: "Being hit by an attack triggers an immediate counter-attack for 80% damage, granting a 3-turn barrier worth 25% of the damage dealt.",
    details: [],
    stats: { damage_multiplier: 0.8, lifesteal: 0.25, barrier_duration: 3 },
    passive: true,
    cooldown: 0,
  },
  overwhelming_odds: {
    name: "Overwhelming Odds",
    description: "Counts allies against enemies within 3 tiles. Outnumbering them deals 16 damage per unit of advantage; being outnumbered instead heals allies in the area 16 per unit of deficit.",
    details: [],
    stats: { cooldown: 5, radius: 3, diff_dmg: 16, diff_heal: 16 },
    passive: false,
    cooldown: 5,
  },
  duel: {
    name: "Duel",
    description: "Locks an adjacent enemy into single combat for 3 turns - neither may act, and each is forced to attack the other at turn's end. The survivor permanently gains 20 to every attribute and heals 50% of its max health.",
    details: [],
    stats: { cooldown: 6, cast_range: 1, duration: 3, duel_bonus: 20, duel_heal: 0.5, win_multiplier: 2.5, duel_damage_multiplier: 2 },
    passive: false,
    cooldown: 6,
  },
  // Ember (design_ideas/abilities/ember/*.json)
  overheat: {
    name: "Overheat",
    description: "Tracks damage dealt to each enemy - once one has taken 80 damage it overheats, dealing 30 damage to every other enemy within 1 tile.",
    details: [],
    stats: { threshold: 80, radius: 1, damage: 30 },
    passive: true,
    cooldown: 0,
  },
  fireblast: {
    name: "Fireblast",
    description: "Hurls a fireball at a target within 2 tiles, dealing 24 damage and applying 2 stacks of Burn.",
    details: [],
    stats: { cooldown: 3, cast_range: 2, damage: 24, burn_stacks: 2 },
    passive: false,
    cooldown: 3,
  },
  eruption: {
    name: "Eruption",
    description: "Sets a tile up to 3 tiles away ablaze for 7 turns, applying 2 stacks of Burn immediately and 1 more to any enemy that ends its turn in the flames.",
    details: [],
    stats: { cooldown: 3, duration: 7, cast_range: 3, burn_stacks_init: 2, burn_stacks: 1 },
    passive: false,
    cooldown: 3,
  },
  // Harbinger (design_ideas/abilities/harbinger/*.json)
  oblivion_confinement: {
    name: "Oblivion Confinement",
    description: "Banishes an enemy within 2 tiles for 1 turn, stunning it but leaving it invulnerable, and steals 35% of its intelligence.",
    details: [],
    stats: { cooldown: 3, cast_range: 2, duration: 1, int_steal: 0.35 },
    passive: false,
    cooldown: 3,
  },
  sanity_eclipse: {
    name: "Sanity's Eclipse",
    description: "Hurls a psionic orb at a tile within 4 tiles. After 1 turn it detonates, dealing 1.5 damage to every enemy within 1 tile for each point of intelligence this unit has over them.",
    details: [],
    stats: { cooldown: 9, cast_range: 4, duration: 1, delay: 1, radius: 1, int_diff_dmg: 1.5 },
    passive: false,
    cooldown: 9,
  },
  objurgation: {
    name: "Objurgation",
    description: "Below 40% health, the next blow is met first with a barrier, burning 20% of this unit's intelligence into shielding at 2.5 health per point. Triggers at most once every 4 turns.",
    details: [],
    stats: { cooldown: 4, int_to_hp: 2.5, int_consumed: 0.2, hp_threshold: 0.4, barrier_duration: 3 },
    passive: true,
    cooldown: 4,
  },
  // Chronos (design_ideas/abilities/chronos/*.json)
  backtrack: {
    name: "Backtrack",
    description: "Dashes to an empty tile within 3 tiles and rewinds this unit's wounds, restoring all damage taken over the last turn.",
    details: [],
    stats: { cooldown: 3, range: 3, backtrack_period: 1 },
    passive: false,
    cooldown: 3,
  },
  dilation: {
    name: "Dilation",
    description: "Warps time within 1 tile for 4 turns - enemies caught in the field have their cooldowns frozen, buffs tick twice as fast, and debuffs linger twice as long.",
    details: [],
    stats: { cooldown: 7, duration: 4, range: 1, time_modifier: 0.5 },
    passive: false,
    cooldown: 7,
  },
  timeless_strike: {
    name: "Timeless Strike",
    description: "A landed attack immediately triggers another, which can chain again. Chained attacks deal 50% damage and add 1 turn to a stun on the target.",
    details: [],
    stats: { duration: 1, damage_multiplier: 0.5 },
    passive: true,
    cooldown: 0,
  },
  // Auroth (design_ideas/abilities/auroth/*.json)
  cold_embrace: {
    name: "Cold Embrace",
    description: "Seals a unit within 4 tiles in ice for 3 turns. Each turn the ice heals an ally, or damages an enemy, for 30.",
    details: [],
    stats: { cooldown: 6, cast_range: 4, duration: 3, dmg_heal: 30 },
    passive: false,
    cooldown: 6,
  },
  frostbite: {
    name: "Frostbite",
    description: "Damaging a unit frostbites it for 3 turns, cutting it off from all healing. A frostbitten unit shatters and dies instantly below 20% of its maximum health.",
    details: [],
    stats: { duration: 3, kill_threshold: 0.2 },
    passive: true,
    cooldown: 0,
  },
  // Lanaya (design_ideas/abilities/lanaya/*.json)
  psychic_projection: {
    name: "Psychic Projection",
    description: "Projects an invulnerable copy of this unit onto a tile within 4 tiles for 3 turns, free to move and attack while this unit is stunned.",
    details: [],
    stats: { cooldown: 6, cast_range: 4, duration: 3 },
    passive: false,
    cooldown: 6,
  },
  refraction: {
    name: "Refraction",
    description: "Refracts incoming damage onto a random adjacent unit instead, up to 1 time per turn.",
    details: [],
    stats: { count: 1 },
    passive: true,
    cooldown: 0,
  },
  // Dirge (design_ideas/abilities/dirge/*.json)
  decay: {
    name: "Decay",
    description: "At the start of each of this unit's turns, every unit within 1 tile takes 5 damage and permanently loses 5 max health and 1 strength, all transferred to this unit.",
    details: [],
    stats: { radius: 1, health_steal: 5, str_steal: 1 },
    passive: true,
    cooldown: 0,
  },
  soul_rip: {
    name: "Soul Rip",
    description: "Tears at a unit within 2 tiles, dealing damage equal to 0.4x this unit's strength advantage over it - or heals an ally by the same amount.",
    details: [],
    stats: { cooldown: 3, cast_range: 2, str_multiplier: 0.4 },
    passive: false,
    cooldown: 3,
  },
  // Mercurial (design_ideas/abilities/mercurial/*.json)
  dispersion: {
    name: "Dispersion",
    description: "Disperses 30% of all damage this unit takes back onto enemies within 1 tile.",
    details: [],
    stats: { radius: 1, dmg_reflect: 0.3 },
    passive: true,
    cooldown: 0,
  },
  manifestation: {
    name: "Manifestation",
    description: "Rematerialises on any free tile bordering an enemy, silencing adjacent enemies and dealing 50% less damage for 2 turns.",
    details: [],
    stats: { cooldown: 5, dmg_reduction: 0.5, duration: 2 },
    passive: false,
    cooldown: 5,
  },
  // Evayne (design_ideas/abilities/evayne/*.json)
  cloak_and_dagger: {
    name: "Cloak and Dagger",
    description: "Vanishes onto a tile up to 2 tiles away for 2 turns, hidden and untouchable, ambushing anyone standing there or who steps on it.",
    details: [],
    stats: { cooldown: 5, cast_range: 2, duration: 2, dmg_penalty: 0.4, backstab_bonus: 1 },
    passive: false,
    cooldown: 5,
  },
  backstab: {
    name: "Backstab",
    description: "Every point of agility adds 0.8 damage to this unit's attacks.",
    details: [],
    stats: { dmg_bonus: 0.8 },
    passive: true,
    cooldown: 0,
  },
  // Maxwell (design_ideas/abilities/maxwell/*.json)
  eureka: {
    name: "Eureka",
    description: "Gains 2 Inspiration at the end of each turn, plus 1 more if it acted, to permanently construct a gadget - the first costs 6, each one after that 10 more.",
    details: [],
    stats: { cooldown: 1, passive_inspiration: 2, bonus_inspiration: 1, cost: 6, cost_increase: 10 },
    passive: false,
    cooldown: 1,
  },
  // Spitter (design_ideas/abilities/spitter/*.json)
  poison_sting: {
    name: "Poison Sting",
    description: "Attacks leave poison on the target for 2 turns, dealing 5 damage for every turn remaining.",
    details: [],
    stats: { duration: 2, dmg_per_duration: 5 },
    passive: true,
    cooldown: 0,
  },
  poison_bloom: {
    name: "Poison Bloom",
    description: "Injects venom into a target within 3 tiles, applying 4 stacks of poison that grow for 3 turns. When it ends it bursts, poisoning every enemy within 1 tile.",
    details: [],
    stats: { cooldown: 6, cast_range: 3, duration: 3, initial_poison: 4, duration_increase: 1, infect_radius: 1 },
    passive: false,
    cooldown: 6,
  },
  // Discharge (design_ideas/abilities/discharge/*.json)
  static_link: {
    name: "Static Link",
    description: "Forms a link with a target within 1 tile, stealing 5 damage from them at the start of every turn and granting a free attack at the end of it.",
    details: [],
    stats: { cooldown: 7, cast_range: 1, link_range: 1, dmg_steal: 5, buff_linger_duration: 2 },
    passive: false,
    cooldown: 7,
  },
  eye_of_the_storm: {
    name: "Eye of the Storm",
    description: "At the start of each of this unit's turns, 1 random enemy within 1 tile is struck by lightning for 2 damage, gaining a permanent +2 damage vulnerability.",
    details: [],
    stats: { count: 1, range: 1, damage: 2, bonus_damage: 2 },
    passive: true,
    cooldown: 0,
  },
  // Thaddeus (design_ideas/abilities/thaddeus/*.json)
  holy_shield: {
    name: "Holy Shield",
    description: "Wraps an ally within 2 tiles in a 50-health barrier for 3 turns. If it breaks early, it erupts, clearing debuffs and dealing 50 damage to enemies within 1 tile.",
    details: [],
    stats: { cooldown: 4, cast_range: 2, duration: 3, barrier_hp: 50, damage: 50, radius: 1 },
    passive: false,
    cooldown: 4,
  },
  selfless: {
    name: "Selfless",
    description: "Allies within 1 tile pass 25% of the damage they take onto this unit instead.",
    details: [],
    stats: { radius: 1, redirect_dmg: 0.25 },
    passive: true,
    cooldown: 0,
  },
  // Yuki (design_ideas/abilities/yuki/*.json)
  blizzard: {
    name: "Blizzard",
    description: "Buries a target within 3 tiles in snow, rooting it and dealing 30 damage per turn for 2 turns.",
    details: [],
    stats: { cooldown: 4, cast_range: 3, duration: 2, damage: 30 },
    passive: false,
    cooldown: 4,
  },
  snow_golem: {
    name: "Snow Golem",
    description: "Summons a snow golem on an empty tile within 1 tile, armed with Blizzard Fist and Snow Blast. Summoning a second golem destroys the first.",
    details: [],
    stats: { cooldown: 16, cast_range: 1 },
    passive: false,
    cooldown: 16,
  },
  // Wei (design_ideas/abilities/wei/*.json)
  energy_break: {
    name: "Energy Break",
    description: "Every encounter drains an opponent's energy, pushing their ability cooldowns up by 1, or 3 on a landed attack.",
    details: [],
    stats: { cooldown_increase: 1, bonus_increase: 3 },
    passive: true,
    cooldown: 0,
  },
  implosion: {
    name: "Implosion",
    description: "Strikes at a target within 2 tiles, dealing 12 damage for every turn of cooldown it's carrying - splashing the same damage onto enemies within 1 tile.",
    details: [],
    stats: { cooldown: 5, cast_range: 2, dmg_per_cooldown: 12, radius: 1 },
    passive: false,
    cooldown: 5,
  },
  // Grivath (design_ideas/abilities/grivath/*.json)
  feast: {
    name: "Feast",
    description: "Enters a feeding frenzy for 3 turns, making a free attack on an adjacent enemy each turn. Each free attack heals this unit for 25% of the damage dealt and roots the victim for 1 turn.",
    details: [],
    stats: { cooldown: 7, duration: 3, attacks: 1, lifesteal: 0.25, root_duration: 1, cast_range: 1 },
    passive: false,
    cooldown: 7,
  },
  cripple: {
    name: "Cripple",
    description: "A landed attack drains 1 point of every attribute and 5 max/current health from the target, transferring it all to this unit. A further 3 points are taken from whichever attribute the target defended with.",
    details: [],
    stats: { stat_steal: 1, defended_bonus: 3, hp_steal: 5, non_basic_multiplier: 2 },
    passive: true,
    cooldown: 0,
  },
};

function preview(id: string): AbilityPreviewSnapshot {
  return { id, ...ABILITY_PREVIEWS[id] };
}

interface DefOpts {
  definitionId: string;
  name: string;
  type: "CHAMPION" | "ELITE";
  maxHp: number;
  strength: number;
  agility: number;
  intelligence: number;
  attackRange: number;
  abilityIds: string[];
}

function def(o: DefOpts): UnitDefinitionSnapshot {
  return {
    definitionId: o.definitionId,
    name: o.name,
    type: o.type,
    maxHp: o.maxHp,
    strength: o.strength,
    agility: o.agility,
    intelligence: o.intelligence,
    attackRange: o.attackRange,
    abilities: o.abilityIds.map(preview),
  };
}

const DEF_VALOR = def({ definitionId: "valor", name: "Valor", type: "CHAMPION", maxHp: 1280, strength: 68, agility: 54, intelligence: 60, attackRange: 1, abilityIds: ["counterstrike", "overwhelming_odds", "duel"] });
const DEF_EMBER = def({ definitionId: "ember", name: "Ember", type: "CHAMPION", maxHp: 960, strength: 35, agility: 50, intelligence: 88, attackRange: 1, abilityIds: ["overheat", "fireblast", "eruption"] });
const DEF_HARBINGER = def({ definitionId: "harbinger", name: "Harbinger", type: "CHAMPION", maxHp: 1080, strength: 66, agility: 30, intelligence: 86, attackRange: 2, abilityIds: ["oblivion_confinement", "sanity_eclipse", "objurgation"] });
const DEF_CHRONOS = def({ definitionId: "chronos", name: "Chronos", type: "CHAMPION", maxHp: 1140, strength: 58, agility: 104, intelligence: 46, attackRange: 1, abilityIds: ["backtrack", "dilation", "timeless_strike"] });

const DEF_AUROTH = def({ definitionId: "auroth", name: "Auroth", type: "ELITE", maxHp: 530, strength: 20, agility: 30, intelligence: 70, attackRange: 2, abilityIds: ["cold_embrace", "frostbite"] });
const DEF_LANAYA = def({ definitionId: "lanaya", name: "Lanaya", type: "ELITE", maxHp: 600, strength: 30, agility: 120, intelligence: 70, attackRange: 1, abilityIds: ["psychic_projection", "refraction"] });
const DEF_DIRGE = def({ definitionId: "dirge", name: "Dirge", type: "ELITE", maxHp: 750, strength: 60, agility: 20, intelligence: 45, attackRange: 1, abilityIds: ["decay", "soul_rip"] });
const DEF_MERCURIAL = def({ definitionId: "mercurial", name: "Mercurial", type: "ELITE", maxHp: 860, strength: 38, agility: 36, intelligence: 30, attackRange: 1, abilityIds: ["dispersion", "manifestation"] });

const DEF_EVAYNE = def({ definitionId: "evayne", name: "Evayne", type: "ELITE", maxHp: 480, strength: 15, agility: 120, intelligence: 30, attackRange: 1, abilityIds: ["cloak_and_dagger", "backstab"] });
const DEF_MAXWELL = def({ definitionId: "maxwell", name: "Maxwell", type: "ELITE", maxHp: 510, strength: 18, agility: 12, intelligence: 84, attackRange: 2, abilityIds: ["eureka"] });
const DEF_SPITTER = def({ definitionId: "spitter", name: "Spitter", type: "ELITE", maxHp: 600, strength: 30, agility: 35, intelligence: 60, attackRange: 2, abilityIds: ["poison_sting", "poison_bloom"] });
const DEF_DISCHARGE = def({ definitionId: "discharge", name: "Discharge", type: "ELITE", maxHp: 900, strength: 30, agility: 40, intelligence: 10, attackRange: 1, abilityIds: ["static_link", "eye_of_the_storm"] });

const DEF_THADDEUS = def({ definitionId: "thaddeus", name: "Thaddeus", type: "ELITE", maxHp: 920, strength: 30, agility: 9, intelligence: 20, attackRange: 1, abilityIds: ["holy_shield", "selfless"] });
const DEF_YUKI = def({ definitionId: "yuki", name: "Yuki", type: "ELITE", maxHp: 440, strength: 15, agility: 20, intelligence: 40, attackRange: 2, abilityIds: ["blizzard", "snow_golem"] });
const DEF_WEI = def({ definitionId: "wei", name: "Wei", type: "ELITE", maxHp: 710, strength: 36, agility: 84, intelligence: 20, attackRange: 1, abilityIds: ["energy_break", "implosion"] });
const DEF_GRIVATH = def({ definitionId: "grivath", name: "Grivath", type: "ELITE", maxHp: 680, strength: 56, agility: 48, intelligence: 42, attackRange: 1, abilityIds: ["feast", "cripple"] });

export const DRAFT_ROUNDS: DraftRoundSnapshot[] = [
  { roundLabel: "Champion", options: [DEF_VALOR, DEF_EMBER], opponentOptions: [DEF_HARBINGER, DEF_CHRONOS] },
  { roundLabel: "Elite 1/3", options: [DEF_LANAYA, DEF_AUROTH], opponentOptions: [DEF_DIRGE, DEF_MERCURIAL] },
  { roundLabel: "Elite 2/3", options: [DEF_EVAYNE, DEF_MAXWELL], opponentOptions: [DEF_SPITTER, DEF_DISCHARGE] },
  { roundLabel: "Elite 3/3", options: [DEF_THADDEUS, DEF_YUKI], opponentOptions: [DEF_WEI, DEF_GRIVATH] },
];
