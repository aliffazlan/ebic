// Render-priority helpers for stacked units sharing a tile: only one unit can
// normally occupy a tile, but a handful of abilities intentionally stack a
// second unit onto an existing occupant. Board.ts uses these to decide paint
// order, which unit's HP bar to show, and the stack-count badge.

import type { UnitSnapshot } from "../types/contract";

/**
 * True for a unit that should render *below* any other live occupant sharing
 * its tile. Pylon and Killer Drone are checked by name (their definitionId/
 * unitType both collapse to generic "basic"/"BASIC" server-side, so name is
 * the only reliable signal). Anything else is checked by the HIDDEN status
 * flag on any active effect - not by caster name or effect name - so a unit
 * currently hidden via Cloak and Dagger, a latched upgraded Feast, or any
 * future ability that copies either (e.g. a unit mimicking one of them) is
 * automatically covered with no extra logic.
 */
export function isLowPriorityUnit(unit: UnitSnapshot): boolean {
  if (unit.name === "Pylon") return true;
  if (unit.name === "Drone") return true;
  return unit.effects.some((e) => e.statusFlags.includes("HIDDEN"));
}

/**
 * Groups live (non-dead) units by tile key "q,r". Dead units are excluded,
 * matching the existing stack-picker/click-handler convention in Board.ts.
 */
export function groupUnitsByTile(units: UnitSnapshot[]): Map<string, UnitSnapshot[]> {
  const groups = new Map<string, UnitSnapshot[]>();
  for (const unit of units) {
    if (unit.dead) continue;
    const key = `${unit.q},${unit.r}`;
    const list = groups.get(key);
    if (list) list.push(unit);
    else groups.set(key, [unit]);
  }
  return groups;
}

/**
 * Deterministically picks which unit in a same-tile group should render on
 * top / show its HP bar: the selected unit wins outright if it's a member of
 * this group; otherwise prefer a non-low-priority ("normal") unit; ties
 * within a priority class break on lexicographically smallest id (a minor,
 * rare edge case - e.g. two low-priority units stacked with nothing else).
 */
export function pickTopmostUnit(group: UnitSnapshot[], selectedUnitId: string | null): UnitSnapshot {
  if (selectedUnitId) {
    const selected = group.find((u) => u.id === selectedUnitId);
    if (selected) return selected;
  }
  const normalTier = group.filter((u) => !isLowPriorityUnit(u));
  const pool = normalTier.length > 0 ? normalTier : group;
  return [...pool].sort((a, b) => a.id.localeCompare(b.id))[0];
}
