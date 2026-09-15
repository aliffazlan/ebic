// PixiJS rendering for attack animations: slash/arrow (recoloured SVG art),
// projectile (a plain travelling orb), lightning (a procedural jittering
// bolt), and beam (a static pulsing line). No test file - this is the same
// category as Board.ts/DamageIndicator.ts/ParticleBurst.ts, verified visually
// rather than under vitest.
//
// Frame counts assume Ticker.shared's default ~60fps, matching the
// frame-counting convention the rest of the board's animations already use
// (animateUnitMove, spawnDamageIndicator, spawnParticleBurst).

import { Container, Graphics, GraphicsContext, Ticker } from "pixi.js";
import type { PixelCoord } from "../hex/HexMath";
import { cssHex } from "../ui/Colors";
import { strokeDurationMs, type AttackAnimationSpec, type AttackAnimationStroke } from "./AttackAnimations";
import { spawnParticleBurst } from "./ParticleBurst";
import { safeTick } from "./SafeTick";
import slashSvg from "./icons/slash.svg?raw";
import arrowSvg from "./icons/arrow.svg?raw";

const FPS = 60;
function msToFrames(ms: number): number {
  return Math.max(1, Math.round((ms / 1000) * FPS));
}

const ICON_COLOR_PLACEHOLDER = "currentColor";
const ICON_SOURCE: Record<"slash" | "arrow", string> = { slash: slashSvg, arrow: arrowSvg };
const SLASH_ICON_HEIGHT_PX = 40;
const ARROW_ICON_HEIGHT_PX = 34;

/**
 * One parsed GraphicsContext per (kind, colour) pair. Colour varies per unit
 * here (unlike DamageIndicator's fixed 6-kind cache), but the key space is
 * still bounded by the finite unit roster - at most ~2 kinds x ~15 distinct
 * colours - so this never grows unbounded and is never evicted.
 */
const strokeIconContexts = new Map<string, GraphicsContext>();

function strokeIconContextFor(kind: "slash" | "arrow", color: number): GraphicsContext {
  const key = `${kind}:${cssHex(color)}`;
  const cached = strokeIconContexts.get(key);
  if (cached) return cached;
  const context = new GraphicsContext().svg(ICON_SOURCE[kind].replaceAll(ICON_COLOR_PLACEHOLDER, cssHex(color)));
  strokeIconContexts.set(key, context);
  return context;
}

function buildStrokeIcon(kind: "slash" | "arrow", color: number, heightPx: number): Graphics {
  const view = new Graphics({ context: strokeIconContextFor(kind, color) });
  const bounds = view.getLocalBounds();
  const scale = bounds.height > 0 ? heightPx / bounds.height : 1;
  view.scale.set(scale);
  view.pivot.set(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
  return view;
}

/** Ease-out cubic - the same curve animateUnitMove and spawnDamageIndicator use. */
function easeOutCubic(t: number): number {
  return 1 - Math.pow(1 - t, 3);
}

function lerp(from: number, to: number, t: number): number {
  return from + (to - from) * t;
}

/**
 * Plays one attack event's full animation (every stroke in `spec`), calling
 * `onComplete` only once every stroke has finished - so a two-stroke spec
 * (Wei, Evayne) reports done when its *second* slash lands, not its first.
 *
 * Returns one tick callback per stroke, synchronously, so the caller can
 * register them for teardown before a single frame has run.
 */
export function spawnAttackAnimation(
  parent: Container,
  ticker: Ticker,
  from: PixelCoord,
  to: PixelCoord,
  spec: AttackAnimationSpec,
  onComplete: () => void,
): (() => void)[] {
  if (spec.strokes.length === 0) {
    onComplete();
    return [];
  }
  let remaining = spec.strokes.length;
  const onStrokeDone = () => {
    remaining -= 1;
    if (remaining === 0) onComplete();
  };
  return spec.strokes.map((stroke) => spawnStroke(parent, ticker, from, to, stroke, onStrokeDone));
}

/**
 * One stroke, one stable tick closure for its whole life - never swapped for
 * a different closure mid-flight, so Board's teardown set always holds a
 * live, still-correct reference. Before `stroke.delayMs` has elapsed nothing
 * is drawn at all (the view is created lazily on the first post-delay
 * frame), so a delayed second stroke (Wei, Evayne) has zero visual presence
 * until its turn starts.
 */
function spawnStroke(
  parent: Container,
  ticker: Ticker,
  from: PixelCoord,
  to: PixelCoord,
  stroke: AttackAnimationStroke,
  onDone: () => void,
): () => void {
  const delayFrames = msToFrames(stroke.delayMs ?? 0);
  const durationFrames = msToFrames(strokeDurationMs(stroke.kind));
  let elapsed = 0;
  let view: Graphics | null = null;
  let particleCursor = 0;

  const tick = safeTick(() => {
    elapsed += 1;
    if (elapsed <= delayFrames) return;
    const localElapsed = elapsed - delayFrames;
    const t = Math.min(1, localElapsed / durationFrames);

    if (!view) {
      view = createStrokeView(stroke);
      parent.addChild(view);
    }

    switch (stroke.kind) {
      case "slash":
        updateSlash(view, from, to, t, stroke);
        break;
      case "arrow":
        updateArrow(view, from, to, t, stroke);
        break;
      case "projectile":
        particleCursor = updateProjectile(view, from, to, t, stroke, parent, ticker, particleCursor, localElapsed);
        break;
      case "growing-projectile":
        updateGrowingProjectile(view, from, to, t, parent, ticker);
        break;
      case "lightning":
        updateLightning(view, from, to, localElapsed, t, stroke.width ?? LIGHTNING_WIDTH_PX);
        break;
      case "beam":
        updateBeam(view, from, to, localElapsed, t, stroke.toColor);
        break;
    }

    if (t >= 1) {
      ticker.remove(tick);
      view.destroy();
      onDone();
    }
  });
  ticker.add(tick);
  return tick;
}

function createStrokeView(stroke: AttackAnimationStroke): Graphics {
  switch (stroke.kind) {
    case "slash": {
      const view = buildStrokeIcon("slash", stroke.color, SLASH_ICON_HEIGHT_PX);
      view.scale.set(view.scale.x * (stroke.scale ?? 1));
      return view;
    }
    case "arrow": {
      const view = buildStrokeIcon("arrow", stroke.color, ARROW_ICON_HEIGHT_PX);
      view.scale.set(view.scale.x * (stroke.scale ?? 1));
      return view;
    }
    case "projectile":
      return new Graphics().circle(0, 0, PROJECTILE_RADIUS_PX).fill({ color: stroke.color });
    case "growing-projectile": {
      const view = new Graphics();
      strokeColors.set(view, stroke.color);
      return view;
    }
    case "lightning":
    case "beam": {
      const view = new Graphics();
      // Stashed here since lightning/beam redraw their geometry every frame
      // (clear() + a fresh path) and need the colour again each time, but
      // Graphics itself has no "remembered fill colour" accessor.
      strokeColors.set(view, stroke.color);
      return view;
    }
  }
}

// --- slash: SVG art, swipes through a small arc, pops in, fades out late ---

const SLASH_FADE_START = 0.65; // fades over the final 35% of its travel
const SLASH_SWEEP_RAD = 0.35;
const SLASH_POP_FRACTION = 0.25; // scale pops in over the first 25%

function updateSlash(view: Graphics, from: PixelCoord, to: PixelCoord, t: number, stroke: AttackAnimationStroke): void {
  const eased = easeOutCubic(t);
  view.position.set(lerp(from.x, to.x, eased), lerp(from.y, to.y, eased));

  const baseAngle = Math.atan2(to.y - from.y, to.x - from.x);
  view.rotation = baseAngle - SLASH_SWEEP_RAD + SLASH_SWEEP_RAD * 2 * eased;

  view.scale.set(baseIconScale(view, "slash") * (stroke.scale ?? 1) * popScale(t));

  view.alpha = t < SLASH_FADE_START ? 1 : 1 - (t - SLASH_FADE_START) / (1 - SLASH_FADE_START);
}

function popScale(t: number): number {
  if (t >= SLASH_POP_FRACTION) return 1;
  const local = t / SLASH_POP_FRACTION;
  const overshoot = 1.15;
  const from = 0.4;
  return local < 0.5 ? from + (overshoot - from) * (local / 0.5) : overshoot + (1 - overshoot) * ((local - 0.5) / 0.5);
}

/** The icon's own fitted scale (height-to-target-px), before any pop/stroke scale is layered on. */
const baseIconScaleCache = new WeakMap<Graphics, number>();
function baseIconScale(view: Graphics, kind: "slash" | "arrow"): number {
  const cached = baseIconScaleCache.get(view);
  if (cached !== undefined) return cached;
  const bounds = view.getLocalBounds();
  const heightPx = kind === "slash" ? SLASH_ICON_HEIGHT_PX : ARROW_ICON_HEIGHT_PX;
  const scale = bounds.height > 0 ? heightPx / bounds.height : 1;
  baseIconScaleCache.set(view, scale);
  return scale;
}

// --- arrow: SVG art, faces travel direction, no swipe ---

const ARROW_FADE_START = 0.8; // fades over the final 20%
/**
 * The art's own resting orientation isn't obvious from its path data (the
 * source SVG bakes in a rotate(-45) group) - tune this by eye in the dev
 * harness (FixtureScreen, "v" key, Artemis's arrow) if it points the wrong
 * way, then hardcode the corrected value here.
 */
const ARROW_ART_BASE_ANGLE = 0;

function updateArrow(view: Graphics, from: PixelCoord, to: PixelCoord, t: number, stroke: AttackAnimationStroke): void {
  const eased = easeOutCubic(t);
  view.position.set(lerp(from.x, to.x, eased), lerp(from.y, to.y, eased));
  view.rotation = Math.atan2(to.y - from.y, to.x - from.x) - ARROW_ART_BASE_ANGLE;
  view.scale.set(baseIconScale(view, "arrow") * (stroke.scale ?? 1));
  view.alpha = t < ARROW_FADE_START ? 1 : 1 - (t - ARROW_FADE_START) / (1 - ARROW_FADE_START);
}

// --- projectile: a plain orb, optional trailing sparkle (Ember) ---

const PROJECTILE_RADIUS_PX = 6;
const PROJECTILE_FADE_START = 0.8;
const PARTICLE_INTERVAL_FRAMES = 3;
const TRAIL_PARTICLE_RADIUS_PX = 2;
const TRAIL_PARTICLE_LIFE_FRAMES = 10;

function updateProjectile(
  view: Graphics,
  from: PixelCoord,
  to: PixelCoord,
  t: number,
  stroke: AttackAnimationStroke,
  parent: Container,
  ticker: Ticker,
  particleCursor: number,
  localElapsed: number,
): number {
  const eased = easeOutCubic(t);
  const x = lerp(from.x, to.x, eased);
  const y = lerp(from.y, to.y, eased);
  view.position.set(x, y);
  view.alpha = t < PROJECTILE_FADE_START ? 1 : 1 - (t - PROJECTILE_FADE_START) / (1 - PROJECTILE_FADE_START);

  let nextCursor = particleCursor;
  if (stroke.particles && localElapsed - particleCursor >= PARTICLE_INTERVAL_FRAMES) {
    nextCursor = localElapsed;
    spawnTrailParticle(parent, ticker, x, y, stroke.color);
  }
  return nextCursor;
}

/**
 * A single fading trail dot. Self-contained and self-cleaning - not part of
 * the stroke's own tracked tick, so Board's teardown set never needs to know
 * about it. Wrapped in safeTick like every other ticker in this codebase, so
 * an untracked one outliving a destroyed parent/Board can't take down
 * Ticker.shared - see SafeTick.ts.
 */
function spawnTrailParticle(parent: Container, ticker: Ticker, x: number, y: number, color: number): void {
  const dot = new Graphics().circle(0, 0, TRAIL_PARTICLE_RADIUS_PX).fill({ color });
  dot.position.set(x, y);
  parent.addChild(dot);
  let elapsed = 0;
  const tick = safeTick(() => {
    elapsed += 1;
    dot.alpha = Math.max(0, 1 - elapsed / TRAIL_PARTICLE_LIFE_FRAMES);
    if (elapsed >= TRAIL_PARTICLE_LIFE_FRAMES) {
      ticker.remove(tick);
      dot.destroy();
    }
  });
  ticker.add(tick);
}

// --- growing-projectile: Fireblast's ball - grows in place, then travels, then bursts ---

const GROWING_PROJECTILE_RADIUS_PX = 10;
const GROWING_PROJECTILE_GROW_FRACTION = 0.25; // first 25% of the duration is the grow-in-place phase

function updateGrowingProjectile(
  view: Graphics,
  from: PixelCoord,
  to: PixelCoord,
  t: number,
  parent: Container,
  ticker: Ticker,
): void {
  const color = strokeColorOf(view) ?? 0xffffff;
  if (t < GROWING_PROJECTILE_GROW_FRACTION) {
    const growT = t / GROWING_PROJECTILE_GROW_FRACTION;
    const radius = GROWING_PROJECTILE_RADIUS_PX * easeOutCubic(growT);
    view.position.set(from.x, from.y);
    view.clear().circle(0, 0, radius).fill({ color });
    return;
  }
  const travelT = (t - GROWING_PROJECTILE_GROW_FRACTION) / (1 - GROWING_PROJECTILE_GROW_FRACTION);
  const eased = easeOutCubic(travelT);
  view.position.set(lerp(from.x, to.x, eased), lerp(from.y, to.y, eased));
  view.clear().circle(0, 0, GROWING_PROJECTILE_RADIUS_PX).fill({ color });
  if (travelT >= 1) {
    spawnParticleBurst(parent, ticker, { x: to.x, y: to.y, color, count: 18, speed: 2.6, life: 26, radius: 3 });
  }
}

// --- lightning: procedural jittering bolt, full-length instantly, no travel ---

const LIGHTNING_SEGMENTS = 8;
const LIGHTNING_JITTER_PX = 10;
const LIGHTNING_JITTER_INTERVAL_FRAMES = 4;
const LIGHTNING_WIDTH_PX = 3;
const LIGHTNING_FADE_FRACTION = 0.85; // fades over the final 15% (~150ms of 1000ms)

function updateLightning(view: Graphics, from: PixelCoord, to: PixelCoord, localElapsed: number, t: number, width: number): void {
  if (localElapsed % LIGHTNING_JITTER_INTERVAL_FRAMES === 1) {
    drawLightningBolt(view, from, to, width);
  }
  view.alpha = t < LIGHTNING_FADE_FRACTION ? 1 : 1 - (t - LIGHTNING_FADE_FRACTION) / (1 - LIGHTNING_FADE_FRACTION);
}

function drawLightningBolt(view: Graphics, from: PixelCoord, to: PixelCoord, width: number): void {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const len = Math.hypot(dx, dy) || 1;
  const nx = -dy / len;
  const ny = dx / len;

  const points: PixelCoord[] = [from];
  for (let i = 1; i < LIGHTNING_SEGMENTS; i++) {
    const segT = i / LIGHTNING_SEGMENTS;
    const offset = (Math.random() - 0.5) * 2 * LIGHTNING_JITTER_PX;
    points.push({ x: from.x + dx * segT + nx * offset, y: from.y + dy * segT + ny * offset });
  }
  points.push(to);

  view.clear();
  view.moveTo(points[0].x, points[0].y);
  for (let i = 1; i < points.length; i++) view.lineTo(points[i].x, points[i].y);
  view.stroke({ width, color: strokeColorOf(view) ?? 0xffffff, cap: "round", join: "round" });
}

// Graphics has no built-in "remembered fill colour" accessor, so lightning
// and beam stash their stroke colour on the view itself the first time
// they're drawn - simpler than threading an extra parameter through every
// per-frame call for a value that never changes after creation.
const strokeColors = new WeakMap<Graphics, number>();
function strokeColorOf(view: Graphics): number | undefined {
  return strokeColors.get(view);
}

// --- beam: a straight line, pulses width, fades in then out, no travel ---

const BEAM_BASE_WIDTH_PX = 4;
const BEAM_PULSE_AMPLITUDE_PX = 1.5;
const BEAM_PULSE_PERIOD_FRAMES = 10;
const BEAM_FADE_IN_FRACTION = 0.1; // first 10% (~100ms)
const BEAM_FADE_OUT_START = 0.85; // final 15% (~150ms)

function updateBeam(view: Graphics, from: PixelCoord, to: PixelCoord, localElapsed: number, t: number, toColor?: number): void {
  const width = BEAM_BASE_WIDTH_PX + BEAM_PULSE_AMPLITUDE_PX * Math.sin((localElapsed / BEAM_PULSE_PERIOD_FRAMES) * Math.PI * 2);
  const baseColor = strokeColorOf(view) ?? 0xffffff;
  const color = toColor === undefined ? baseColor : lerpColor(baseColor, toColor, t);
  view.clear();
  view.moveTo(from.x, from.y);
  view.lineTo(to.x, to.y);
  view.stroke({ width: Math.max(1, width), color, cap: "round" });

  if (t < BEAM_FADE_IN_FRACTION) {
    view.alpha = t / BEAM_FADE_IN_FRACTION;
  } else if (t > BEAM_FADE_OUT_START) {
    view.alpha = 1 - (t - BEAM_FADE_OUT_START) / (1 - BEAM_FADE_OUT_START);
  } else {
    view.alpha = 1;
  }
}

/** Channel-wise lerp between two 0xRRGGBB colours - Orbital Beam's white-to-dark-blue sweep. */
function lerpColor(from: number, to: number, t: number): number {
  const r = Math.round(lerp((from >> 16) & 0xff, (to >> 16) & 0xff, t));
  const g = Math.round(lerp((from >> 8) & 0xff, (to >> 8) & 0xff, t));
  const b = Math.round(lerp(from & 0xff, to & 0xff, t));
  return (r << 16) | (g << 8) | b;
}
