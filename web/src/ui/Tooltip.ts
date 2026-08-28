import type { AbilityPreviewSnapshot, AbilitySnapshot, EffectSnapshot } from "../types/contract";

// Which key expands a tooltip from the one-line overview to the full rules. Matched on
// `KeyboardEvent.code`, so this is a physical key, not a character - change these two
// constants together and nothing else needs touching.
const EXPAND_KEY = "AltLeft";
const EXPAND_KEY_LABEL = "Left Alt";

/** Everything a tooltip needs about an ability, from either snapshot shape. */
export interface AbilityTooltipContent {
  name: string;
  description: string;
  details: string[];
  stats: Record<string, number>;
  passive: boolean;
  // Precomputed by the caller: the two snapshot shapes name the cooldown field
  // differently (`cooldown` vs `maxCooldown`) and only one carries live state.
  cooldownLabel: string;
  // Only ever set by an in-match button. Replaces the native `title` such a button would
  // otherwise need, so there is one tooltip competing for the hover instead of two.
  disabledReason?: string | null;
}

export function abilityTooltip(ability: AbilitySnapshot): AbilityTooltipContent {
  return {
    name: ability.name,
    description: ability.description,
    details: ability.details ?? [],
    stats: ability.stats ?? {},
    passive: ability.passive,
    cooldownLabel: cooldownLabel(ability.maxCooldown),
  };
}

export function abilityPreviewTooltip(ability: AbilityPreviewSnapshot): AbilityTooltipContent {
  return {
    name: ability.name,
    description: ability.description,
    details: ability.details ?? [],
    stats: ability.stats ?? {},
    passive: ability.passive,
    cooldownLabel: cooldownLabel(ability.cooldown),
  };
}

export function cooldownLabel(cooldown: number): string {
  if (cooldown <= 0) return "No cooldown";
  return `Cooldown: ${cooldown} turn${cooldown === 1 ? "" : "s"}`;
}

/**
 * The one hover tooltip shared by every hoverable thing on a screen - effect chips,
 * ability buttons, draft and codex ability chips. Only one can ever be visible at a time,
 * so one element is all that is needed.
 *
 * It lives on document.body rather than inside whatever rendered it, because the HUD
 * rebuilds its entire subtree on every store update and would otherwise tear a tooltip
 * down mid-hover.
 *
 * Holding EXPAND_KEY swaps the short description for the full rules without moving the
 * mouse, which means the tooltip has to be able to redraw itself from nothing but what it
 * last showed - hence the remembered builder and pointer position.
 */
export class Tooltip {
  private el: HTMLDivElement;
  private expanded = false;
  private activeBuild: ((expanded: boolean) => void) | null = null;
  private lastPointer: { x: number; y: number } | null = null;
  private onKeyDown: (event: KeyboardEvent) => void;
  private onKeyUp: (event: KeyboardEvent) => void;

  constructor() {
    this.el = document.createElement("div");
    this.el.className = "hover-tooltip";
    document.body.appendChild(this.el);

    this.onKeyDown = (event) => this.setExpanded(event, true);
    this.onKeyUp = (event) => this.setExpanded(event, false);
    window.addEventListener("keydown", this.onKeyDown);
    window.addEventListener("keyup", this.onKeyUp);
  }

  destroy(): void {
    window.removeEventListener("keydown", this.onKeyDown);
    window.removeEventListener("keyup", this.onKeyUp);
    this.el.remove();
  }

  /** Wires this tooltip onto any element - shows on enter, tracks the cursor, hides on leave. */
  attach(el: HTMLElement, build: (expanded: boolean) => void): void {
    el.addEventListener("mouseenter", (e) => {
      this.activeBuild = build;
      build(this.expanded);
      this.el.classList.add("visible");
      // After build, not before: place() measures the element, and measuring it empty
      // would leave a tall tooltip hanging off the bottom of the window.
      this.position(e as MouseEvent);
    });
    el.addEventListener("mousemove", (e) => this.position(e as MouseEvent));
    el.addEventListener("mouseleave", () => this.hide());
  }

  attachAbility(el: HTMLElement, content: AbilityTooltipContent): void {
    this.attach(el, (expanded) => this.renderAbility(content, expanded));
  }

  attachEffect(el: HTMLElement, effect: EffectSnapshot): void {
    this.attach(el, () => this.renderEffect(effect));
  }

  hide(): void {
    this.activeBuild = null;
    this.el.classList.remove("visible");
  }

  private setExpanded(event: KeyboardEvent, expanded: boolean): void {
    if (event.code !== EXPAND_KEY || this.expanded === expanded) {
      return;
    }
    this.expanded = expanded;
    if (!this.activeBuild) {
      return;
    }
    // Alt on its own focuses the menu bar in some browsers, which would steal the hover.
    event.preventDefault();
    this.activeBuild(this.expanded);
    if (this.lastPointer) {
      this.place(this.lastPointer.x, this.lastPointer.y);
    }
  }

  private renderAbility(content: AbilityTooltipContent, expanded: boolean): void {
    this.el.innerHTML = "";
    this.el.classList.toggle("expanded", expanded);

    this.el.appendChild(line("hover-tooltip-title", content.name));
    this.el.appendChild(line("", content.description));
    this.el.appendChild(line("hover-tooltip-meta", content.passive ? "Passive" : content.cooldownLabel));

    const hasMore = content.details.length > 0 || Object.keys(content.stats).length > 0;
    if (expanded && hasMore) {
      if (content.details.length > 0) {
        const list = document.createElement("ul");
        list.className = "hover-tooltip-details";
        for (const detail of content.details) {
          const item = document.createElement("li");
          item.textContent = detail;
          list.appendChild(item);
        }
        this.el.appendChild(list);
      }
      const statKeys = Object.keys(content.stats).sort();
      if (statKeys.length > 0) {
        const grid = document.createElement("div");
        grid.className = "hover-tooltip-stats";
        for (const key of statKeys) {
          grid.appendChild(line("hover-tooltip-stat-key", prettifyStatKey(key)));
          grid.appendChild(line("hover-tooltip-stat-value", formatStatValue(content.stats[key])));
        }
        this.el.appendChild(grid);
      }
    } else if (hasMore) {
      this.el.appendChild(line("hover-tooltip-hint", `Hold ${EXPAND_KEY_LABEL} for details`));
    }

    if (content.disabledReason) {
      this.el.appendChild(line("hover-tooltip-meta hover-tooltip-reason", content.disabledReason));
    }
  }

  private renderEffect(effect: EffectSnapshot): void {
    this.el.innerHTML = "";
    this.el.classList.remove("expanded");

    this.el.appendChild(line("hover-tooltip-title", effect.name));
    this.el.appendChild(line("", effect.description));
    this.el.appendChild(line("hover-tooltip-meta", effect.permanent
      ? "Permanent"
      : `${effect.remainingTurns} turn${effect.remainingTurns === 1 ? "" : "s"} remaining`));

    if (effect.extraInfo) {
      this.el.appendChild(line("hover-tooltip-meta", effect.extraInfo));
    }
    if (effect.statusFlags.length > 0) {
      this.el.appendChild(line("hover-tooltip-meta", `Flags: ${effect.statusFlags.join(", ")}`));
    }
  }

  private position(event: MouseEvent): void {
    this.lastPointer = { x: event.clientX, y: event.clientY };
    this.place(event.clientX, event.clientY);
  }

  /**
   * Down-and-right of the cursor by default, flipped to the other side of it when that
   * would run off the viewport. The expanded panel is several times taller than the
   * collapsed one, so near the bottom of a codex page the default placement would put most
   * of the rules off-screen - which is the half the player pressed a key to read.
   */
  private place(x: number, y: number): void {
    const offset = 14;
    const margin = 8;
    const { width, height } = this.el.getBoundingClientRect();

    let left = x + offset;
    if (left + width + margin > window.innerWidth) {
      left = Math.max(margin, x - offset - width);
    }

    let top = y + offset;
    if (top + height + margin > window.innerHeight) {
      top = Math.max(margin, y - offset - height);
    }

    this.el.style.left = `${left}px`;
    this.el.style.top = `${top}px`;
  }
}

function line(className: string, text: string): HTMLElement {
  const el = document.createElement("div");
  if (className) el.className = className;
  el.textContent = text;
  return el;
}

/** `cast_range` -> `Cast range`. The keys are hand-written design ids, not display text. */
function prettifyStatKey(key: string): string {
  const words = key.replace(/_/g, " ").trim();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

/** Percent-valued stats are stored as fractions server-side (0.2 = 20%), so show both readably. */
function formatStatValue(value: number): string {
  if (Number.isInteger(value)) return String(value);
  return value > 0 && value < 1 ? `${Math.round(value * 1000) / 10}%` : String(value);
}
