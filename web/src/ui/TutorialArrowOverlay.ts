// Bouncing tutorial pointer arrows for DOM targets (draft cards, Hud buttons) - the
// counterpart to Board.setObjectiveArrows, which handles units/tiles on the Pixi
// canvas instead. Lives on the canvas host, same reasoning as ObjectiveBanner/
// TurnBanner: Hud.render() wipes its subtree with innerHTML = "" on every store
// update, so any DOM arrow must re-query its target selector every frame rather
// than caching the element it once found.

export interface DomArrowTarget {
  selector: string;
  direction: "down" | "side";
}

// Buffer between an arrow's tip and the target element's edge it points at.
const GAP = 8;
// A CSS border-triangle's point sits at a fixed offset from the element's own
// top-left corner - these must match .tutorial-arrow--down/--side in style.css.
const DOWN_HALF_WIDTH = 15;
const DOWN_HEIGHT = 24;
const SIDE_WIDTH = 24;
const SIDE_HALF_HEIGHT = 15;

export class TutorialArrowOverlay {
  private readonly host: HTMLElement;
  private targets: DomArrowTarget[] = [];
  private els = new Map<string, HTMLDivElement>();
  private raf: number | null = null;
  private readonly tick = (): void => {
    const hostRect = this.host.getBoundingClientRect();
    for (const target of this.targets) {
      const el = this.els.get(target.selector);
      const found = document.querySelector(target.selector) as HTMLElement | null;
      if (!el) continue;
      if (!found) {
        el.style.display = "none";
        continue;
      }
      const rect = found.getBoundingClientRect();
      el.style.display = "block";
      if (target.direction === "down") {
        // Points down at the target's top edge (e.g. a draft card), not its center.
        const tipX = rect.left - hostRect.left + rect.width / 2;
        const tipY = rect.top - hostRect.top - GAP;
        el.style.left = `${tipX - DOWN_HALF_WIDTH}px`;
        el.style.top = `${tipY - DOWN_HEIGHT}px`;
      } else {
        // Points right at the target's left edge (e.g. a button), sitting beside it.
        const tipX = rect.left - hostRect.left - GAP;
        const tipY = rect.top - hostRect.top + rect.height / 2;
        el.style.left = `${tipX - SIDE_WIDTH}px`;
        el.style.top = `${tipY - SIDE_HALF_HEIGHT}px`;
      }
    }
    this.raf = requestAnimationFrame(this.tick);
  };

  constructor(host: HTMLElement) {
    this.host = host;
  }

  setTargets(targets: DomArrowTarget[]): void {
    this.targets = targets;
    this.syncElements();
    if (targets.length > 0 && this.raf === null) this.raf = requestAnimationFrame(this.tick);
    if (targets.length === 0 && this.raf !== null) {
      cancelAnimationFrame(this.raf);
      this.raf = null;
    }
  }

  private syncElements(): void {
    const wanted = new Set(this.targets.map((t) => t.selector));
    for (const [selector, el] of this.els) {
      if (!wanted.has(selector)) {
        el.remove();
        this.els.delete(selector);
      }
    }
    for (const target of this.targets) {
      let el = this.els.get(target.selector);
      if (!el) {
        el = document.createElement("div");
        this.host.appendChild(el);
        this.els.set(target.selector, el);
      }
      el.className = `tutorial-arrow tutorial-arrow--${target.direction}`;
    }
  }

  destroy(): void {
    if (this.raf !== null) cancelAnimationFrame(this.raf);
    this.raf = null;
    for (const el of this.els.values()) el.remove();
    this.els.clear();
  }
}
