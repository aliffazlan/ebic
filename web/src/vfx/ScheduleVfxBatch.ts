// Turns one incoming "vfx" batch into calls against the board, sequenced
// correctly on the shared IndicatorScheduler timeline. Dependency-injected
// (see VfxBatchDeps) so MatchScreen and FixtureScreen's demo harness share
// this exact orchestration rather than each hand-rolling their own stagger
// loop that can silently drift apart.

import { attackAnimationDurationMs, attackAnimationFor, partitionVfxBatch, type AttackAnimationSpec } from "./AttackAnimations";
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
export function scheduleVfxBatch(events: VfxEvent[], deps: VfxBatchDeps): void {
  const { attackEvents, otherEvents } = partitionVfxBatch(events);
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

  const groups = groupIndicators(otherEvents);
  for (const group of groups) {
    for (const spec of group) deps.beginPendingHpChange(spec.unitId);
  }
  for (const group of groups) {
    deps.indicators.enqueue(() => deps.showIndicators(group), INDICATOR_GROUP_GAP_MS);
  }
}
