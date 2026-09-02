import { describe, expect, it } from "vitest";
import {
  groupIndicators,
  ICON_COLOR_PLACEHOLDER,
  ICON_SVG,
  indicatorFor,
  indicatorGroup,
  isMissEvent,
} from "./VfxIndicators";
import type { IndicatorKind } from "./VfxIndicators";
import type { VfxEvent } from "../types/contract";

function damage(amount: number, causeLabel: string | null, targetUnitId = "u1"): VfxEvent {
  return { type: "damage", abilityId: null, sourceUnitId: "u9", targetUnitId, amount, causeLabel };
}

function heal(amount: number, targetUnitId = "u1"): VfxEvent {
  // The server sends heals with no source and no cause label.
  return { type: "heal", abilityId: null, sourceUnitId: null, targetUnitId, amount, causeLabel: null };
}

describe("isMissEvent", () => {
  it("treats a 0-damage encounter as a miss", () => {
    expect(isMissEvent(damage(0, "Attack"))).toBe(true);
    expect(isMissEvent(damage(0, "Counterstrike"))).toBe(true);
    expect(isMissEvent(damage(0, "Cloak and Dagger"))).toBe(true);
  });

  it("does not treat an ability that rolled 0 as a miss", () => {
    // Only encounter-resolved damage can be turned aside; a DoT ticking for 0
    // is just a DoT ticking for 0.
    expect(isMissEvent(damage(0, "Poison"))).toBe(false);
    expect(isMissEvent(damage(0, "Sanity's Eclipse"))).toBe(false);
    expect(isMissEvent(damage(0, null))).toBe(false);
  });

  it("does not treat a landed attack as a miss", () => {
    expect(isMissEvent(damage(7, "Attack"))).toBe(false);
  });
});

describe("indicatorFor", () => {
  it("shows MISS in grey rather than a zero", () => {
    const spec = indicatorFor(damage(0, "Attack"));
    expect(spec).toMatchObject({ text: "MISS", kind: "miss", color: 0x9ca3af });
  });

  it("colours a basic attack red and an ability blue", () => {
    expect(indicatorFor(damage(34, "Attack"))).toMatchObject({ text: "-34", kind: "attack", color: 0xef4444 });
    expect(indicatorFor(damage(60, "Sanity's Eclipse"))).toMatchObject({ text: "-60", kind: "ability", color: 0x60a5fa });
  });

  it("treats every other damage-over-time as ability damage", () => {
    for (const label of ["Acidic Brew", "Blizzard", "Cold Embrace", "Doom", "Decay"]) {
      expect(indicatorFor(damage(9, label))).toMatchObject({ kind: "ability", color: 0x60a5fa });
    }
  });

  it("gives burn and poison their own colours", () => {
    expect(indicatorFor(damage(15, "Burn"))).toMatchObject({ kind: "burn", color: 0xf97316 });
    expect(indicatorFor(damage(12, "Poison"))).toMatchObject({ kind: "poison", color: 0x15803d });
  });

  it("keeps poison visually distinct from a heal", () => {
    const poison = indicatorFor(damage(12, "Poison"));
    const healed = indicatorFor(heal(12));
    expect(poison?.color).not.toBe(healed?.color);
  });

  it("shows a heal as a signed green number", () => {
    expect(indicatorFor(heal(40))).toMatchObject({ text: "+40", kind: "heal", color: 0x22c55e });
  });

  it("falls back to ability damage when the server sends no cause label", () => {
    // Only CombatEngine back-fills "Attack", so an unlabelled hit came from
    // something calling takeDamage directly - i.e. an ability.
    expect(indicatorFor(damage(5, null))).toMatchObject({ kind: "ability" });
  });

  it("returns null for events that get no number", () => {
    const noNumber: VfxEvent[] = [
      { type: "ability_used", abilityId: "fireblast", sourceUnitId: "u9", targetUnitId: "u1", amount: null, causeLabel: null },
      { type: "death", abilityId: null, sourceUnitId: null, targetUnitId: "u1", amount: null, causeLabel: null },
      { type: "status_applied", abilityId: null, sourceUnitId: "u9", targetUnitId: "u1", amount: null, causeLabel: null },
    ];
    for (const event of noNumber) expect(indicatorFor(event)).toBeNull();
  });

  it("suppresses a non-miss that landed for zero", () => {
    // A floating "0" is noise; the combat log still records it.
    expect(indicatorFor(damage(0, "Poison"))).toBeNull();
    expect(indicatorFor(heal(0))).toBeNull();
  });

  it("returns null when there is no unit to float over", () => {
    expect(indicatorFor(damage(10, "Attack", null as unknown as string))).toBeNull();
  });
});

describe("indicatorGroup", () => {
  it("reads poison, then burn, then everything else", () => {
    expect(indicatorGroup("poison")).toBeLessThan(indicatorGroup("burn"));
    expect(indicatorGroup("burn")).toBeLessThan(indicatorGroup("attack"));
    for (const kind of ["attack", "ability", "heal", "miss"] as const) {
      expect(indicatorGroup(kind)).toBe(indicatorGroup("attack"));
    }
  });
});

describe("groupIndicators", () => {
  it("splits a turn-start batch into poison then burn", () => {
    const groups = groupIndicators([
      damage(15, "Burn", "u2"),
      damage(12, "Poison", "u1"),
      damage(8, "Poison", "u2"),
    ]);
    expect(groups).toHaveLength(2);
    expect(groups[0].map((s) => s.kind)).toEqual(["poison", "poison"]);
    expect(groups[1].map((s) => s.kind)).toEqual(["burn"]);
  });

  it("keeps an ordinary cast in a single group so nothing is delayed", () => {
    const groups = groupIndicators([
      damage(34, "Attack", "u1"),
      heal(10, "u2"),
      { type: "ability_used", abilityId: "move", sourceUnitId: "u9", targetUnitId: null, amount: null, causeLabel: null },
    ]);
    expect(groups).toHaveLength(1);
    expect(groups[0]).toHaveLength(2);
  });

  it("drops empty groups and events with no indicator", () => {
    expect(groupIndicators([])).toEqual([]);
    expect(groupIndicators([damage(0, "Poison")])).toEqual([]);
  });

  it("preserves within-group order so the log and the board agree", () => {
    const groups = groupIndicators([damage(1, "Poison", "a"), damage(2, "Poison", "b")]);
    expect(groups[0].map((s) => s.unitId)).toEqual(["a", "b"]);
  });
});

describe("ICON_SVG", () => {
  const ALL_KINDS: IndicatorKind[] = ["attack", "ability", "burn", "poison", "heal", "miss"];

  it("has an icon for every indicator kind", () => {
    // The Record type already enforces this at compile time; this catches the
    // rarer case of an entry existing but being empty.
    for (const kind of ALL_KINDS) {
      expect(ICON_SVG[kind]).toContain("<svg");
    }
  });

  it("leaves every icon's fill as the placeholder the renderer substitutes", () => {
    // An icon that ships with a literal colour instead would render in that
    // colour forever, ignoring the kind's palette - and black-on-dark board is
    // invisible, so this would be easy to miss by eye.
    for (const kind of ALL_KINDS) {
      expect(ICON_SVG[kind]).toContain(ICON_COLOR_PLACEHOLDER);
      expect(ICON_SVG[kind]).not.toContain("#000000");
    }
  });
});
