import { describe, expect, it } from "vitest";
import { buildCombatLogLines, type CombatLogSegment } from "./CombatLog";
import { TEAM_COLOR, cssHex } from "../ui/Colors";
import { colorForKind } from "../vfx/VfxIndicators";
import type { Team, UnitSnapshot, VfxEvent } from "../types/contract";

const P1_COLOR = cssHex(TEAM_COLOR.PLAYER_ONE);
const P2_COLOR = cssHex(TEAM_COLOR.PLAYER_TWO);

function unit(id: string, name: string, team: Team): UnitSnapshot {
  return {
    id, name, team,
    definitionId: name.toLowerCase(),
    unitType: "CHAMPION",
    q: 0, r: 0,
    currentHp: 100, maxHp: 100, currentBarrierHp: 0, maxBarrierHp: 0,
    strength: 10, agility: 10, intelligence: 10,
    attackRange: 1, minAttackRange: 0,
    dead: false, hasMovedThisTurn: false, hasAttackedThisTurn: false,
    statusFlags: [],
    abilities: [{ id: "fireblast", name: "Fireblast" } as UnitSnapshot["abilities"][number]],
    effects: [],
  };
}

const UNITS: Record<string, UnitSnapshot> = {
  a: unit("a", "Valor", "PLAYER_ONE"),
  b: unit("b", "Dirge", "PLAYER_TWO"),
};
const findUnit = (id: string | null) => (id ? (UNITS[id] ?? null) : null);

function damage(amount: number, causeLabel: string | null, target = "b", source = "a"): VfxEvent {
  return { type: "damage", abilityId: null, sourceUnitId: source, targetUnitId: target, amount, causeLabel };
}

/** The line as plain text, for asserting phrasing without caring about segments. */
function text(segments: CombatLogSegment[]): string {
  return segments.map((s) => s.text).join("");
}

function segmentFor(segments: CombatLogSegment[], value: string): CombatLogSegment | undefined {
  return segments.find((s) => s.text === value);
}

describe("buildCombatLogLines", () => {
  it("colours each unit name with its own team", () => {
    const [line] = buildCombatLogLines([damage(34, "Attack")], findUnit);
    expect(text(line)).toBe("Valor attacks Dirge for 34 damage");
    expect(segmentFor(line, "Valor")?.color).toBe(P1_COLOR);
    expect(segmentFor(line, "Dirge")?.color).toBe(P2_COLOR);
  });

  it("makes the amount bold and colours it like the floating number", () => {
    const [line] = buildCombatLogLines([damage(34, "Attack")], findUnit);
    const amount = segmentFor(line, "34");
    expect(amount?.bold).toBe(true);
    expect(amount?.color).toBe(cssHex(colorForKind("attack")));
  });

  it("gives a damage-over-time line its own colour, shared by the cause label", () => {
    const [line] = buildCombatLogLines([damage(12, "Poison")], findUnit);
    expect(text(line)).toBe("Dirge takes 12 damage from Poison");
    const poisonColor = cssHex(colorForKind("poison"));
    expect(segmentFor(line, "12")).toMatchObject({ color: poisonColor, bold: true });
    expect(segmentFor(line, "Poison")?.color).toBe(poisonColor);
  });

  it("colours ability damage blue, distinct from a basic attack", () => {
    const [ability] = buildCombatLogLines([damage(60, "Sanity's Eclipse")], findUnit);
    const [attack] = buildCombatLogLines([damage(60, "Attack")], findUnit);
    expect(segmentFor(ability, "60")?.color).toBe(cssHex(colorForKind("ability")));
    expect(segmentFor(ability, "60")?.color).not.toBe(segmentFor(attack, "60")?.color);
  });

  it("reports a turned-aside attack as a miss, with no number", () => {
    const [line] = buildCombatLogLines([damage(0, "Attack")], findUnit);
    expect(text(line)).toBe("Valor missed their attack on Dirge");
    expect(line.some((s) => s.bold)).toBe(false);
  });

  it("still logs a non-miss that landed for zero", () => {
    // The floating indicator suppresses these as noise, but the log is the
    // place you go to find out exactly what happened.
    const [line] = buildCombatLogLines([damage(0, "Poison")], findUnit);
    expect(text(line)).toBe("Dirge takes 0 damage from Poison");
  });

  it("logs heals, which the server sends with no source and no cause label", () => {
    const heal: VfxEvent = {
      type: "heal", abilityId: null, sourceUnitId: null, targetUnitId: "b", amount: 40, causeLabel: null,
    };
    const [line] = buildCombatLogLines([heal], findUnit);
    expect(text(line)).toBe("Dirge heals for 40");
    expect(segmentFor(line, "40")).toMatchObject({ color: cssHex(colorForKind("heal")), bold: true });
    expect(segmentFor(line, "Dirge")?.color).toBe(P2_COLOR);
  });

  it("skips a zero heal", () => {
    const heal: VfxEvent = {
      type: "heal", abilityId: null, sourceUnitId: null, targetUnitId: "b", amount: 0, causeLabel: null,
    };
    expect(buildCombatLogLines([heal], findUnit)).toEqual([]);
  });

  it("names the ability on a cast, resolved from the caster's own ability list", () => {
    const cast: VfxEvent = {
      type: "ability_used", abilityId: "fireblast", sourceUnitId: "a", targetUnitId: "b", amount: null, causeLabel: null,
    };
    const [line] = buildCombatLogLines([cast], findUnit);
    expect(text(line)).toBe("Valor cast Fireblast on Dirge");
  });

  it("skips moves and basic attacks, which their damage line already covers", () => {
    const noisy: VfxEvent[] = [
      { type: "ability_used", abilityId: "move", sourceUnitId: "a", targetUnitId: null, amount: null, causeLabel: null },
      { type: "ability_used", abilityId: "attack", sourceUnitId: "a", targetUnitId: "b", amount: null, causeLabel: null },
      { type: "death", abilityId: null, sourceUnitId: null, targetUnitId: "b", amount: null, causeLabel: null },
      { type: "status_applied", abilityId: null, sourceUnitId: "a", targetUnitId: "b", amount: null, causeLabel: null },
    ];
    expect(buildCombatLogLines(noisy, findUnit)).toEqual([]);
  });

  it("falls back to Unknown for a unit missing from the snapshot", () => {
    const [line] = buildCombatLogLines([damage(5, "Attack", "ghost", "ghost")], findUnit);
    expect(text(line)).toBe("Unknown attacks Unknown for 5 damage");
    // No team, so no team colour to apply.
    expect(segmentFor(line, "Unknown")?.color).toBeUndefined();
  });

  it("returns one line per loggable event, in order", () => {
    const lines = buildCombatLogLines([damage(1, "Poison"), damage(2, "Burn"), damage(3, "Attack")], findUnit);
    expect(lines).toHaveLength(3);
    expect(lines.map((l) => text(l).match(/\d+/)?.[0])).toEqual(["1", "2", "3"]);
  });
});
