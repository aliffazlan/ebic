// Per-effect status-visual config, plus the pure lookup/fallback logic that
// decides what (if anything) a unit's active EffectSnapshot renders as. No
// PixiJS here - see StatusEffectPlayer.ts for the rendering side, kept
// separate so this file stays unit-testable under vitest's node environment.
//
// Unlike AttackAnimations.ts, these are persistent, state-driven visuals -
// driven straight from each unit's `effects` on every snapshot, not from a
// one-shot VfxEvent. Effects not listed here render nothing, the same
// fallback philosophy as the rest of the asset system.

export type UnitStatusVisualKind =
  | "stun"
  | "pulse"
  | "ember"
  | "translucent-circle"
  | "smoke"
  | "ice"
  | "shield"
  | "snow"
  | "snowflakes"
  | "energy-bars"
  | "doom"
  | "shrink"
  | "chains"
  | "crosshair"
  | "inward-particles";

export interface UnitStatusVisual {
  mode: "unit";
  kind: UnitStatusVisualKind;
  color: number;
  /** Poison Bloom only - thicker/denser smoke than the plain smoke kinds. */
  thick?: boolean;
  /** translucent-circle only - Objurgation Barrier renders slightly larger than the token. Default 1. */
  radiusMultiplier?: number;
}

export interface TileStatusVisual {
  mode: "tile";
  kind: "cloak";
}

export interface PairStatusVisual {
  mode: "pair";
  kind: "banner" | "lightning";
  color: number;
}

export type StatusVisualSpec = UnitStatusVisual | TileStatusVisual | PairStatusVisual;

const WHITE = 0xffffff;
const LIGHT_GRAY = 0xe2e8f0;
const DARK_PURPLE = 0x7c3aed;
const EMBER_ORANGE = 0xfb923c;
const OBLIVION_BLUE = 0x38bdf8;
const BRIGHT_ICE_BLUE = 0x7dd3fc;
const STATIC_LINK_YELLOW = 0xfacc15;
const FEAST_RED = 0xf87171;
const DOOM_ORANGE = 0xf97316;
const ENERGY_BLUE = 0x60a5fa;
const NANOBOTS_BLUE = 0xa5f3fc;
const POISON_GREEN = 0xbef264;
const POISON_BLOOM_GREEN = 0x166534;
const OBJURGATION_BLUE = 0x93c5fd;
const FROSTBITE_BLUE = 0xbfe7ff;
const REFRACTION_PINK = 0xf9a8d4;
const INFERNAL_BLADE_RED = 0xdc2626;
const MANIFESTATION_PURPLE = 0x581c87;
const HIGH_NOON_ORANGE = 0xf97316;
const BLOODWAKE_RED = 0xdc2626;

/**
 * One entry per effect name (see EffectSnapshot.name, sourced from the
 * backend's `super("Name", ...)` calls in effect/impl/*.java) with a listed
 * visual in temp/abilities.txt. "Energy Shield" covers both Maxwell's cast
 * and its passive regen form - both use that exact name. "Dilation Field"
 * (the 1-turn debuff DilationEffect pulses onto nearby enemies) deliberately
 * has no entry of its own - the caster's own enlarged aura circle already
 * covers every affected tile, so a second pulse per enemy would be redundant.
 */
export const STATUS_VISUAL_BY_EFFECT_NAME: Record<string, StatusVisualSpec> = {
  Dilation: { mode: "unit", kind: "pulse", color: DARK_PURPLE },
  Burn: { mode: "unit", kind: "ember", color: EMBER_ORANGE },
  "Oblivion Confinement": { mode: "unit", kind: "translucent-circle", color: OBLIVION_BLUE },
  "Objurgation Barrier": { mode: "unit", kind: "translucent-circle", color: OBJURGATION_BLUE, radiusMultiplier: 1.15 },
  Duel: { mode: "pair", kind: "banner", color: WHITE },
  "Steady Focus": { mode: "unit", kind: "smoke", color: LIGHT_GRAY },
  "Cold Embrace": { mode: "unit", kind: "ice", color: BRIGHT_ICE_BLUE },
  "Static Link": { mode: "pair", kind: "lightning", color: STATIC_LINK_YELLOW },
  "Cloak and Dagger": { mode: "tile", kind: "cloak" },
  Feast: { mode: "unit", kind: "smoke", color: FEAST_RED },
  Doom: { mode: "unit", kind: "doom", color: DOOM_ORANGE },
  "Energy Shield": { mode: "unit", kind: "energy-bars", color: ENERGY_BLUE },
  Nanobots: { mode: "unit", kind: "smoke", color: NANOBOTS_BLUE },
  Shrunk: { mode: "unit", kind: "shrink", color: WHITE },
  Poison: { mode: "unit", kind: "smoke", color: POISON_GREEN },
  "Poison Bloom": { mode: "unit", kind: "smoke", color: POISON_BLOOM_GREEN, thick: true },
  "Holy Shield": { mode: "unit", kind: "shield", color: WHITE },
  Blizzard: { mode: "unit", kind: "snow", color: WHITE },
  Frostbite: { mode: "unit", kind: "snowflakes", color: FROSTBITE_BLUE },
  // Cosmetic-only marker (see RefractionReadyEffect.java) - present exactly while
  // Lanaya has an unspent Refraction charge this turn.
  "Refraction Ready": { mode: "unit", kind: "translucent-circle", color: REFRACTION_PINK },
  // Two curved red arcs crossing in an X, per temp/abilities.txt - procedural rather than an
  // SVG (see spawnChainsOverlay), the same call made for Frostbite's snowflakes.
  "Infernal Blade": { mode: "unit", kind: "chains", color: INFERNAL_BLADE_RED },
  Manifestation: { mode: "unit", kind: "smoke", color: MANIFESTATION_PURPLE },
  // The crosshair Homing Missile's tile marker used to draw (that marker is now a pulse, so
  // the two can't be confused), per temp/vfx.txt.
  "High Noon Mark": { mode: "unit", kind: "crosshair", color: HIGH_NOON_ORANGE },
  // Large red particles drawn in from outside the portrait, fading out before they reach its
  // centre - Sanity's Eclipse's charge, minus the orb, per temp/vfx.txt.
  Bloodwake: { mode: "unit", kind: "inward-particles", color: BLOODWAKE_RED },
};

/**
 * Generic stun: 3 small stars orbiting a unit's head. Used for every stun
 * that has no dedicated visual of its own - Timeless Strike, Pylon Collapse,
 * Psychic Projection's self-stun, the homing-missile upgrade's stun, and any
 * future one, without needing their exact effect names (each is its own
 * separately-named backend effect, see GameStateSnapshotMapper).
 */
export const GENERIC_STUN_VISUAL: UnitStatusVisual = { mode: "unit", kind: "stun", color: WHITE };

export interface EffectLike {
  name: string;
  statusFlags: readonly string[];
}

/**
 * Resolves one effect's visual: a dedicated entry by name if there is one,
 * otherwise the generic stun fallback if it carries the STUNNED flag,
 * otherwise no visual at all (silently rendered as nothing).
 */
export function statusVisualFor(effect: EffectLike): StatusVisualSpec | null {
  const specific = STATUS_VISUAL_BY_EFFECT_NAME[effect.name];
  if (specific) return specific;
  if (effect.statusFlags.includes("STUNNED")) return GENERIC_STUN_VISUAL;
  return null;
}
