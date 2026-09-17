import { describe, expect, it } from "vitest";
import { groupUnitsByTile, isLowPriorityUnit, pickTopmostUnit } from "./UnitStacking";
import type { EffectSnapshot, UnitSnapshot } from "../types/contract";

function effect(name: string, statusFlags: string[]): EffectSnapshot {
  return { name, description: "", category: "BUFF", permanent: false, remainingTurns: 1, statusFlags, extraInfo: null, partnerUnitId: null };
}

function unit(id: string, overrides: Partial<UnitSnapshot> = {}): UnitSnapshot {
  return {
    id, name: id, team: "PLAYER_ONE", definitionId: id.toLowerCase(), unitType: "CHAMPION",
    q: 0, r: 0,
    currentHp: 100, maxHp: 100, currentBarrierHp: 0, maxBarrierHp: 0,
    strength: 10, agility: 10, intelligence: 10,
    attackRange: 1, minAttackRange: 0,
    dead: false, hasMovedThisTurn: false, hasAttackedThisTurn: false,
    statusFlags: [], abilities: [], effects: [],
    ...overrides,
  };
}

describe("isLowPriorityUnit", () => {
  it("treats Pylon as low priority by name", () => {
    expect(isLowPriorityUnit(unit("p1", { name: "Pylon" }))).toBe(true);
  });

  it("treats Drone as low priority by name", () => {
    expect(isLowPriorityUnit(unit("d1", { name: "Drone" }))).toBe(true);
  });

  it("treats any unit with a HIDDEN-flagged effect as low priority, regardless of effect name or caster", () => {
    const u = unit("mimic", { name: "Joker", effects: [effect("Copycat Ability", ["HIDDEN"])] });
    expect(isLowPriorityUnit(u)).toBe(true);
  });

  it("does NOT treat a unit with only UNTARGETABLE (no HIDDEN) as low priority", () => {
    const u = unit("shielded", { effects: [effect("Some Future Ability", ["UNTARGETABLE"])] });
    expect(isLowPriorityUnit(u)).toBe(false);
  });

  it("does not flag an ordinary unit with no effects", () => {
    expect(isLowPriorityUnit(unit("valor"))).toBe(false);
  });
});

describe("groupUnitsByTile", () => {
  it("excludes dead units", () => {
    const groups = groupUnitsByTile([unit("a", { q: 1, r: 1, dead: true }), unit("b", { q: 1, r: 1 })]);
    expect(groups.get("1,1")).toEqual([unit("b", { q: 1, r: 1 })]);
  });

  it("groups two units sharing a tile", () => {
    const groups = groupUnitsByTile([unit("a", { q: 2, r: 3 }), unit("b", { q: 2, r: 3 }), unit("c", { q: 5, r: 5 })]);
    expect(groups.get("2,3")?.map((u) => u.id)).toEqual(["a", "b"]);
    expect(groups.get("5,5")?.map((u) => u.id)).toEqual(["c"]);
  });

  it("gives a singleton its own one-length group", () => {
    const groups = groupUnitsByTile([unit("a", { q: 0, r: 0 })]);
    expect(groups.size).toBe(1);
    expect(groups.get("0,0")).toHaveLength(1);
  });
});

describe("pickTopmostUnit", () => {
  it("picks the selected unit even if it's low-priority", () => {
    const low = unit("pylon", { name: "Pylon" });
    const normal = unit("champ");
    expect(pickTopmostUnit([low, normal], "pylon")).toBe(low);
  });

  it("picks the normal-tier unit over a low-priority one when nothing is selected", () => {
    const low = unit("pylon", { name: "Pylon" });
    const normal = unit("champ");
    expect(pickTopmostUnit([low, normal], null)).toBe(normal);
  });

  it("breaks ties by lexicographically smallest id when all units are the same tier", () => {
    const a = unit("zzz");
    const b = unit("aaa");
    expect(pickTopmostUnit([a, b], null)).toBe(b);

    const lowA = unit("pylon-z", { name: "Pylon" });
    const lowB = unit("pylon-a", { name: "Pylon" });
    expect(pickTopmostUnit([lowA, lowB], null)).toBe(lowB);
  });
});
