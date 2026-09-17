import { describe, expect, it } from "vitest";
import { hpSeparatorThresholds } from "./UnitHp";

describe("hpSeparatorThresholds", () => {
  it("places a mark every 100 maxHp, excluding both edges", () => {
    expect(hpSeparatorThresholds(1000)).toEqual([100, 200, 300, 400, 500, 600, 700, 800, 900]);
  });

  it("includes a mark for a maxHp between two hundreds", () => {
    expect(hpSeparatorThresholds(150)).toEqual([100]);
  });

  it("excludes maxHp itself when it's an exact multiple of 100", () => {
    expect(hpSeparatorThresholds(200)).toEqual([100]);
  });

  it("has no marks when maxHp is 100 or less", () => {
    expect(hpSeparatorThresholds(100)).toEqual([]);
    expect(hpSeparatorThresholds(50)).toEqual([]);
    expect(hpSeparatorThresholds(0)).toEqual([]);
  });
});
