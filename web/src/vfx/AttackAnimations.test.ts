import { describe, expect, it } from "vitest";
import {
  ABILITY_DAMAGE_ANIMATION_BY_CAUSE_LABEL,
  ATTACK_ANIMATION_CAUSE_LABELS,
  BEAM_DURATION_MS,
  DEFAULT_ATTACK_ANIMATION,
  LIGHTNING_DURATION_MS,
  SLASH_DURATION_MS,
  abilityDamageAnimationFor,
  attackAnimationDurationMs,
  attackAnimationFor,
  partitionVfxBatch,
  perplexingShotSpecForChainIndex,
  qualifiesForAbilityDamageAnimation,
  qualifiesForAttackAnimation,
} from "./AttackAnimations";
import type { VfxEvent } from "../types/contract";

function damage(
  causeLabel: string | null,
  amount = 10,
  sourceUnitId: string | null = "u9",
  targetUnitId: string | null = "u1",
): VfxEvent {
  return { type: "damage", abilityId: null, sourceUnitId, targetUnitId, amount, causeLabel };
}

describe("attackAnimationFor", () => {
  it("resolves every configured unit to its own spec", () => {
    expect(attackAnimationFor("chronos").strokes[0]).toMatchObject({ kind: "slash", color: 0x5b21b6 });
    expect(attackAnimationFor("ember").strokes[0]).toMatchObject({ kind: "projectile", particles: true });
    expect(attackAnimationFor("zenith").strokes[0]).toMatchObject({ kind: "beam" });
    expect(attackAnimationFor("discharge").strokes[0]).toMatchObject({ kind: "lightning" });
    expect(attackAnimationFor("artemis").strokes[0]).toMatchObject({ kind: "arrow" });
  });

  it("gives Wei and Evayne two strokes with a delay on the second", () => {
    const wei = attackAnimationFor("wei");
    expect(wei.strokes).toHaveLength(2);
    expect(wei.strokes[1].delayMs).toBe(400);

    const evayne = attackAnimationFor("evayne");
    expect(evayne.strokes).toHaveLength(2);
    expect(evayne.strokes[0].scale).toBe(0.7);
    expect(evayne.strokes[1].delayMs).toBe(300);
  });

  it("falls back to the default white slash for grivath, summons, and unknown ids", () => {
    for (const id of ["grivath", "maxwell_drone", "yuki_golem", "not_a_real_unit", null, undefined]) {
      expect(attackAnimationFor(id)).toEqual(DEFAULT_ATTACK_ANIMATION);
    }
  });
});

describe("attackAnimationDurationMs", () => {
  it("is a single stroke's duration for a one-stroke spec", () => {
    expect(attackAnimationDurationMs(attackAnimationFor("chronos"))).toBe(SLASH_DURATION_MS);
    expect(attackAnimationDurationMs(attackAnimationFor("discharge"))).toBe(LIGHTNING_DURATION_MS);
    expect(attackAnimationDurationMs(attackAnimationFor("maxwell"))).toBe(BEAM_DURATION_MS);
  });

  it("is the last stroke's delay plus its own duration for multi-stroke specs", () => {
    expect(attackAnimationDurationMs(attackAnimationFor("evayne"))).toBe(300 + SLASH_DURATION_MS);
    expect(attackAnimationDurationMs(attackAnimationFor("wei"))).toBe(400 + SLASH_DURATION_MS);
  });
});

describe("ATTACK_ANIMATION_CAUSE_LABELS", () => {
  it("includes every encounter cause except Cloak and Dagger", () => {
    expect(ATTACK_ANIMATION_CAUSE_LABELS.has("Attack")).toBe(true);
    expect(ATTACK_ANIMATION_CAUSE_LABELS.has("Counterstrike")).toBe(true);
    expect(ATTACK_ANIMATION_CAUSE_LABELS.has("Duel")).toBe(true);
    expect(ATTACK_ANIMATION_CAUSE_LABELS.has("Cloak and Dagger")).toBe(false);
  });
});

describe("qualifiesForAttackAnimation", () => {
  it("qualifies a landed or missed basic attack, counterstrike, and duel", () => {
    expect(qualifiesForAttackAnimation(damage("Attack", 34))).toBe(true);
    expect(qualifiesForAttackAnimation(damage("Attack", 0))).toBe(true); // a miss still gets the animation
    expect(qualifiesForAttackAnimation(damage("Counterstrike", 12))).toBe(true);
    expect(qualifiesForAttackAnimation(damage("Duel", 12))).toBe(true);
  });

  it("excludes Cloak and Dagger - attacker and defender share a tile", () => {
    expect(qualifiesForAttackAnimation(damage("Cloak and Dagger", 15))).toBe(false);
  });

  it("excludes ability damage and non-damage events", () => {
    expect(qualifiesForAttackAnimation(damage("Poison", 5))).toBe(false);
    expect(qualifiesForAttackAnimation(damage(null, 5))).toBe(false);
    expect(
      qualifiesForAttackAnimation({
        type: "heal",
        abilityId: null,
        sourceUnitId: null,
        targetUnitId: "u1",
        amount: 10,
        causeLabel: null,
      }),
    ).toBe(false);
  });

  it("falls back to the old path when either unit id is missing", () => {
    expect(qualifiesForAttackAnimation(damage("Attack", 10, null, "u1"))).toBe(false);
    expect(qualifiesForAttackAnimation(damage("Attack", 10, "u9", null))).toBe(false);
  });
});

describe("partitionVfxBatch", () => {
  it("splits a mixed batch, preserving order within each bucket", () => {
    const events = [
      damage("Poison", 8, "u9", "u1"),
      damage("Attack", 34, "u9", "u2"),
      damage("Cloak and Dagger", 15, "u3", "u4"),
      damage("Counterstrike", 6, "u2", "u9"),
      damage("Burn", 3, null, "u1"),
    ];
    const { attackEvents, abilityDamageEvents, otherEvents } = partitionVfxBatch(events);
    expect(attackEvents).toEqual([events[1], events[3]]);
    expect(abilityDamageEvents).toEqual([]);
    expect(otherEvents).toEqual([events[0], events[2], events[4]]);
  });

  it("routes ability damage (Fireblast, Perplexing Shot, Orbital Beam) to its own bucket", () => {
    const events = [
      damage("Fireblast", 24, "u1", "u2"),
      damage("Attack", 10, "u1", "u2"),
      damage("Perplexing Shot", 30, "u3", "u4"),
      damage("Orbital Beam", 60, "u5", "u6"),
    ];
    const { attackEvents, abilityDamageEvents, otherEvents } = partitionVfxBatch(events);
    expect(attackEvents).toEqual([events[1]]);
    expect(abilityDamageEvents).toEqual([events[0], events[2], events[3]]);
    expect(otherEvents).toEqual([]);
  });

  it("routes an empty batch to three empty buckets", () => {
    expect(partitionVfxBatch([])).toEqual({ attackEvents: [], abilityDamageEvents: [], otherEvents: [] });
  });
});

describe("qualifiesForAbilityDamageAnimation", () => {
  it("qualifies Fireblast, Perplexing Shot, and Orbital Beam damage with both unit ids present", () => {
    expect(qualifiesForAbilityDamageAnimation(damage("Fireblast"))).toBe(true);
    expect(qualifiesForAbilityDamageAnimation(damage("Perplexing Shot"))).toBe(true);
    expect(qualifiesForAbilityDamageAnimation(damage("Orbital Beam"))).toBe(true);
    expect(qualifiesForAbilityDamageAnimation(damage("Pylon Orbital Beam"))).toBe(true);
  });

  it("excludes basic attacks, other ability damage, and missing unit ids", () => {
    expect(qualifiesForAbilityDamageAnimation(damage("Attack"))).toBe(false);
    expect(qualifiesForAbilityDamageAnimation(damage("Poison"))).toBe(false);
    expect(qualifiesForAbilityDamageAnimation(damage("Fireblast", 24, null, "u1"))).toBe(false);
    expect(qualifiesForAbilityDamageAnimation(damage("Fireblast", 24, "u9", null))).toBe(false);
  });
});

describe("abilityDamageAnimationFor", () => {
  it("resolves each configured cause label to its own spec", () => {
    expect(abilityDamageAnimationFor("Fireblast")).toBe(ABILITY_DAMAGE_ANIMATION_BY_CAUSE_LABEL.Fireblast);
    expect(abilityDamageAnimationFor("Orbital Beam")?.strokes[0]).toMatchObject({ kind: "beam", toColor: expect.any(Number) });
  });

  it("returns null for an unconfigured or missing cause label", () => {
    expect(abilityDamageAnimationFor("Poison")).toBeNull();
    expect(abilityDamageAnimationFor(null)).toBeNull();
  });
});

describe("perplexingShotSpecForChainIndex", () => {
  it("starts at 2px and adds 1px per chain index", () => {
    expect(perplexingShotSpecForChainIndex(0).strokes[0]).toMatchObject({ width: 2 });
    expect(perplexingShotSpecForChainIndex(1).strokes[0]).toMatchObject({ width: 3 });
    expect(perplexingShotSpecForChainIndex(3).strokes[0]).toMatchObject({ width: 5 });
  });

  it("caps width at 6px as a safeguard", () => {
    expect(perplexingShotSpecForChainIndex(10).strokes[0]).toMatchObject({ width: 6 });
  });
});
