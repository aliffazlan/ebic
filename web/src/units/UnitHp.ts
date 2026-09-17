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

/**
 * Translucent-white barrier bar, drawn as an overlay on the on-board token and a
 * separate bar above the HP bar in the sidebar/encounter-card panel. Reuses
 * hpSeparatorThresholds (generic over any max value) for its own separator ticks.
 */
export const BARRIER_FILL_COLOR = 0xffffff;
export const BARRIER_FILL_ALPHA = 0.6;
export const BARRIER_SEPARATOR_COLOR = 0xffffff;
export const BARRIER_FILL_CSS = "rgba(255, 255, 255, 0.6)";
export const BARRIER_SEPARATOR_COLOR_CSS = "#ffffff";
