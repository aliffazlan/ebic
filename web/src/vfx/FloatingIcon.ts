// A one-shot floating icon: pops in at a unit's portrait, rises, fades out,
// self-destroys. Same rise/fade/pop shape as DamageIndicator's floating number
// (spawnDamageIndicator), stripped of the digit label - used for Eureka's bulb
// and Reload's gear, both "a Maxwell gadget procs" cast flashes, per
// temp/abilities.txt ("floats up... then fades out. total animation time ~2 seconds").
//
// No test file - same category as DamageIndicator.ts/ParticleBurst.ts, verified
// visually rather than under vitest.

import { Container, Graphics, GraphicsContext, Ticker } from "pixi.js";
import { cssHex } from "../ui/Colors";
import { safeTick } from "./SafeTick";
import bulbSvg from "./icons/bulb.svg?raw";
import gearSvg from "./icons/gear.svg?raw";

export type FloatingIconKind = "bulb" | "gear";

const ICON_COLOR_PLACEHOLDER = "currentColor";
const ICON_SOURCE: Record<FloatingIconKind, string> = { bulb: bulbSvg, gear: gearSvg };
const ICON_HEIGHT_PX = 26;

/** One parsed GraphicsContext per (kind, colour), same caching idiom as every other recoloured icon in this codebase. */
const iconContexts = new Map<string, GraphicsContext>();

function iconContextFor(kind: FloatingIconKind, color: number): GraphicsContext {
  const key = `${kind}:${cssHex(color)}`;
  const cached = iconContexts.get(key);
  if (cached) return cached;
  const context = new GraphicsContext().svg(ICON_SOURCE[kind].replaceAll(ICON_COLOR_PLACEHOLDER, cssHex(color)));
  iconContexts.set(key, context);
  return context;
}

// Frame counts assume Ticker.shared's default ~60fps, matching the rest of the board's animations.
const LIFE_FRAMES = 120; // ~2s, per temp/abilities.txt
const POP_FRAMES = 8;
const POP_OVERSHOOT = 1.15;
const POP_FROM = 0.4;
// Floats well above the portrait, not just a small drift - bigger than DamageIndicator's
// own 12px rise, since this is meant to read as leaving the unit entirely.
const RISE_PX = 40;
const FADE_AFTER = 0.5;
const MIN_COUNTER_SCALE = 0.5;
const MAX_COUNTER_SCALE = 2;

export interface FloatingIconOptions {
  x: number;
  y: number;
  kind: FloatingIconKind;
  color: number;
  /** Current board zoom, for the same counter-scaling DamageIndicator uses so this doesn't shrink to nothing when zoomed out. */
  getBoardScale?: () => number;
  onComplete?: () => void;
}

/** Spawns the icon and starts it animating. Returns the ticker callback so the caller can cancel it on teardown. */
export function spawnFloatingIcon(parent: Container, ticker: Ticker, opts: FloatingIconOptions): () => void {
  const { x, y, kind, color, getBoardScale, onComplete } = opts;
  const view = new Graphics({ context: iconContextFor(kind, color) });
  const bounds = view.getLocalBounds();
  const baseScale = bounds.height > 0 ? ICON_HEIGHT_PX / bounds.height : 1;
  view.pivot.set(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
  view.position.set(x, y);
  parent.addChild(view);

  let elapsed = 0;
  const tick = safeTick(() => {
    elapsed += 1;
    const t = Math.min(1, elapsed / LIFE_FRAMES);
    const eased = 1 - Math.pow(1 - t, 3);
    view.position.y = y - RISE_PX * eased;
    view.alpha = t < FADE_AFTER ? 1 : 1 - (t - FADE_AFTER) / (1 - FADE_AFTER);
    const counterScale = getBoardScale ? clamp(1 / getBoardScale(), MIN_COUNTER_SCALE, MAX_COUNTER_SCALE) : 1;
    view.scale.set(baseScale * popScale(elapsed) * counterScale);
    if (elapsed >= LIFE_FRAMES) {
      ticker.remove(tick);
      // Deliberately not { context: true } - the icon's GraphicsContext is shared with every
      // other floating icon of this kind/colour and must outlive this one.
      view.destroy();
      onComplete?.();
    }
  });
  ticker.add(tick);
  return tick;
}

/** Scale-in with a slight overshoot, same curve as DamageIndicator's popScale. */
function popScale(elapsed: number): number {
  if (elapsed >= POP_FRAMES) return 1;
  const t = elapsed / POP_FRAMES;
  return t < 0.5
    ? POP_FROM + (POP_OVERSHOOT - POP_FROM) * (t / 0.5)
    : POP_OVERSHOOT + (1 - POP_OVERSHOOT) * ((t - 0.5) / 0.5);
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
