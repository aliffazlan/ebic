// Per-unit attack-animation config, plus the pure logic that decides which
// VfxEvents get one and how long to reserve for it. No PixiJS here - see
// AttackAnimationPlayer.ts for the rendering side, kept separate so this file
// stays unit-testable under vitest's node environment.

import { ENCOUNTER_CAUSE_LABELS } from "./VfxIndicators";
import type { VfxEvent } from "../types/contract";

export type AttackAnimationKind = "slash" | "arrow" | "projectile" | "lightning" | "beam";

export interface AttackAnimationStroke {
  kind: AttackAnimationKind;
  color: number;
  /** Scale multiplier for slash/arrow art. Default 1. */
  scale?: number;
  /** Delay in ms after the whole animation starts before this stroke begins. Default 0. */
  delayMs?: number;
  /** Ember only: spawns a light trailing sparkle behind the travelling orb. */
  particles?: boolean;
}

export interface AttackAnimationSpec {
  /** Almost always length 1. Wei and Evayne use two, played in sequence. */
  strokes: readonly AttackAnimationStroke[];
}

// Short and punchy - a swipe, not a flight.
export const SLASH_DURATION_MS = 250;
export const ARROW_DURATION_MS = 350;
export const PROJECTILE_DURATION_MS = 450;
// Lightning and beam span the full attacker-defender distance instantly -
// there's no travel phase, just a fixed visible duration.
export const LIGHTNING_DURATION_MS = 1000;
export const BEAM_DURATION_MS = 1000;

export function strokeDurationMs(kind: AttackAnimationKind): number {
  switch (kind) {
    case "slash":
      return SLASH_DURATION_MS;
    case "arrow":
      return ARROW_DURATION_MS;
    case "projectile":
      return PROJECTILE_DURATION_MS;
    case "lightning":
      return LIGHTNING_DURATION_MS;
    case "beam":
      return BEAM_DURATION_MS;
  }
}

/**
 * Total time the animation occupies, end to end - the last stroke's delay
 * plus its own duration. This is what the caller needs synchronously, before
 * the animation has actually started playing, to reserve the right gap on
 * the shared indicator timeline.
 */
export function attackAnimationDurationMs(spec: AttackAnimationSpec): number {
  return Math.max(...spec.strokes.map((s) => (s.delayMs ?? 0) + strokeDurationMs(s.kind)));
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

export const DEFAULT_ATTACK_ANIMATION: AttackAnimationSpec = {
  strokes: [{ kind: "slash", color: WHITE }],
};

/**
 * One entry per unit with a bespoke attack animation, keyed by definitionId.
 * Anything not listed here (grivath, summons, any future unit) falls through
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

export interface PartitionedVfxBatch {
  /** Events that get the new travel-animation-then-indicator treatment. */
  attackEvents: VfxEvent[];
  /** Everything else - unchanged generic-burst + staggered-indicator path. */
  otherEvents: VfxEvent[];
}

/** Splits a batch in arrival order, preserved within each bucket. */
export function partitionVfxBatch(events: VfxEvent[]): PartitionedVfxBatch {
  const attackEvents: VfxEvent[] = [];
  const otherEvents: VfxEvent[] = [];
  for (const event of events) {
    (qualifiesForAttackAnimation(event) ? attackEvents : otherEvents).push(event);
  }
  return { attackEvents, otherEvents };
}
