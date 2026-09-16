// Turns one incoming "vfx" batch into calls against the board, sequenced
// correctly on the shared IndicatorScheduler timeline. Dependency-injected
// (see VfxBatchDeps) so MatchScreen and FixtureScreen's demo harness share
// this exact orchestration rather than each hand-rolling their own stagger
// loop that can silently drift apart.

import {
  abilityDamageAnimationFor,
  attackAnimationDurationMs,
  attackAnimationFor,
  partitionVfxBatch,
  perplexingShotSpecForChainIndex,
  type AttackAnimationSpec,
} from "./AttackAnimations";
import { groupIndicators, indicatorFor, type IndicatorSpec } from "./VfxIndicators";
import { INDICATOR_GROUP_GAP_MS, type IndicatorScheduler } from "./IndicatorScheduler";
import type { VfxEvent } from "../types/contract";

export interface VfxBatchDeps {
  indicators: IndicatorScheduler;
  /** Unchanged generic particle burst - now only ever called with non-attack events. */
  playVfx: (events: VfxEvent[]) => void;
  /** Plays one attack's travel animation between two already-resolved points, then calls onComplete. */
  playAttackAnimation: (
    from: { x: number; y: number },
    to: { x: number; y: number },
    spec: AttackAnimationSpec,
    onComplete: () => void,
  ) => void;
  showIndicators: (specs: IndicatorSpec[]) => void;
  /** Shows one indicator at an already-resolved position. */
  showIndicatorAt: (pos: { x: number; y: number }, spec: IndicatorSpec) => void;
  /** Resolves a unit's current position, live, at call time. */
  resolveUnitPosition: (unitId: string | null) => { x: number; y: number } | null;
  /** Registers one more event still to be visually applied to this unit's displayed hp/dead. */
  beginPendingHpChange: (unitId: string) => void;
  /** Resolves an event's attacker to its unit-type id, for the animation lookup. */
  sourceDefinitionId: (event: VfxEvent) => string | null;
}

/**
 * Partitions a vfx batch into attack events (get the new travel animation,
 * then their own indicator) and everything else (unchanged: an immediate
 * generic burst, plus indicators staggered by kind). Both share the one
 * IndicatorScheduler cursor, so ordering is preserved end to end - including
 * across a chained sequence of "Attack" events for the same attacker/defender
 * pair (e.g. Timeless Strike): each gets its own enqueue() step, gapped by
 * its own animation's real duration, so hit N+1 can't start before hit N's
 * animation and indicator have both finished.
 *
 * Position and hp-pending registration for every qualifying event are
 * resolved *eagerly*, synchronously, before any enqueue() callback runs -
 * i.e. before this batch's matching "state" message can possibly be
 * processed. Attacker/defender don't move as part of an attack itself (only
 * a later Move or a lethal hit's death/graveyard relocation changes that),
 * so freezing here is correct for the animation's whole duration, however
 * long this batch's queue takes to actually drain - see Board.playAttackAnimation
 * and DisplayedUnitState for why this matters.
 */
/**
 * Sky-drop origin for Orbital Beam's per-hit beam - offset far enough above
 * the target that it reads as "from above the map" at any zoom/pan, per
 * temp/abilities2.txt. Plain pixel math, not a real map position - nothing
 * downstream needs it to correspond to an actual tile.
 */
const ORBITAL_BEAM_SKY_OFFSET_PX = 600;
/**
 * Orbital Beam's stagger between successive beams in one cast (the upgrade's
 * global volley) - shorter than the beam's own 800ms duration so the next
 * one spawns in just before the previous finishes, per temp/abilities2.txt.
 */
const ORBITAL_BEAM_GAP_MS = 600;
/**
 * Eye of the Storm's bolts originate near the caster's own portrait, not its
 * centre - plain pixel offset upward, same "not a real map position" idiom as
 * ORBITAL_BEAM_SKY_OFFSET_PX above, tuned to roughly the top of a unit's icon.
 */
const EYE_OF_THE_STORM_ORIGIN_OFFSET_PX = 22;

export function scheduleVfxBatch(events: VfxEvent[], deps: VfxBatchDeps): void {
  const { attackEvents, abilityDamageEvents, otherEvents } = partitionVfxBatch(events);
  deps.playVfx(otherEvents);

  const attackSteps = attackEvents.map((event) => {
    const spec = attackAnimationFor(deps.sourceDefinitionId(event));
    const durationMs = attackAnimationDurationMs(spec);
    const from = deps.resolveUnitPosition(event.sourceUnitId);
    const to = deps.resolveUnitPosition(event.targetUnitId);
    const indicatorSpec = indicatorFor(event);
    if (indicatorSpec) deps.beginPendingHpChange(indicatorSpec.unitId);
    return { spec, durationMs, from, to, indicatorSpec };
  });
  for (const { spec, durationMs, from, to, indicatorSpec } of attackSteps) {
    deps.indicators.enqueue(() => {
      if (!from || !to) return; // defensive - shouldn't happen for a well-formed event
      deps.playAttackAnimation(from, to, spec, () => {
        if (indicatorSpec) deps.showIndicatorAt(to, indicatorSpec);
      });
    }, durationMs);
  }

  // Ability damage that gets its own animation (Fireblast, Perplexing Shot's chain,
  // Orbital Beam's sky beam) - same enqueue/onComplete-gated-indicator shape as a
  // basic attack, but keyed by causeLabel rather than the attacker's unit type, and
  // each cause label gets its own position/gap override below. Perplexing Shot's
  // chain index is tracked per source unit across this one batch, since a bounce's
  // damage event carries no "which link in the chain is this" field of its own -
  // resetting per batch is correct because one cast's whole chain always arrives
  // together (PerplexingShot.java fires every jump's DamageEvent in the same turn).
  const perplexingChainIndexBySource = new Map<string, number>();
  const perplexingPrevTargetBySource = new Map<string, { x: number; y: number }>();

  // Eye of the Storm resolves every target in one synchronous backend hook (see
  // EyeOfTheStormEffect), so all its damage events already arrive together in this
  // one batch - handled as its own group below so every bolt fires in the same
  // enqueue() step instead of the staggered one-event-per-step treatment every
  // other ability damage cause label gets.
  const eyeOfTheStormEvents = abilityDamageEvents.filter((e) => e.causeLabel === "Eye of the Storm");
  // HomingMissileEffect.onExpire (backend) deals impact damage to the locked target, then
  // splash damage to every adjacent enemy, all in one method call under this same causeLabel
  // and sourceUnitId - one missile, one explosion, but previously N staggered animations for
  // N victims. Grouped by source below so it plays exactly once per cast regardless of how
  // many units it actually hits - see that group's own handling further down.
  const homingMissileEvents = abilityDamageEvents.filter((e) => e.causeLabel === "Homing Missile");
  // Implosion.onUse (backend) strikes the primary target then every enemy within its splash
  // radius, all in one synchronous loop under this causeLabel and sourceUnitId - same
  // one-caster/several-same-batch-events shape as Homing Missile, grouped below so N victims
  // still play exactly one charge+detonation.
  const implosionEvents = abilityDamageEvents.filter((e) => e.causeLabel === "Implosion");
  const staggeredAbilityDamageEvents = abilityDamageEvents.filter(
    (e) => e.causeLabel !== "Eye of the Storm" && e.causeLabel !== "Homing Missile" && e.causeLabel !== "Implosion",
  );

  const abilityDamageSteps = staggeredAbilityDamageEvents.map((event) => {
    const to = deps.resolveUnitPosition(event.targetUnitId);
    let from = deps.resolveUnitPosition(event.sourceUnitId);
    let spec = abilityDamageAnimationFor(event.causeLabel);
    let gapMs = spec ? attackAnimationDurationMs(spec) : 0;

    if (event.causeLabel === "Perplexing Shot" && event.sourceUnitId) {
      const chainIndex = perplexingChainIndexBySource.get(event.sourceUnitId) ?? 0;
      spec = perplexingShotSpecForChainIndex(chainIndex);
      const prevTarget = perplexingPrevTargetBySource.get(event.sourceUnitId);
      if (chainIndex > 0 && prevTarget) from = prevTarget;
      perplexingChainIndexBySource.set(event.sourceUnitId, chainIndex + 1);
      if (to) perplexingPrevTargetBySource.set(event.sourceUnitId, to);
      gapMs = attackAnimationDurationMs(spec);
    } else if ((event.causeLabel === "Orbital Beam" || event.causeLabel === "Pylon Orbital Beam") && to) {
      // A pylon's own volley (PylonOrbitalBeam.java) fires under this distinct causeLabel,
      // not "Orbital Beam" (see ABILITY_DAMAGE_ANIMATION_BY_CAUSE_LABEL) - same sky-beam
      // origin either way. Previously only "Orbital Beam" was matched here, so a pylon's
      // beam fell through to the default `from` (its own position) instead of the sky.
      from = { x: to.x, y: to.y - ORBITAL_BEAM_SKY_OFFSET_PX };
      gapMs = ORBITAL_BEAM_GAP_MS;
    } else if (event.causeLabel === "Refraction" && event.redirectedFromUnitId) {
      // The beam should run from whoever redirected the blow (Lanaya), not the
      // original attacker (sourceUnitId) - see VfxEvent.redirectedFromUnitId.
      from = deps.resolveUnitPosition(event.redirectedFromUnitId);
    }

    const indicatorSpec = indicatorFor(event);
    if (indicatorSpec) deps.beginPendingHpChange(indicatorSpec.unitId);
    return { spec, gapMs, from, to, indicatorSpec };
  });
  for (const { spec, gapMs, from, to, indicatorSpec } of abilityDamageSteps) {
    if (!spec) continue; // defensive - shouldn't happen, causeLabel was already checked to qualify
    deps.indicators.enqueue(() => {
      if (!from || !to) return;
      deps.playAttackAnimation(from, to, spec, () => {
        if (indicatorSpec) deps.showIndicatorAt(to, indicatorSpec);
      });
    }, gapMs);
  }

  const eyeOfTheStormBySource = new Map<string, VfxEvent[]>();
  for (const event of eyeOfTheStormEvents) {
    const key = event.sourceUnitId ?? "";
    const list = eyeOfTheStormBySource.get(key) ?? [];
    list.push(event);
    eyeOfTheStormBySource.set(key, list);
  }
  for (const groupEvents of eyeOfTheStormBySource.values()) {
    const spec = abilityDamageAnimationFor("Eye of the Storm");
    if (!spec) continue; // defensive - shouldn't happen, the table entry was just added above
    const gapMs = attackAnimationDurationMs(spec);
    const strikes = groupEvents.map((event) => {
      const casterPos = deps.resolveUnitPosition(event.sourceUnitId);
      const from = casterPos ? { x: casterPos.x, y: casterPos.y - EYE_OF_THE_STORM_ORIGIN_OFFSET_PX } : null;
      const to = deps.resolveUnitPosition(event.targetUnitId);
      const indicatorSpec = indicatorFor(event);
      if (indicatorSpec) deps.beginPendingHpChange(indicatorSpec.unitId);
      return { from, to, indicatorSpec };
    });
    // One enqueue() step for the whole group - every strike's playAttackAnimation call
    // starts within the same synchronous callback, so all bolts flash together rather
    // than one-after-another like every other ability damage cause label.
    deps.indicators.enqueue(() => {
      for (const { from, to, indicatorSpec } of strikes) {
        if (!from || !to) continue;
        deps.playAttackAnimation(from, to, spec, () => {
          if (indicatorSpec) deps.showIndicatorAt(to, indicatorSpec);
        });
      }
    }, gapMs);
  }

  const homingMissileBySource = new Map<string, VfxEvent[]>();
  for (const event of homingMissileEvents) {
    const key = event.sourceUnitId ?? "";
    const list = homingMissileBySource.get(key) ?? [];
    list.push(event);
    homingMissileBySource.set(key, list);
  }
  for (const groupEvents of homingMissileBySource.values()) {
    const spec = abilityDamageAnimationFor("Homing Missile");
    if (!spec) continue; // defensive - shouldn't happen, the table entry was just added above
    const gapMs = attackAnimationDurationMs(spec);
    // The locked target (impact) is always buffered before any splash victim - see
    // HomingMissileEffect.onExpire - so the first event in arrival order names the one tile
    // the missile actually strikes; every other event in the group is a splash victim that
    // still gets its own indicator, just no separate animation.
    const to = deps.resolveUnitPosition(groupEvents[0].targetUnitId);
    const from = to ? { x: to.x, y: to.y - ORBITAL_BEAM_SKY_OFFSET_PX } : null;
    const reveals = groupEvents.map((event) => {
      const pos = deps.resolveUnitPosition(event.targetUnitId);
      const indicatorSpec = indicatorFor(event);
      if (indicatorSpec) deps.beginPendingHpChange(indicatorSpec.unitId);
      return { pos, indicatorSpec };
    });
    deps.indicators.enqueue(() => {
      if (!from || !to) return;
      deps.playAttackAnimation(from, to, spec, () => {
        for (const { pos, indicatorSpec } of reveals) {
          if (pos && indicatorSpec) deps.showIndicatorAt(pos, indicatorSpec);
        }
      });
    }, gapMs);
  }

  const implosionBySource = new Map<string, VfxEvent[]>();
  for (const event of implosionEvents) {
    const key = event.sourceUnitId ?? "";
    const list = implosionBySource.get(key) ?? [];
    list.push(event);
    implosionBySource.set(key, list);
  }
  for (const groupEvents of implosionBySource.values()) {
    const spec = abilityDamageAnimationFor("Implosion");
    if (!spec) continue; // defensive - shouldn't happen, the table entry was just added above
    const gapMs = attackAnimationDurationMs(spec);
    // The primary target (struck first, see Implosion.onUse) anchors the charge+detonation;
    // splash victims still get their own indicator, just no separate stationary effect.
    const at = deps.resolveUnitPosition(groupEvents[0].targetUnitId);
    const reveals = groupEvents.map((event) => {
      const pos = deps.resolveUnitPosition(event.targetUnitId);
      const indicatorSpec = indicatorFor(event);
      if (indicatorSpec) deps.beginPendingHpChange(indicatorSpec.unitId);
      return { pos, indicatorSpec };
    });
    deps.indicators.enqueue(() => {
      if (!at) return;
      // No travel phase - from and to are the same stationary point.
      deps.playAttackAnimation(at, at, spec, () => {
        for (const { pos, indicatorSpec } of reveals) {
          if (pos && indicatorSpec) deps.showIndicatorAt(pos, indicatorSpec);
        }
      });
    }, gapMs);
  }

  const groups = groupIndicators(otherEvents);
  for (const group of groups) {
    for (const spec of group) deps.beginPendingHpChange(spec.unitId);
  }
  for (const group of groups) {
    deps.indicators.enqueue(() => deps.showIndicators(group), INDICATOR_GROUP_GAP_MS);
  }
}
