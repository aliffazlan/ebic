// A serial timeline for board feedback that must be read one step at a time.
//
// Every step runs at max(now, cursor) and then pushes the cursor forward, so
// work queued later never lands on top of work queued earlier. That's what
// makes the turn-start sequence fall out for free: the DoT groups go in from
// the "vfx" message, the "YOUR TURN" banner goes in from the "state" message
// that arrives immediately behind it, and the banner lands after the numbers
// without either side knowing about the other.
//
// Bare setTimeout rather than window.setTimeout: vitest runs these tests in a
// node environment where `window` doesn't exist.

/**
 * Gap between staggered damage-indicator groups, and between the last group and
 * the "YOUR TURN" banner. At the start of a turn every damage-over-time effect
 * ticks at once; spacing poison, then burn, then everything else makes each
 * source readable without the whole sequence dragging.
 */
export const INDICATOR_GROUP_GAP_MS = 750;

export class IndicatorScheduler {
  private pending = new Set<ReturnType<typeof setTimeout>>();
  /** When the next step is free to run, as an epoch-ms timestamp. */
  private cursor = 0;

  /**
   * Queues `run` at the current end of the timeline, then reserves `gapMs`
   * after it before anything else may run.
   */
  enqueue(run: () => void, gapMs: number): void {
    const now = Date.now();
    const at = Math.max(now, this.cursor);
    this.cursor = at + gapMs;

    const handle = setTimeout(() => {
      this.pending.delete(handle);
      run();
    }, at - now);
    this.pending.add(handle);
  }

  /** Drops everything still queued and resets the timeline - for unmount. */
  clear(): void {
    for (const handle of this.pending) clearTimeout(handle);
    this.pending.clear();
    this.cursor = 0;
  }
}
