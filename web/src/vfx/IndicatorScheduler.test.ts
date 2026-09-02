import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { IndicatorScheduler } from "./IndicatorScheduler";

describe("IndicatorScheduler", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("runs the first step immediately", () => {
    const scheduler = new IndicatorScheduler();
    const run = vi.fn();
    scheduler.enqueue(run, 500);
    vi.advanceTimersByTime(0);
    expect(run).toHaveBeenCalledTimes(1);
  });

  it("spaces consecutive steps by the requested gap", () => {
    const scheduler = new IndicatorScheduler();
    const first = vi.fn();
    const second = vi.fn();
    const third = vi.fn();
    scheduler.enqueue(first, 500);
    scheduler.enqueue(second, 500);
    scheduler.enqueue(third, 500);

    vi.advanceTimersByTime(0);
    expect([first, second, third].map((f) => f.mock.calls.length)).toEqual([1, 0, 0]);
    vi.advanceTimersByTime(500);
    expect([first, second, third].map((f) => f.mock.calls.length)).toEqual([1, 1, 0]);
    vi.advanceTimersByTime(500);
    expect([first, second, third].map((f) => f.mock.calls.length)).toEqual([1, 1, 1]);
  });

  it("lands work queued from a later message after work already in flight", () => {
    // This is the turn-start arrangement: the vfx message queues the damage
    // groups, then the state message right behind it queues the banner, and the
    // banner must not appear on top of the numbers.
    const scheduler = new IndicatorScheduler();
    const poison = vi.fn();
    const burn = vi.fn();
    const banner = vi.fn();
    scheduler.enqueue(poison, 500);
    scheduler.enqueue(burn, 500);
    scheduler.enqueue(banner, 0);

    vi.advanceTimersByTime(999);
    expect(banner).not.toHaveBeenCalled();
    vi.advanceTimersByTime(1);
    expect(banner).toHaveBeenCalledTimes(1);
  });

  it("runs immediately again once the timeline has drained", () => {
    const scheduler = new IndicatorScheduler();
    scheduler.enqueue(vi.fn(), 500);
    vi.advanceTimersByTime(5000);

    const later = vi.fn();
    scheduler.enqueue(later, 500);
    vi.advanceTimersByTime(0);
    expect(later).toHaveBeenCalledTimes(1);
  });

  it("cancels pending work and resets the timeline on clear", () => {
    const scheduler = new IndicatorScheduler();
    const cancelled = vi.fn();
    scheduler.enqueue(vi.fn(), 500);
    scheduler.enqueue(cancelled, 500);
    scheduler.clear();
    vi.advanceTimersByTime(5000);
    expect(cancelled).not.toHaveBeenCalled();

    // The cursor reset too, so the next step is immediate rather than waiting
    // out the cleared queue's reservations.
    const afterClear = vi.fn();
    scheduler.enqueue(afterClear, 500);
    vi.advanceTimersByTime(0);
    expect(afterClear).toHaveBeenCalledTimes(1);
  });
});
