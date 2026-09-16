// Shared screen-motion primitives. Used both by App.ts (cross-screen routing)
// and by LobbyScreen.ts, whose menu <-> waiting-for-opponent swap is internal
// to one Screen instance and so never passes through App.setScreen.
//
// Directions name the direction of MOVEMENT: "up" means the outgoing screen
// leaves through the top and the incoming one rises from below.

export type SlideDirection = "up" | "down" | "left" | "right";

/** Keep in sync with the animation durations in style.css. */
export const SLIDE_MS = 800;
/** Game-start / match-end fade-to-black, and the black hold between them. */
export const FADE_MS = 2000;
export const HOLD_MS = 1000;
/** Match-end's reveal is a quick hand-off to the logo intro, not a 2s fade. */
export const REVEAL_MS = 500;

const OUT_CLASS: Record<SlideDirection, string> = {
  up: "screen-slide-out-up",
  down: "screen-slide-out-down",
  left: "screen-slide-out-left",
  right: "screen-slide-out-right",
};

const IN_CLASS: Record<SlideDirection, string> = {
  up: "screen-slide-in-from-bottom",
  down: "screen-slide-in-from-top",
  left: "screen-slide-in-from-right",
  right: "screen-slide-in-from-left",
};

/**
 * Slides `outgoing` away and `incoming` in. `onOutgoingFinished` is what
 * removes/unmounts the old view, so it is guaranteed to run exactly once -
 * never twice (idempotent latch) and never zero times (timeout fallback for a
 * detached element or disabled animations).
 */
export function slideScreens(
  outgoing: HTMLElement,
  incoming: HTMLElement,
  direction: SlideDirection,
  onOutgoingFinished: () => void,
): void {
  const outClass = OUT_CLASS[direction];
  const inClass = IN_CLASS[direction];
  outgoing.classList.add(outClass);
  incoming.classList.add(inClass);

  whenAnimationEnds(outgoing, SLIDE_MS, () => {
    outgoing.classList.remove(outClass);
    onOutgoingFinished();
  });
  whenAnimationEnds(incoming, SLIDE_MS, () => incoming.classList.remove(inClass));
}

/**
 * Resolves when el's OWN animation ends. The `e.target === el` guard matters:
 * animationend bubbles, so an animated child (.card.intro-reveal) would
 * otherwise end the parent's transition early.
 */
function whenAnimationEnds(el: HTMLElement, ms: number, done: () => void): void {
  let finished = false;
  let timer = 0;
  const finish = () => {
    if (finished) return;
    finished = true;
    el.removeEventListener("animationend", onEnd);
    window.clearTimeout(timer);
    done();
  };
  const onEnd = (e: AnimationEvent) => {
    if (e.target === el) finish();
  };
  el.addEventListener("animationend", onEnd);
  timer = window.setTimeout(finish, ms + 150);
}

/**
 * A full-viewport black sheet, on document.body so it also covers the
 * tooltip (z-index 1000, also on body). It deliberately accepts pointer
 * events: it is the input lock for the whole fade sequence.
 */
export function createFadeOverlay(): HTMLDivElement {
  const el = document.createElement("div");
  el.className = "fade-overlay";
  document.body.appendChild(el);
  return el;
}

/** Runs one overlay animation class to completion. */
export function runOverlayFade(el: HTMLElement, cls: string, ms: number): Promise<void> {
  return new Promise((resolve) => {
    el.classList.add(cls);
    whenAnimationEnds(el, ms, resolve);
  });
}

export function delay(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

/** Two rAFs: the first schedules, the second lands after a paint has happened. */
export function nextFrames(count = 2): Promise<void> {
  return new Promise((resolve) => {
    const step = (remaining: number) => {
      if (remaining <= 0) {
        resolve();
        return;
      }
      requestAnimationFrame(() => step(remaining - 1));
    };
    step(count);
  });
}
