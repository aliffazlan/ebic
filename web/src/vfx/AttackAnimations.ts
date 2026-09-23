// Per-unit attack-animation config, plus the pure logic that decides which
// VfxEvents get one and how long to reserve for it. No PixiJS here - see
// AttackAnimationPlayer.ts for the rendering side, kept separate so this file
// stays unit-testable under vitest's node environment.

import { ENCOUNTER_CAUSE_LABELS } from "./VfxIndicators";
import type { VfxEvent } from "../types/contract";

export type AttackAnimationKind =
  | "slash"
  | "arrow"
  | "projectile"
  | "growing-projectile"
  | "lightning"
  | "beam"
  | "pulse"
  | "arc-projectile"
  | "charge-burst";

export interface AttackAnimationStroke {
  kind: AttackAnimationKind;
  color: number;
  /** Scale multiplier for slash/arrow art. Default 1. */
  scale?: number;
  /** Delay in ms after the whole animation starts before this stroke begins. Default 0. */
  delayMs?: number;
  /** Ember/Fireblast's trailing sparkle behind the travelling orb - "projectile" only. */
  particles?: boolean;
  /** Lightning only - stroke width in px. Default LIGHTNING_WIDTH_PX (3) when unset. */
  width?: number;
  /** Beam only - end colour to lerp toward across the beam's duration. No lerp (stays `color`) when unset. */
  toColor?: number;
  /**
   * "projectile" or "arc-projectile" only - draws this recoloured icon instead of a plain dot
   * (Homing Missile's missile; Acidic Brew's flask; Hidden Potential's scroll).
   */
  icon?: "missile" | "flask" | "scroll";
  /** arc-projectile only - how high (px) the throw arcs above a straight line, at its midpoint. */
  arcHeightPx?: number;
  /** arc-projectile only - continuous rotation speed in radians/frame, independent of travel direction. */
  spinSpeed?: number;
  /**
   * arc-projectile only - spawns a continuous particle trail in this colour while flying
   * (Hidden Potential's gold trail, distinct from the icon's own colour). No trail when unset.
   */
  trailColor?: number;
  /**
   * arc-projectile only - a particle burst on arrival (Hidden Potential's golden burst on the
   * target). Unlike growing-projectile's always-on impact burst, this is opt-in and unset by
   * default - Acidic Brew's own landing effect (a single slow pulse, gated separately so it can
   * hold the persistent tile overlay back) is played by the caller instead, not by the stroke.
   */
  landingBurst?: { count?: number; radiusPx?: number; speed?: number; lifeFrames?: number; color?: number };
  /**
   * growing-projectile only - overrides the default grow-in-place phase duration (ms). Plasma
   * Cannon's much longer ~2s grow, per temp/abilities.txt, without lengthening Fireblast's
   * (which leaves this unset).
   */
  growDurationMs?: number;
  /** growing-projectile only - overrides the default travel-to-target phase duration (ms). */
  travelDurationMs?: number;
  /** growing-projectile only - overrides the default orb radius (px) while growing/travelling. */
  projectileRadiusPx?: number;
  /**
   * growing-projectile only - spawns particles converging inward on the orb while it grows
   * (Plasma Cannon, "similar to sanity's eclipse effect" per temp/abilities.txt). A distinct
   * flag from `particles` (Fireblast's trailing sparkle) so Fireblast's existing look is
   * unaffected by this addition.
   */
  growParticles?: boolean;
  /** growing-projectile only - overrides the default impact burst's particle count/size/speed/life. */
  impactBurst?: { count?: number; radiusPx?: number; speed?: number; lifeFrames?: number };
  /** pulse only - how many quick pulses to cycle through over the stroke's duration. Default 5. */
  pulseCount?: number;
  /** pulse only - radius each pulse grows to, in px. Default a plain projectile's own radius. */
  pulseRadiusPx?: number;
  /**
   * charge-burst / pulse only - a second colour to alternate with `color` (Implosion's
   * magenta-and-light-blue mix, per temp/abilities.txt "same as Wei's colour scheme"). Ignored
   * (stays a single colour) when unset.
   */
  secondaryColor?: number;
  /** charge-burst only - how long (ms) particles converge on the target before it detonates. Default ~2000ms. */
  chargeDurationMs?: number;
  /** charge-burst only - how long (ms) the multi-pulse detonation itself lasts. Default ~800ms. */
  burstDurationMs?: number;
  /** charge-burst only - how many pulses the detonation cycles through. Default 10. */
  burstPulseCount?: number;
  /** charge-burst only - radius each detonation pulse grows to, in px. */
  burstRadiusPx?: number;
}

export interface AttackAnimationSpec {
  /** Almost always length 1. Wei and Evayne use two, played in sequence. */
  strokes: readonly AttackAnimationStroke[];
}

// Short and punchy - a swipe, not a flight.
export const SLASH_DURATION_MS = 250;
export const ARROW_DURATION_MS = 350;
export const PROJECTILE_DURATION_MS = 450;
// Fireblast's ball: part of this is spent growing in place before it even
// launches, so the whole thing takes noticeably longer than a plain projectile.
export const GROWING_PROJECTILE_DURATION_MS = 700;
// Lightning and beam span the full attacker-defender distance instantly -
// there's no travel phase, just a fixed visible duration.
export const LIGHTNING_DURATION_MS = 1000;
export const BEAM_DURATION_MS = 1000;
// Fireblast's default split of GROWING_PROJECTILE_DURATION_MS between growing in place and
// travelling to the target - exported so AttackAnimationPlayer.ts can derive the same default
// ms values a stroke's own growDurationMs/travelDurationMs override when left unset.
export const GROWING_PROJECTILE_GROW_FRACTION = 0.25;
// 5 fast pulses over ~0.4s, per Homing Missile's "5 fast white pulses" in temp/abilities.txt.
export const PULSE_STROKE_DURATION_MS = 400;
// Acidic Brew's flask / Hidden Potential's scroll - "much slower (travel speed) compared to
// other projectiles" per temp/abilities.txt, vs. a plain projectile's 450ms.
export const ARC_PROJECTILE_DURATION_MS = 950;
// Implosion's "charging up effect lasts ~2s" per temp/abilities.txt, then a quick multi-pulse
// detonation - 10 pulses at roughly the same ~80ms-per-pulse cadence Homing Missile's 5-over-400ms already uses.
export const CHARGE_BURST_DEFAULT_CHARGE_MS = 2000;
export const CHARGE_BURST_DEFAULT_BURST_MS = 800;

export function strokeDurationMs(stroke: AttackAnimationStroke): number {
  switch (stroke.kind) {
    case "slash":
      return SLASH_DURATION_MS;
    case "arrow":
      return ARROW_DURATION_MS;
    case "projectile":
      return PROJECTILE_DURATION_MS;
    case "growing-projectile":
      return (
        (stroke.growDurationMs ?? GROWING_PROJECTILE_DURATION_MS * GROWING_PROJECTILE_GROW_FRACTION) +
        (stroke.travelDurationMs ?? GROWING_PROJECTILE_DURATION_MS * (1 - GROWING_PROJECTILE_GROW_FRACTION))
      );
    case "lightning":
      return LIGHTNING_DURATION_MS;
    case "beam":
      return BEAM_DURATION_MS;
    case "pulse":
      return PULSE_STROKE_DURATION_MS;
    case "arc-projectile":
      return ARC_PROJECTILE_DURATION_MS;
    case "charge-burst":
      return (
        (stroke.chargeDurationMs ?? CHARGE_BURST_DEFAULT_CHARGE_MS) +
        (stroke.burstDurationMs ?? CHARGE_BURST_DEFAULT_BURST_MS)
      );
  }
}

/**
 * Total time the animation occupies, end to end - the last stroke's delay
 * plus its own duration. This is what the caller needs synchronously, before
 * the animation has actually started playing, to reserve the right gap on
 * the shared indicator timeline.
 */
export function attackAnimationDurationMs(spec: AttackAnimationSpec): number {
  return Math.max(...spec.strokes.map((s) => (s.delayMs ?? 0) + strokeDurationMs(s)));
}

const WHITE = 0xffffff;
const DARK_PURPLE = 0x5b21b6;
const EMBER_ORANGE = 0xfb923c;
const LIGHT_BLUE = 0x38bdf8;
const JOKER_PURPLE = 0x9333ea;
const SILVER = 0xc0c0c0;
const DARK_BLUE = 0x1e3a8a;
const DARK_GREEN = 0x166534;
const YELLOW = 0xfacc15;
const PINK = 0xec4899;
const RED_ORANGE = 0xf4511e;
const YELLOW_GREEN = 0xa3e635;
const MAGENTA = 0xd946ef;
const BRIGHT_GREEN = 0x4ade80;
const GOLD = 0xfbbf24;
const BLOOD_RED = 0xdc2626;
const GUNMETAL_GRAY = 0x9ca3af;

export const DEFAULT_ATTACK_ANIMATION: AttackAnimationSpec = {
  strokes: [{ kind: "slash", color: WHITE }],
};

/**
 * One entry per unit with a bespoke attack animation, keyed by definitionId.
 * Anything not listed here (summons, any future unit) falls through
 * to DEFAULT_ATTACK_ANIMATION via attackAnimationFor - deliberately not
 * special-cased, the same way UnitArt.ts's art lookups fall back gracefully.
 */
const ATTACK_ANIMATION_BY_DEFINITION_ID: Record<string, AttackAnimationSpec> = {
  chronos: { strokes: [{ kind: "slash", color: DARK_PURPLE }] },
  ember: { strokes: [{ kind: "projectile", color: EMBER_ORANGE, particles: true }] },
  harbinger: { strokes: [{ kind: "projectile", color: LIGHT_BLUE }] },
  joker: { strokes: [{ kind: "lightning", color: JOKER_PURPLE }] },
  valor: { strokes: [{ kind: "slash", color: SILVER }] },
  zenith: { strokes: [{ kind: "beam", color: DARK_BLUE }] },
  artemis: { strokes: [{ kind: "arrow", color: WHITE }] },
  auroth: { strokes: [{ kind: "projectile", color: WHITE }] },
  branch: { strokes: [{ kind: "projectile", color: DARK_GREEN }] },
  dirge: { strokes: [{ kind: "slash", color: WHITE }] },
  discharge: { strokes: [{ kind: "lightning", color: YELLOW }] },
  flint: { strokes: [{ kind: "projectile", color: GUNMETAL_GRAY }] },
  // Three quick, smaller slashes - Evayne's two-slash shape with one more beat.
  grivath: {
    strokes: [
      { kind: "slash", color: WHITE, scale: 0.6 },
      { kind: "slash", color: WHITE, scale: 0.6, delayMs: 220 },
      { kind: "slash", color: WHITE, scale: 0.6, delayMs: 440 },
    ],
  },
  evayne: {
    strokes: [
      { kind: "slash", color: WHITE, scale: 0.7 },
      { kind: "slash", color: WHITE, scale: 0.7, delayMs: 300 },
    ],
  },
  lanaya: { strokes: [{ kind: "slash", color: PINK }] },
  lucifer: { strokes: [{ kind: "slash", color: RED_ORANGE }] },
  maxwell: { strokes: [{ kind: "beam", color: LIGHT_BLUE }] },
  mercurial: { strokes: [{ kind: "slash", color: JOKER_PURPLE }] },
  noctis: {
    strokes: [
      { kind: "slash", color: BLOOD_RED },
      { kind: "slash", color: BLOOD_RED, delayMs: 300 },
    ],
  },
  shawl: { strokes: [{ kind: "lightning", color: DARK_BLUE }] },
  spitter: { strokes: [{ kind: "projectile", color: YELLOW_GREEN }] },
  thaddeus: { strokes: [{ kind: "slash", color: WHITE }] },
  wei: {
    strokes: [
      { kind: "slash", color: MAGENTA },
      { kind: "slash", color: DARK_BLUE, delayMs: 400 },
    ],
  },
  yuki: { strokes: [{ kind: "projectile", color: WHITE }] },
};

export function attackAnimationFor(definitionId: string | null | undefined): AttackAnimationSpec {
  if (!definitionId) return DEFAULT_ATTACK_ANIMATION;
  return ATTACK_ANIMATION_BY_DEFINITION_ID[definitionId] ?? DEFAULT_ATTACK_ANIMATION;
}

/**
 * Cause labels that get the new travel animation: every RPS-resolved attack
 * except Cloak and Dagger, whose attacker and defender end up on the same
 * tile (Evayne teleports onto it first) - a travel animation would have
 * nowhere to travel. Derived from ENCOUNTER_CAUSE_LABELS rather than a fresh
 * list so the two sets can't drift apart.
 */
export const ATTACK_ANIMATION_CAUSE_LABELS: ReadonlySet<string> = new Set(
  [...ENCOUNTER_CAUSE_LABELS].filter((label) => label !== "Cloak and Dagger"),
);

/** True when a damage event should play a travel animation before its indicator. */
export function qualifiesForAttackAnimation(event: VfxEvent): boolean {
  return (
    event.type === "damage" &&
    event.causeLabel !== null &&
    ATTACK_ANIMATION_CAUSE_LABELS.has(event.causeLabel) &&
    !!event.sourceUnitId &&
    !!event.targetUnitId
  );
}

const DARK_GREEN_LIGHTNING = 0x15803d;

/**
 * Ability damage that gets its own travel/impact animation, keyed by
 * causeLabel (damage events carry no abilityId - see VfxEvent) rather than by
 * unit definitionId, since these are one-off ability effects rather than a
 * unit's basic attack. Perplexing Shot's entry here is only ever the *first*
 * hit's spec - see perplexingShotSpecForChainIndex for the rest of the chain.
 */
export const ABILITY_DAMAGE_ANIMATION_BY_CAUSE_LABEL: Record<string, AttackAnimationSpec> = {
  Fireblast: { strokes: [{ kind: "growing-projectile", color: EMBER_ORANGE, particles: true }] },
  "Perplexing Shot": { strokes: [{ kind: "lightning", color: DARK_GREEN_LIGHTNING, width: 2 }] },
  "Orbital Beam": { strokes: [{ kind: "beam", color: WHITE, toColor: DARK_BLUE }] },
  // A pylon's own volley (PylonOrbitalBeam.java) fires under this distinct causeLabel,
  // not "Orbital Beam" - same sky-beam treatment either way.
  "Pylon Orbital Beam": { strokes: [{ kind: "beam", color: WHITE, toColor: DARK_BLUE }] },
  // White lightning from near the caster's own portrait - see ScheduleVfxBatch's
  // simultaneous-group handling, which fires every target's bolt from one batch together.
  "Eye of the Storm": { strokes: [{ kind: "lightning", color: WHITE, width: 3 }] },
  // Pink beam from the unit that redirected the blow (see ScheduleVfxBatch's
  // redirectedFromUnitId handling) to wherever it actually landed.
  Refraction: { strokes: [{ kind: "beam", color: PINK }] },
  // Grows in place for ~2s with inward particles (reusing Fireblast's "growing-projectile"
  // kind at overridden durations/radius, per temp/abilities.txt: "should grow to about 80%
  // the size of a tile... particles should go inward, similar to sanity's eclipse effect"),
  // then a quick launch and a bigger/brighter burst on impact.
  "Plasma Cannon": {
    strokes: [
      {
        kind: "growing-projectile",
        color: LIGHT_BLUE,
        growDurationMs: 2000,
        travelDurationMs: 350,
        projectileRadiusPx: 27, // ~80% of HEX_SIZE (34), see board/Board.ts
        growParticles: true,
        impactBurst: { count: 26, radiusPx: 6, speed: 3, lifeFrames: 30 },
      },
    ],
  },
  // A missile-icon projectile (sky-drop origin, see ScheduleVfxBatch.ts) followed by 5 fast
  // white pulses at the impact point before the damage indicator reveals, per
  // temp/abilities.txt.
  "Homing Missile": {
    strokes: [
      { kind: "projectile", color: WHITE, icon: "missile" },
      // Bumped from 34 (one tile) to comfortably cover a 1-tile splash ring, since a single
      // cast now plays exactly one explosion for however many units it actually hits.
      { kind: "pulse", color: WHITE, delayMs: PROJECTILE_DURATION_MS, pulseCount: 5, pulseRadiusPx: 70 },
    ],
  },
  // Wei - onUse fires one DamageEvent("Implosion") per victim (primary + splash), all sharing
  // sourceUnitId in one synchronous loop - grouped in ScheduleVfxBatch.ts the same way Homing
  // Missile's splash is, so N victims still play exactly one charge+detonation. Magenta/light
  // blue per temp/abilities.txt's "same thing like wei's colour scheme".
  Implosion: {
    strokes: [
      { kind: "charge-burst", color: MAGENTA, secondaryColor: LIGHT_BLUE, burstRadiusPx: 85 },
    ],
  },
};

export function abilityDamageAnimationFor(causeLabel: string | null | undefined): AttackAnimationSpec | null {
  if (!causeLabel) return null;
  return ABILITY_DAMAGE_ANIMATION_BY_CAUSE_LABEL[causeLabel] ?? null;
}

/**
 * Cast-only animations, dispatched directly from Board.playVfx's ability_used handling rather
 * than through ScheduleVfxBatch's damage-triggered qualifiesForAbilityDamageAnimation pipeline -
 * kept in a table of its own, keyed by abilityId rather than causeLabel, so nothing here can
 * ever collide with a real damage event's own causeLabel the way "Acidic Brew" once did (its
 * recurring acid tick reuses that exact display name as its own causeLabel - see
 * AcidPoolEffect.java - which meant registering it in ABILITY_DAMAGE_ANIMATION_BY_CAUSE_LABEL
 * made every tick replay the cast's own flask throw). Poison Bloom and Hidden Potential don't
 * have that exact collision today, but the same design mistake would apply the moment either
 * one gained a same-named recurring tick, so all cast-only entries live here regardless.
 */
export const CAST_ANIMATION_BY_ABILITY_ID: Record<string, AttackAnimationSpec> = {
  // "Similar to Fireblast" per temp/abilities.txt - literally Fireblast's own defaults, just
  // dark green (PoisonBloom.java fires no DamageEvent at cast time - the poison itself deals
  // damage later, under causeLabel "Poison").
  poison_bloom: { strokes: [{ kind: "growing-projectile", color: DARK_GREEN }] },
  // A bright-green flask, thrown in a small arc, spinning throughout (AcidicBrew.onUse fires no
  // DamageEvent either - it targets a bare tile).
  acidic_brew: {
    strokes: [{ kind: "arc-projectile", color: BRIGHT_GREEN, icon: "flask", arcHeightPx: 40, spinSpeed: 0.35 }],
  },
  // A dark-blue scroll with a continuous golden trail, arced and spinning like the flask
  // (HiddenPotential.onUse fires no DamageEvent - it's a support unlock, not damage).
  hidden_potential: {
    strokes: [
      {
        kind: "arc-projectile",
        color: DARK_BLUE,
        icon: "scroll",
        arcHeightPx: 40,
        spinSpeed: 0.35,
        trailColor: GOLD,
        landingBurst: { color: GOLD, count: 20, radiusPx: 3, speed: 2.2, lifeFrames: 26 },
      },
    ],
  },
  // A large plain white snowball (no icon - see arc-projectile's plain-circle fallback in
  // AttackAnimationPlayer.ts), arced onto the destination tile. SnowGolem.onUse targets a bare
  // tile (or two, upgraded) with no DamageEvent of its own.
  snow_golem: { strokes: [{ kind: "arc-projectile", color: WHITE, arcHeightPx: 50, projectileRadiusPx: 16 }] },
};

export function castAnimationFor(abilityId: string | null | undefined): AttackAnimationSpec | null {
  if (!abilityId) return null;
  return CAST_ANIMATION_BY_ABILITY_ID[abilityId] ?? null;
}

/** True when a damage event should play an ability-specific animation before its indicator. */
export function qualifiesForAbilityDamageAnimation(event: VfxEvent): boolean {
  return (
    event.type === "damage" &&
    event.causeLabel !== null &&
    event.causeLabel in ABILITY_DAMAGE_ANIMATION_BY_CAUSE_LABEL &&
    !!event.sourceUnitId &&
    !!event.targetUnitId
  );
}

const PERPLEXING_SHOT_BASE_WIDTH_PX = 2;
const PERPLEXING_SHOT_MAX_WIDTH_PX = 6;

/**
 * Perplexing Shot only - each bounce's bolt is 1px wider than the last
 * (capped as a safeguard), per temp/abilities2.txt. `chainIndex` is 0 for the
 * initial hit, 1 for the first bounce, and so on - the caller (ScheduleVfxBatch)
 * tracks that per source unit across one vfx batch, since damage events carry
 * no explicit "which bounce is this" field of their own.
 */
export function perplexingShotSpecForChainIndex(chainIndex: number): AttackAnimationSpec {
  const width = Math.min(PERPLEXING_SHOT_MAX_WIDTH_PX, PERPLEXING_SHOT_BASE_WIDTH_PX + chainIndex);
  return { strokes: [{ kind: "lightning", color: DARK_GREEN_LIGHTNING, width }] };
}

export interface PartitionedVfxBatch {
  /** Events that get the new travel-animation-then-indicator treatment. */
  attackEvents: VfxEvent[];
  /** Ability damage that gets its own animation - see ABILITY_DAMAGE_ANIMATION_BY_CAUSE_LABEL. */
  abilityDamageEvents: VfxEvent[];
  /** Everything else - unchanged generic-burst + staggered-indicator path. */
  otherEvents: VfxEvent[];
}

/** Splits a batch in arrival order, preserved within each bucket. */
export function partitionVfxBatch(events: VfxEvent[]): PartitionedVfxBatch {
  const attackEvents: VfxEvent[] = [];
  const abilityDamageEvents: VfxEvent[] = [];
  const otherEvents: VfxEvent[] = [];
  for (const event of events) {
    if (qualifiesForAttackAnimation(event)) {
      attackEvents.push(event);
    } else if (qualifiesForAbilityDamageAnimation(event)) {
      abilityDamageEvents.push(event);
    } else {
      otherEvents.push(event);
    }
  }
  return { attackEvents, abilityDamageEvents, otherEvents };
}
