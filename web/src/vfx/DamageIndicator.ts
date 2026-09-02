// A floating damage/heal number: pops out from the unit's centre, drifts up,
// fades out.
//
// Same injectable (parent, ticker, opts) shape as spawnParticleBurst, but this
// one *returns* its tick so the caller can register it for teardown -
// spawnParticleBurst doesn't, and its dots keep ticking against destroyed
// Graphics after a Board is torn down. Board tracks these in
// activeIndicatorTicks the way it already tracks strobe and move tweens.

import { Container, Graphics, GraphicsContext, Text, Ticker } from "pixi.js";
import {
  colorForKind,
  ICON_COLOR_PLACEHOLDER,
  ICON_SVG,
  type IndicatorKind,
} from "./VfxIndicators";
import { cssHex } from "../ui/Colors";

// Frame counts assume Ticker.shared's default ~60fps, matching the
// frame-counting style used by ParticleBurst and Board's own animations.
const LIFE_FRAMES = 144; // ~2.4s
const POP_FRAMES = 8; // ~130ms of scale-in
const POP_OVERSHOOT = 1.15;
const POP_FROM = 0.4;
/** How far the number drifts up over its life, in board pixels (HEX_SIZE is 34). */
const RISE_PX = 12;
/** Fraction of the life spent at full opacity before the fade starts. */
const FADE_AFTER = 0.55;

const FONT_SIZE = 22;
// Pixi Text doesn't inherit CSS, so the stack from style.css is repeated here
// rather than falling back to a bare "sans-serif".
const FONT_FAMILY = 'system-ui, -apple-system, "Segoe UI", sans-serif';

// The board is dark but a number can land on top of a bright unit token, so
// every glyph gets a dark outline rather than relying on the backdrop.
const OUTLINE_COLOR = 0x0f172a;
const OUTLINE_WIDTH = 4;

/** Counter-scaling bounds - see getBoardScale below. */
const MIN_COUNTER_SCALE = 0.5;
const MAX_COUNTER_SCALE = 2;

/**
 * Icon height, a little under the font size so the glyph reads as sitting on the
 * same line as the digits rather than towering over them. Width follows from the
 * art's own aspect ratio.
 */
const ICON_HEIGHT = 17;
/** Space between the icon and the first digit. */
const ICON_GAP = 4;
/**
 * Widest an icon may render, as a multiple of its height. Pixi's SVG parser has
 * sharp edges - its <polygon> reader silently truncates decimals, which turned
 * one icon into a 55:1 smear across the whole board - so a malformed icon should
 * come out wrong inside its own slot rather than covering the game.
 */
const MAX_ICON_ASPECT = 2;

/**
 * One parsed GraphicsContext per kind, built on first use and shared by every
 * Graphics that draws that icon - parsing the SVG on each spawn would be
 * wasteful, and the colour is fixed per kind so one context per kind is exactly
 * the right granularity.
 *
 * Anything drawing from these must destroy without `{ context: true }`, or the
 * shared context goes with it and every later indicator of that kind is blank.
 */
const iconContexts = new Map<IndicatorKind, GraphicsContext>();

function iconContextFor(kind: IndicatorKind): GraphicsContext {
  const cached = iconContexts.get(kind);
  if (cached) return cached;
  // Pixi has no notion of CSS currentColor, so the placeholder is substituted
  // for this kind's real colour before the SVG is ever parsed.
  const context = new GraphicsContext().svg(
    ICON_SVG[kind].replaceAll(ICON_COLOR_PLACEHOLDER, cssHex(colorForKind(kind))),
  );
  iconContexts.set(kind, context);
  return context;
}

/**
 * The icon, normalised to ICON_HEIGHT and centred on its own origin, plus the
 * width that came out - the art is not square (miss is wide and squat, burn is
 * tall and narrow), so the caller has to lay the text out against the real
 * width rather than assuming the height.
 *
 * Size comes from the parsed bounds rather than a per-icon constant because the
 * source art doesn't share a viewBox (16 for some, 24 for others) and Pixi's SVG
 * parser works in viewBox units - so measuring is what makes them all come out
 * the same height on screen, whatever a future icon is authored at.
 */
function buildIcon(kind: IndicatorKind): { view: Graphics; width: number } {
  const view = new Graphics({ context: iconContextFor(kind) });
  const bounds = view.getLocalBounds();
  const scale = bounds.height > 0 ? ICON_HEIGHT / bounds.height : 1;
  view.scale.set(scale);
  view.pivot.set(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
  return { view, width: Math.min(bounds.width * scale, ICON_HEIGHT * MAX_ICON_ASPECT) };
}

export interface IndicatorOptions {
  x: number;
  y: number;
  text: string;
  color: number;
  /** Which icon to draw to the left of the text. */
  kind: IndicatorKind;
  /**
   * Nth indicator on this unit in the same batch. Each one starts a little
   * higher than the last so two hits on one unit don't print on top of
   * each other.
   */
  stackIndex?: number;
  /**
   * Current board zoom. Indicators live inside the camera-transformed board
   * container, so without counter-scaling they'd shrink to nothing when the
   * user zooms out - which is exactly when they most need to be legible.
   */
  getBoardScale?: () => number;
  /**
   * Called once the animation has finished and removed itself from the ticker,
   * so a caller tracking the tick for teardown can stop tracking it. Without
   * this the caller's set would grow for the whole match.
   */
  onComplete?: () => void;
}

/**
 * Spawns the indicator and starts it animating. Returns the ticker callback so
 * the caller can cancel it on teardown; it removes itself from the ticker
 * normally when the animation finishes.
 */
export function spawnDamageIndicator(
  parent: Container,
  ticker: Ticker,
  opts: IndicatorOptions,
): () => void {
  const { x, y, text, color, kind, stackIndex = 0, getBoardScale, onComplete } = opts;

  const label = new Text({
    text,
    style: {
      fill: color,
      fontSize: FONT_SIZE,
      fontWeight: "bold",
      fontFamily: FONT_FAMILY,
      stroke: { color: OUTLINE_COLOR, width: OUTLINE_WIDTH, join: "round" },
    },
  });
  // Left-aligned and vertically centred: the icon sits to its left, and the pair
  // is centred as a group below rather than each half centring itself.
  label.anchor.set(0, 0.5);

  const { view: icon, width: iconWidth } = buildIcon(kind);
  const group = new Container();
  icon.position.set(iconWidth / 2, 0);
  label.position.set(iconWidth + ICON_GAP, 0);
  group.addChild(icon, label);
  // Centre the icon-plus-number over the unit, the way the bare number used to
  // centre itself. Only x needs it - both children are already centred on y.
  group.pivot.set((iconWidth + ICON_GAP + label.width) / 2, 0);

  const startY = y - stackIndex * FONT_SIZE;
  group.position.set(x, startY);
  parent.addChild(group);

  let elapsed = 0;
  const tick = () => {
    elapsed += 1;
    const t = Math.min(1, elapsed / LIFE_FRAMES);

    // Ease-out cubic, the same easing animateUnitMove uses.
    const eased = 1 - Math.pow(1 - t, 3);
    group.position.y = startY - RISE_PX * eased;

    group.alpha = t < FADE_AFTER ? 1 : 1 - (t - FADE_AFTER) / (1 - FADE_AFTER);

    const counterScale = getBoardScale ? clamp(1 / getBoardScale(), MIN_COUNTER_SCALE, MAX_COUNTER_SCALE) : 1;
    group.scale.set(popScale(elapsed) * counterScale);

    if (elapsed >= LIFE_FRAMES) {
      ticker.remove(tick);
      // Deliberately not { context: true }: the icon's GraphicsContext is shared
      // with every other indicator of this kind and must outlive this one.
      group.destroy({ children: true });
      onComplete?.();
    }
  };
  ticker.add(tick);
  return tick;
}

/** Scale-in with a slight overshoot, so the number reads as popping out of the unit. */
function popScale(elapsed: number): number {
  if (elapsed >= POP_FRAMES) return 1;
  const t = elapsed / POP_FRAMES;
  // Up to the overshoot by the halfway point, then settle back to 1.
  return t < 0.5
    ? POP_FROM + (POP_OVERSHOOT - POP_FROM) * (t / 0.5)
    : POP_OVERSHOOT + (1 - POP_OVERSHOOT) * ((t - 0.5) / 0.5);
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
