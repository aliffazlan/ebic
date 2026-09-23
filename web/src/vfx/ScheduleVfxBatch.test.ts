import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { scheduleVfxBatch, type VfxBatchDeps } from "./ScheduleVfxBatch";
import { IndicatorScheduler } from "./IndicatorScheduler";
import { LIGHTNING_DURATION_MS, PROJECTILE_DURATION_MS, SLASH_DURATION_MS } from "./AttackAnimations";
import type { VfxEvent } from "../types/contract";

function damage(
  causeLabel: string | null,
  amount = 10,
  sourceUnitId: string | null = "u9",
  targetUnitId: string | null = "u1",
): VfxEvent {
  return { type: "damage", abilityId: null, sourceUnitId, targetUnitId, amount, causeLabel };
}

function makeDeps(overrides: Partial<VfxBatchDeps> = {}): {
  deps: VfxBatchDeps;
  calls: string[];
} {
  const calls: string[] = [];
  const deps: VfxBatchDeps = {
    indicators: new IndicatorScheduler(),
    playVfx: (events) => calls.push(`playVfx:${events.length}`),
    playAttackAnimation: (from, to, _spec, onComplete) => {
      calls.push(`anim:${from.x},${from.y}->${to.x},${to.y}`);
      onComplete();
    },
    showIndicators: (specs) => calls.push(`indicators:${specs.map((s) => s.unitId).join(",")}`),
    showIndicatorAt: (pos, spec) => calls.push(`indicatorAt:${spec.unitId}@${pos.x},${pos.y}`),
    resolveUnitPosition: (unitId) => (unitId ? { x: unitId.length, y: 0 } : null),
    beginPendingHpChange: (unitId) => calls.push(`pending:${unitId}`),
    playMarkConsumedBurst: (from, to) => calls.push(`burst:${from.x},${from.y}->${to.x},${to.y}`),
    sourceDefinitionId: () => "chronos",
    ...overrides,
  };
  return { deps, calls };
}

describe("scheduleVfxBatch", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("routes only non-attack events to the generic burst", () => {
    const { deps, calls } = makeDeps();
    scheduleVfxBatch([damage("Attack", 10), damage("Poison", 5)], deps);
    expect(calls[0]).toBe("playVfx:1"); // only the Poison event
  });

  it("plays each attack event's animation before its own indicator", () => {
    const { deps, calls } = makeDeps();
    scheduleVfxBatch([damage("Attack", 34, "u9", "u1")], deps);
    vi.advanceTimersByTime(0);
    const animIndex = calls.findIndex((c) => c.startsWith("anim:"));
    const indicatorIndex = calls.findIndex((c) => c.startsWith("indicatorAt:"));
    expect(animIndex).toBeGreaterThanOrEqual(0);
    expect(indicatorIndex).toBeGreaterThan(animIndex);
  });

  it("gaps each chained hit's start by the previous hit's own animation duration", () => {
    // Timeless Strike: several "Attack" events between the same pair in one
    // batch. Board's real playAttackAnimation completes via its own ticker in
    // lockstep with the same nominal duration used here for scheduling - this
    // test covers what ScheduleVfxBatch itself guarantees: step N+1 isn't
    // even started until durationMs has elapsed since step N started.
    const order: string[] = [];
    const { deps } = makeDeps({
      playAttackAnimation: (_from, _to, _spec, onComplete) => {
        order.push("start");
        onComplete();
      },
      showIndicatorAt: (_pos, spec) => order.push(`indicator:${spec.text}`),
    });
    scheduleVfxBatch(
      [damage("Attack", 20, "u9", "u1"), damage("Attack", 12, "u9", "u1"), damage("Attack", 8, "u9", "u1")],
      deps,
    );

    vi.advanceTimersByTime(0);
    expect(order).toEqual(["start", "indicator:-20"]);

    vi.advanceTimersByTime(SLASH_DURATION_MS - 1);
    expect(order).toEqual(["start", "indicator:-20"]);

    vi.advanceTimersByTime(1);
    expect(order).toEqual(["start", "indicator:-20", "start", "indicator:-12"]);

    vi.advanceTimersByTime(SLASH_DURATION_MS);
    expect(order).toEqual(["start", "indicator:-20", "start", "indicator:-12", "start", "indicator:-8"]);
  });

  it("still shows an indicator for a miss (amount 0 with a real cause label)", () => {
    const { deps, calls } = makeDeps();
    scheduleVfxBatch([damage("Attack", 0, "u9", "u1")], deps);
    vi.advanceTimersByTime(0);
    expect(calls.some((c) => c.startsWith("indicatorAt:u1"))).toBe(true);
  });

  it("still staggers non-attack groups by kind on the same shared timeline", () => {
    const { deps, calls } = makeDeps();
    scheduleVfxBatch([damage("Poison", 5, "u9", "u1"), damage("Burn", 3, "u9", "u2")], deps);
    vi.advanceTimersByTime(0);
    expect(calls).toContain("indicators:u1"); // poison group first
    calls.length = 0;
    vi.advanceTimersByTime(750);
    expect(calls).toContain("indicators:u2"); // burn group after the gap
  });

  it("registers hp-pending eagerly, before any animation or indicator has been shown", () => {
    const { deps, calls } = makeDeps();
    scheduleVfxBatch([damage("Attack", 20, "u9", "u1"), damage("Poison", 5, "u9", "u2")], deps);
    // Nothing has run yet (no advanceTimersByTime) - pending registration
    // already happened synchronously inside scheduleVfxBatch itself.
    expect(calls).toEqual(["playVfx:1", "pending:u1", "pending:u2"]);
  });

  it("freezes each attack event's position eagerly - a later change to what resolveUnitPosition returns doesn't affect it", () => {
    let positionY = 0;
    const { deps, calls } = makeDeps({
      resolveUnitPosition: () => ({ x: 0, y: positionY }),
    });
    scheduleVfxBatch([damage("Attack", 20, "u9", "u1")], deps);
    // Simulates the unit relocating (e.g. to the graveyard) before the
    // deferred animation step actually runs.
    positionY = 999;
    vi.advanceTimersByTime(0);
    expect(calls.find((c) => c.startsWith("anim:"))).toBe("anim:0,0->0,0");
  });

  it("routes Fireblast/Perplexing Shot/Orbital Beam damage through playAttackAnimation, not the generic burst", () => {
    const { deps, calls } = makeDeps();
    scheduleVfxBatch(
      [damage("Fireblast", 24, "u9", "u1"), damage("Poison", 5, "u9", "u1")],
      deps,
    );
    expect(calls[0]).toBe("playVfx:1"); // only the Poison event
    vi.advanceTimersByTime(0);
    expect(calls.some((c) => c.startsWith("anim:"))).toBe(true);
  });

  it("chains Perplexing Shot's bounces from hit to hit, widening the bolt each time", () => {
    const specs: number[] = [];
    const { deps, calls } = makeDeps({
      playAttackAnimation: (from, to, spec, onComplete) => {
        calls.push(`anim:${from.x},${from.y}->${to.x},${to.y}`);
        specs.push(spec.strokes[0].width!);
        onComplete();
      },
    });
    // u9 (caster) -> u1 -> u22 -> u333, three hits in one chain.
    scheduleVfxBatch(
      [
        damage("Perplexing Shot", 30, "u9", "u1"),
        damage("Perplexing Shot", 50, "u9", "u22"),
        damage("Perplexing Shot", 70, "u9", "u333"),
      ],
      deps,
    );
    vi.advanceTimersByTime(0);
    let anims = calls.filter((c) => c.startsWith("anim:"));
    expect(anims[0]).toBe("anim:2,0->2,0"); // caster ("u9".length=2) -> u1 ("u1".length=2)
    vi.advanceTimersByTime(LIGHTNING_DURATION_MS);
    anims = calls.filter((c) => c.startsWith("anim:"));
    expect(anims[1]).toBe("anim:2,0->3,0"); // previous target (u1) -> u22 ("u22".length=3)
    vi.advanceTimersByTime(LIGHTNING_DURATION_MS);
    anims = calls.filter((c) => c.startsWith("anim:"));
    expect(anims[2]).toBe("anim:3,0->4,0"); // previous target (u22) -> u333
    expect(specs).toEqual([2, 3, 4]);
  });

  it("spawns Orbital Beam from above its target and staggers repeated beams by their own 600ms gap", () => {
    const { deps, calls } = makeDeps();
    scheduleVfxBatch(
      [damage("Orbital Beam", 60, "u9", "u1"), damage("Orbital Beam", 60, "u9", "u1")],
      deps,
    );
    vi.advanceTimersByTime(0);
    expect(calls.filter((c) => c.startsWith("anim:"))).toEqual(["anim:2,-600->2,0"]);
    vi.advanceTimersByTime(599);
    expect(calls.filter((c) => c.startsWith("anim:"))).toHaveLength(1);
    vi.advanceTimersByTime(1);
    expect(calls.filter((c) => c.startsWith("anim:"))).toEqual(["anim:2,-600->2,0", "anim:2,-600->2,0"]);
  });

  function abilityUsed(abilityId: string, sourceUnitId: string): VfxEvent {
    return { type: "ability_used", abilityId, sourceUnitId, targetUnitId: null, amount: null, causeLabel: null };
  }

  it("delays a Double Draw shot by a short lead-in after the shot before it", () => {
    const starts: number[] = [];
    const specs: number[] = [];
    const { deps } = makeDeps({
      sourceDefinitionId: () => "flint",
      playAttackAnimation: (_from, _to, spec, onComplete) => {
        starts.push(Date.now());
        specs.push(spec.strokes[0].delayMs ?? 0);
        onComplete();
      },
    });
    const t0 = Date.now();
    scheduleVfxBatch([damage("Attack", 20, "flint1", "u1"), damage("Double Draw", 15, "flint1", "u2")], deps);
    vi.advanceTimersByTime(PROJECTILE_DURATION_MS * 3);
    // Second step starts right after the first shot's own duration, but its stroke carries the lead-in.
    expect(starts.map((t) => t - t0)).toEqual([0, PROJECTILE_DURATION_MS]);
    expect(specs).toEqual([0, 200]);
  });

  it("plays the mark-consumed burst on the hit that consumed the mark, and hides the proc from playVfx", () => {
    const { deps, calls } = makeDeps({ sourceDefinitionId: () => "flint" });
    scheduleVfxBatch(
      [
        damage("Attack", 20, "flint1", "u1"),
        abilityUsed("high_noon_mark_consumed", "u1"),
        damage("Attack", 50, "flint1", "u1"),
      ],
      deps,
    );
    expect(calls[0]).toBe("playVfx:0");
    vi.advanceTimersByTime(PROJECTILE_DURATION_MS * 3);
    const bursts = calls.filter((c) => c.startsWith("burst:"));
    expect(bursts).toHaveLength(1);
    // The burst lands with the second hit (-50), not the first.
    const burstIndex = calls.indexOf(bursts[0]);
    expect(calls.slice(0, burstIndex).filter((c) => c.startsWith("indicatorAt:"))).toHaveLength(1);
  });

  it("spaces a High Noon barrage's shots at least 500ms apart", () => {
    const starts: number[] = [];
    const { deps } = makeDeps({
      sourceDefinitionId: () => "flint",
      playAttackAnimation: (_from, _to, _spec, onComplete) => {
        starts.push(Date.now());
        onComplete();
      },
    });
    const t0 = Date.now();
    scheduleVfxBatch(
      [damage("Attack", 20, "flint1", "u1"), damage("Attack", 20, "flint1", "u1"), abilityUsed("high_noon", "flint1")],
      deps,
    );
    vi.advanceTimersByTime(2000);
    expect(starts.map((t) => t - t0)).toEqual([0, 500]);
  });
});
