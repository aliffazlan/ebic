import { describe, expect, it } from "vitest";
import { DisplayedUnitState } from "./DisplayedUnitState";

describe("DisplayedUnitState", () => {
  it("syncs a fresh unit immediately - nothing pending, nothing to catch up on", () => {
    const state = new DisplayedUnitState();
    state.syncToTruth("u1", 100, false);
    expect(state.hpFor("u1", -1)).toBe(100);
    expect(state.isDead("u1")).toBe(false);
  });

  it("holds displayed state while something is still pending, even against a lower/dead truth", () => {
    const state = new DisplayedUnitState();
    state.syncToTruth("u1", 100, false);
    state.beginPendingChange("u1");

    // The server has already applied the killing blow and sent it - but the
    // hit's own animation/indicator hasn't played yet.
    state.syncToTruth("u1", 0, true);

    expect(state.hpFor("u1", -1)).toBe(100);
    expect(state.isDead("u1")).toBe(false);
  });

  it("accumulates several applyChange calls in order", () => {
    const state = new DisplayedUnitState();
    state.syncToTruth("u1", 100, false);
    state.beginPendingChange("u1");
    state.beginPendingChange("u1");

    state.applyChange("u1", -20);
    expect(state.hpFor("u1", -1)).toBe(80);

    state.applyChange("u1", -30);
    expect(state.hpFor("u1", -1)).toBe(50);
    expect(state.isDead("u1")).toBe(false);
  });

  it("marks dead and reports justDied when a delta crosses to <= 0", () => {
    const state = new DisplayedUnitState();
    state.syncToTruth("u1", 20, false);
    state.beginPendingChange("u1");

    const result = state.applyChange("u1", -25);
    expect(result.justDied).toBe(true);
    expect(state.isDead("u1")).toBe(true);
  });

  it("treats an already-dead unit's further changes as a no-op", () => {
    const state = new DisplayedUnitState();
    state.syncToTruth("u1", 20, false);
    state.beginPendingChange("u1");
    state.beginPendingChange("u1");

    expect(state.applyChange("u1", -25).justDied).toBe(true);
    const hpAfterDeath = state.hpFor("u1", -1);

    const second = state.applyChange("u1", -999);
    expect(second.justDied).toBe(false);
    expect(state.hpFor("u1", -1)).toBe(hpAfterDeath);
    expect(state.isDead("u1")).toBe(true);
  });

  it("resumes syncing to truth once pending drains back to zero", () => {
    const state = new DisplayedUnitState();
    state.syncToTruth("u1", 100, false);
    state.beginPendingChange("u1");
    state.applyChange("u1", -40);

    // Pending is back to 0 - a later, unrelated snapshot should be trusted again.
    state.syncToTruth("u1", 60, false);
    expect(state.hpFor("u1", -1)).toBe(60);
  });

  it("forget clears hp, dead, and pending bookkeeping", () => {
    const state = new DisplayedUnitState();
    state.syncToTruth("u1", 100, false);
    state.beginPendingChange("u1");
    state.applyChange("u1", -200);
    expect(state.isDead("u1")).toBe(true);

    state.forget("u1");
    expect(state.hpFor("u1", -1)).toBe(-1);
    expect(state.isDead("u1")).toBe(false);

    // No pending left over either - a fresh sync takes immediately.
    state.syncToTruth("u1", 5, false);
    expect(state.hpFor("u1", -1)).toBe(5);
  });
});
