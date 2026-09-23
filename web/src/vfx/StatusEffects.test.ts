import { describe, expect, it } from "vitest";
import { GENERIC_STUN_VISUAL, STATUS_VISUAL_BY_EFFECT_NAME, statusVisualFor } from "./StatusEffects";

function effect(name: string, statusFlags: readonly string[] = []) {
  return { name, statusFlags };
}

describe("statusVisualFor", () => {
  it("resolves every listed effect name to its own spec", () => {
    for (const name of Object.keys(STATUS_VISUAL_BY_EFFECT_NAME)) {
      expect(statusVisualFor(effect(name))).toBe(STATUS_VISUAL_BY_EFFECT_NAME[name]);
    }
  });

  it("falls back to the generic stun visual for an unlisted effect carrying STUNNED", () => {
    expect(statusVisualFor(effect("Timeless Strike Stun", ["STUNNED"]))).toEqual(GENERIC_STUN_VISUAL);
    expect(statusVisualFor(effect("Missile Stun", ["STUNNED", "ROOTED"]))).toEqual(GENERIC_STUN_VISUAL);
  });

  it("prefers a dedicated visual over the generic stun fallback when an effect has both", () => {
    // Oblivion Confinement and Cold Embrace both carry STUNNED/FROZEN but have their own entry.
    const result = statusVisualFor(effect("Oblivion Confinement", ["STUNNED", "INVULNERABLE"]));
    expect(result).toBe(STATUS_VISUAL_BY_EFFECT_NAME["Oblivion Confinement"]);
  });

  it("renders nothing for an unlisted, non-stunning effect", () => {
    expect(statusVisualFor(effect("Insight", []))).toBeNull();
    expect(statusVisualFor(effect("Sprout", ["ROOTED"]))).toBeNull();
  });

  it("Poison Bloom is marked thick, plain Poison is not", () => {
    expect(STATUS_VISUAL_BY_EFFECT_NAME["Poison Bloom"]).toMatchObject({ thick: true });
    expect(STATUS_VISUAL_BY_EFFECT_NAME.Poison).not.toHaveProperty("thick", true);
  });

  it("marks High Noon's target with a crosshair and gives Bloodwake inward particles", () => {
    expect(statusVisualFor(effect("High Noon Mark"))).toMatchObject({ mode: "unit", kind: "crosshair" });
    expect(statusVisualFor(effect("Bloodwake"))).toMatchObject({ mode: "unit", kind: "inward-particles", color: 0xdc2626 });
  });
});
