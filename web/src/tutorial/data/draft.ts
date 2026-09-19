// The tutorial's 4 draft rounds (Champion, Elite 1/3-3/3), each offering the player a
// real pick and a decoy, plus the enemy's own (cosmetic, never actually drafted)
// options. Stats are the real values from design_ideas/units/{champion,elite}/*.json.
// Ability previews are decorative on a draft card (name + generic blurb, shown on
// hover) - the tutorial never grants or casts any of these, so they don't need to be
// authored in full.

import type { AbilityPreviewSnapshot, DraftRoundSnapshot, UnitDefinitionSnapshot } from "../../types/contract";

function titleCase(id: string): string {
  return id
    .split("_")
    .map((w) => w[0].toUpperCase() + w.slice(1))
    .join(" ");
}

function preview(id: string): AbilityPreviewSnapshot {
  const name = titleCase(id);
  return { id, name, description: `${name} - a signature ability.`, details: [], stats: {}, passive: false, cooldown: 4 };
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
const DEF_HARBINGER = def({ definitionId: "harbinger", name: "Harbinger", type: "CHAMPION", maxHp: 1200, strength: 66, agility: 30, intelligence: 86, attackRange: 2, abilityIds: ["oblivion_confinement", "sanity_eclipse", "objurgation"] });
const DEF_CHRONOS = def({ definitionId: "chronos", name: "Chronos", type: "CHAMPION", maxHp: 1200, strength: 70, agility: 110, intelligence: 60, attackRange: 1, abilityIds: ["backtrack", "dilation", "timeless_strike"] });

const DEF_AUROTH = def({ definitionId: "auroth", name: "Auroth", type: "ELITE", maxHp: 530, strength: 20, agility: 30, intelligence: 70, attackRange: 2, abilityIds: ["cold_embrace", "frostbite"] });
const DEF_LANAYA = def({ definitionId: "lanaya", name: "Lanaya", type: "ELITE", maxHp: 600, strength: 30, agility: 120, intelligence: 70, attackRange: 1, abilityIds: ["psychic_projection", "refraction"] });
const DEF_DIRGE = def({ definitionId: "dirge", name: "Dirge", type: "ELITE", maxHp: 750, strength: 60, agility: 20, intelligence: 45, attackRange: 1, abilityIds: ["decay", "soul_rip"] });
const DEF_MERCURIAL = def({ definitionId: "mercurial", name: "Mercurial", type: "ELITE", maxHp: 860, strength: 38, agility: 36, intelligence: 30, attackRange: 1, abilityIds: ["dispersion", "manifestation"] });

const DEF_EVAYNE = def({ definitionId: "evayne", name: "Evayne", type: "ELITE", maxHp: 480, strength: 15, agility: 120, intelligence: 30, attackRange: 1, abilityIds: ["cloak_and_dagger", "backstab"] });
const DEF_MAXWELL = def({ definitionId: "maxwell", name: "Maxwell", type: "ELITE", maxHp: 510, strength: 18, agility: 12, intelligence: 84, attackRange: 2, abilityIds: ["eureka"] });
const DEF_SPITTER = def({ definitionId: "spitter", name: "Spitter", type: "ELITE", maxHp: 600, strength: 30, agility: 35, intelligence: 60, attackRange: 2, abilityIds: ["poison_sting", "poison_bloom"] });
const DEF_DISCHARGE = def({ definitionId: "discharge", name: "Discharge", type: "ELITE", maxHp: 900, strength: 30, agility: 40, intelligence: 10, attackRange: 1, abilityIds: ["static_link", "eye_of_the_storm"] });

const DEF_THADDEUS = def({ definitionId: "thaddeus", name: "Thaddeus", type: "ELITE", maxHp: 850, strength: 30, agility: 9, intelligence: 20, attackRange: 1, abilityIds: ["holy_shield", "selfless"] });
const DEF_YUKI = def({ definitionId: "yuki", name: "Yuki", type: "ELITE", maxHp: 440, strength: 15, agility: 20, intelligence: 40, attackRange: 2, abilityIds: ["blizzard", "snow_golem"] });
const DEF_WEI = def({ definitionId: "wei", name: "Wei", type: "ELITE", maxHp: 710, strength: 36, agility: 84, intelligence: 20, attackRange: 1, abilityIds: ["energy_break", "implosion"] });
const DEF_GRIVATH = def({ definitionId: "grivath", name: "Grivath", type: "ELITE", maxHp: 750, strength: 60, agility: 54, intelligence: 48, attackRange: 1, abilityIds: ["feast", "cripple"] });

export const DRAFT_ROUNDS: DraftRoundSnapshot[] = [
  { roundLabel: "Champion", options: [DEF_VALOR, DEF_EMBER], opponentOptions: [DEF_HARBINGER, DEF_CHRONOS] },
  { roundLabel: "Elite 1/3", options: [DEF_LANAYA, DEF_AUROTH], opponentOptions: [DEF_DIRGE, DEF_MERCURIAL] },
  { roundLabel: "Elite 2/3", options: [DEF_EVAYNE, DEF_MAXWELL], opponentOptions: [DEF_SPITTER, DEF_DISCHARGE] },
  { roundLabel: "Elite 3/3", options: [DEF_THADDEUS, DEF_YUKI], opponentOptions: [DEF_WEI, DEF_GRIVATH] },
];
