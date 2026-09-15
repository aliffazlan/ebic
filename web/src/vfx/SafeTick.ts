// A ticker callback anywhere in this codebase must never be able to throw
// uncaught: PixiJS's Ticker.update() has zero per-listener exception
// isolation (confirmed against node_modules/pixi.js/lib/ticker/Ticker.mjs),
// and Ticker's own _tick only reschedules the next requestAnimationFrame
// *after* update() returns without throwing - so one uncaught exception in
// any ticker callback, anywhere, permanently stalls the page-wide
// Ticker.shared for the rest of the session, including every future match's
// Board (all of this codebase's vfx animation explicitly drives Ticker.shared,
// not a per-Application ticker). The recurring trigger is a callback closing
// over a PixiJS object (a unit's token container, another vfx object) that
// some other code path has since called .destroy() on - reading a destroyed
// Container's `.position` throws, since destroy() nulls it out.
//
// Wrap every Ticker.shared.add() callback in this, with no exceptions.

import { Ticker } from "pixi.js";

/**
 * Wraps a ticker callback so a thrown exception removes it (logging why)
 * instead of escaping into Ticker.update(). Does not know how to clean up
 * whatever PixiJS objects the wrapped callback owns - callers whose callback
 * can throw from touching an externally-owned object should also guard that
 * specific access (e.g. checking `.destroyed`) so their own objects get
 * torn down properly too; this is the last-resort backstop, not a substitute
 * for that.
 */
export function safeTick(fn: () => void): () => void {
  const wrapped = () => {
    try {
      fn();
    } catch (err) {
      console.error("VFX ticker threw - removing it to protect Ticker.shared", err);
      Ticker.shared.remove(wrapped);
    }
  };
  return wrapped;
}
