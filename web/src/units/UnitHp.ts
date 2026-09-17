// HP-bar separator ticks: shared between the on-board token bar (Board.ts,
// Pixi canvas) and the sidebar/encounter-card unit panel bar (Hud.ts, DOM),
// so both draw the same marks from the same rule instead of duplicating the
// threshold math.

/** HP bars get a thin separator tick every this many HP, based on maxHp (not currentHp). */
export const HP_SEPARATOR_INTERVAL = 100;

/** Darker shade of the bar's live-fill green (#22c55e / green-500) used for separator ticks. */
export const HP_SEPARATOR_COLOR = 0x15803d; // tailwind green-700
export const HP_SEPARATOR_COLOR_CSS = "#15803d"; // keep in sync with HP_SEPARATOR_COLOR

/**
 * Interior separator thresholds for a bar spanning [0, maxHp]: multiples of
 * HP_SEPARATOR_INTERVAL strictly between 0 and maxHp. Never includes 0 or
 * maxHp itself - those are the bar's own edges, not separators.
 *
 * Examples: maxHp=1000 -> [100,200,...,900] (9); maxHp=150 -> [100];
 * maxHp=200 -> [100] (200 is the right edge, not interior); maxHp<=100 -> [].
 */
export function hpSeparatorThresholds(maxHp: number): number[] {
  const thresholds: number[] = [];
  for (let t = HP_SEPARATOR_INTERVAL; t < maxHp; t += HP_SEPARATOR_INTERVAL) {
    thresholds.push(t);
  }
  return thresholds;
}
