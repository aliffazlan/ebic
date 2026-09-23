// PixiJS rendering for status-effect visuals: per-unit overlays (stun stars,
// pulsing circle, ember/smoke emitters, translucent circle, recoloured SVG
// overlays, rotating pentagram, jittering "jail bar" lines), a tile-level
// decoration (Cloak and Dagger), and pair-level visuals spanning two units
// (Duel's banners, Static Link's lightning). No test file - same category as
// AttackAnimationPlayer.ts/DamageIndicator.ts/ParticleBurst.ts, verified
// visually rather than under vitest.
//
// These are persistent, state-driven visuals (driven off a unit's `effects`
// on every snapshot), not one-shot vfx-triggered animations - see
// StatusEffects.ts. Because Board.upsertUnit rebuilds a unit's whole token
// from scratch on every snapshot, every continuous animation here derives
// its phase from wall-clock time (`performance.now()`) rather than "time
// since this view was created" - recreating the shape on the next rebuild is
// then visually seamless, there's nothing to lose. The one exception is a
// particle emitter's own next-spawn timer, which does restart on a rebuild -
// an accepted, barely-visible tradeoff (see the plan this was built from).

import { Assets, Container, Graphics, GraphicsContext, Sprite, Texture, Ticker } from "pixi.js";
import { hexCorner, hexPolygonPoints, type PixelCoord } from "../hex/HexMath";
import { cssHex } from "../ui/Colors";
import { safeTick } from "./SafeTick";
import type { UnitStatusVisual } from "./StatusEffects";
import iceSvg from "./icons/ice.svg?raw";
import shieldSvg from "./icons/shield.svg?raw";
import snowSvg from "./icons/snow.svg?raw";
import daggerSvg from "./icons/dagger.svg?raw";

function now(): number {
  return performance.now();
}

/** Splices `tick` out of `ticks` if present - a particle removing itself once its own lifetime ends. */
function removeFromTicks(ticks: (() => void)[], tick: () => void): void {
  const index = ticks.indexOf(tick);
  if (index !== -1) ticks.splice(index, 1);
}

/**
 * Builds and starts one unit's status-effect overlay, appended as more
 * children of its own token container. Pushes every ticker callback it
 * registers into the caller-owned `ticks` array (the caller, Board, is
 * responsible for calling `ticker.remove` on each before the next rebuild,
 * the same convention spawnAttackAnimation's strokes use) rather than
 * returning a fresh array - ember/smoke/snowflake's periodic emitters keep
 * pushing into this same array as they spawn new particles well after this
 * call returns, so a particle spawned moments before the status ends is
 * still reachable by the teardown that runs the instant it does, instead of
 * being left to finish out its own few-hundred-ms lifetime independently.
 */
export function spawnUnitStatusOverlay(
  parent: Container,
  ticker: Ticker,
  spec: UnitStatusVisual,
  tokenRadiusPx: number,
  ticks: (() => void)[],
): void {
  switch (spec.kind) {
    case "stun":
      ticks.push(...spawnStunStars(parent, ticker, spec.color));
      return;
    case "pulse":
      ticks.push(...spawnPulseCircle(parent, ticker, spec.color));
      return;
    case "ember":
      spawnEmberEmitter(parent, ticker, spec.color, ticks);
      return;
    case "snowflakes":
      spawnSnowflakeEmitter(parent, ticker, spec.color, ticks);
      return;
    case "translucent-circle":
      ticks.push(...spawnTranslucentCircle(parent, spec.color, tokenRadiusPx * (spec.radiusMultiplier ?? 1)));
      return;
    case "smoke":
      spawnSmokeEmitter(parent, ticker, spec.color, spec.thick ?? false, tokenRadiusPx, ticks);
      return;
    case "ice":
      ticks.push(...spawnIconOverlay(parent, "ice", spec.color, tokenRadiusPx * 2.2, 0.55));
      return;
    case "shield":
      ticks.push(...spawnIconOverlay(parent, "shield", spec.color, tokenRadiusPx * 1.8, 0.5));
      return;
    case "snow":
      // Was drawn far too big - trimmed 60% narrower, 20% shorter per temp/abilities2.txt.
      ticks.push(...spawnIconOverlay(parent, "snow", spec.color, tokenRadiusPx * 2.0, 0.7, 0.4, 0.8));
      return;
    case "energy-bars":
      ticks.push(...spawnEnergyBars(parent, ticker, spec.color, tokenRadiusPx));
      return;
    case "doom":
      ticks.push(...spawnDoom(parent, ticker, spec.color));
      return;
    case "shrink":
      // Handled directly by Board as a sprite-scale change - nothing to draw.
      return;
    case "chains":
      ticks.push(...spawnChainsOverlay(parent, spec.color, tokenRadiusPx));
      return;
    case "crosshair":
      spawnCrosshairOverlay(parent, spec.color);
      return;
    case "inward-particles":
      spawnInwardParticleEmitter(parent, ticker, spec.color, tokenRadiusPx, ticks);
      return;
  }
}

// --- stun: 3 small stars orbiting the unit's head in an ellipse, for depth ---

const STUN_ORBIT_PERIOD_MS = 1600;
const STUN_ORBIT_RX = 13;
const STUN_ORBIT_RY = 5;
const STUN_ORBIT_Y_OFFSET = -28;
const STUN_STAR_COUNT = 3;
const STUN_STAR_SIZE = 3;

function spawnStunStars(parent: Container, ticker: Ticker, color: number): (() => void)[] {
  const stars = Array.from({ length: STUN_STAR_COUNT }, () => {
    const g = new Graphics();
    parent.addChild(g);
    return g;
  });
  const tick = safeTick(() => {
    const t = now();
    for (let i = 0; i < stars.length; i++) {
      const phase = (t / STUN_ORBIT_PERIOD_MS) * Math.PI * 2 + (i * Math.PI * 2) / STUN_STAR_COUNT;
      const depth = Math.sin(phase); // -1 (back of the orbit) .. 1 (front)
      const x = Math.cos(phase) * STUN_ORBIT_RX;
      const y = STUN_ORBIT_Y_OFFSET + depth * STUN_ORBIT_RY;
      const scale = 0.65 + 0.35 * ((depth + 1) / 2);
      const star = stars[i];
      star.position.set(x, y);
      star.scale.set(scale);
      star.clear();
      drawSparkle(star, STUN_STAR_SIZE, color);
    }
  });
  ticker.add(tick);
  return [tick];
}

function drawSparkle(g: Graphics, size: number, color: number): void {
  g.moveTo(0, -size)
    .lineTo(size * 0.35, -size * 0.35)
    .lineTo(size, 0)
    .lineTo(size * 0.35, size * 0.35)
    .lineTo(0, size)
    .lineTo(-size * 0.35, size * 0.35)
    .lineTo(-size, 0)
    .lineTo(-size * 0.35, -size * 0.35)
    .closePath()
    .fill({ color });
}

// --- pulse: expanding translucent ring, repeats for the effect's duration ---
// Dilation only - radius doubled to cover every surrounding tile the buff
// actually affects, opacity trimmed 10% per temp/abilities2.txt.

const PULSE_PERIOD_MS = 2000;
const PULSE_MIN_RADIUS_PX = 20;
const PULSE_MAX_RADIUS_PX = 60;
const PULSE_ALPHA_SCALE = 0.9;

function spawnPulseCircle(parent: Container, ticker: Ticker, color: number): (() => void)[] {
  const g = new Graphics();
  parent.addChild(g);
  const tick = safeTick(() => {
    const progress = (now() % PULSE_PERIOD_MS) / PULSE_PERIOD_MS;
    const radius = PULSE_MIN_RADIUS_PX + (PULSE_MAX_RADIUS_PX - PULSE_MIN_RADIUS_PX) * progress;
    g.clear().circle(0, 0, radius).stroke({ width: 2, color, alpha: (1 - progress) * PULSE_ALPHA_SCALE });
  });
  ticker.add(tick);
  return [tick];
}

// --- ember: small squares jump diagonally upward then fall, one every 0.5s ---

const EMBER_INTERVAL_MS = 500;
const EMBER_LIFE_FRAMES = 40;
const EMBER_SIZE_PX = 3;
const EMBER_GRAVITY = 0.09;

function spawnEmberEmitter(parent: Container, ticker: Ticker, color: number, ticks: (() => void)[]): void {
  let nextSpawn = now();
  const tick = safeTick(() => {
    const t = now();
    if (t >= nextSpawn) {
      nextSpawn = t + EMBER_INTERVAL_MS;
      spawnEmberParticle(parent, ticker, color, ticks);
    }
  });
  ticker.add(tick);
  ticks.push(tick);
}

/**
 * Registers itself in the same `ticks` array the emitter (and Board's clearStatusEffectTicks)
 * uses, unlike AttackAnimationPlayer's spawnTrailParticle - a one-shot cast effect's trail can
 * safely outlive its cast by a fraction of a second, but a status particle spawned moments
 * before Burn/Poison itself expires must not be left running past clearStatusEffectTicks, or it
 * visibly outlives the status by up to its own lifetime.
 */
function spawnEmberParticle(parent: Container, ticker: Ticker, color: number, ticks: (() => void)[]): void {
  const dot = new Graphics().rect(-EMBER_SIZE_PX / 2, -EMBER_SIZE_PX / 2, EMBER_SIZE_PX, EMBER_SIZE_PX).fill({ color });
  dot.position.set((Math.random() - 0.5) * 24, 10);
  parent.addChild(dot);
  const vx = (Math.random() < 0.5 ? -1 : 1) * (0.3 + Math.random() * 0.3);
  let vy = -1.6 - Math.random() * 0.5;
  let elapsed = 0;
  const tick = safeTick(() => {
    elapsed += 1;
    vy += EMBER_GRAVITY;
    dot.position.x += vx;
    dot.position.y += vy;
    dot.alpha = Math.max(0, 1 - elapsed / EMBER_LIFE_FRAMES);
    if (elapsed >= EMBER_LIFE_FRAMES) {
      ticker.remove(tick);
      removeFromTicks(ticks, tick);
      dot.destroy();
    }
  });
  ticks.push(tick);
  ticker.add(tick);
}

// --- snowflakes: small drifting flakes fall past the unit, one every 0.4s ---
// Frostbite only - deliberately distinct from Blizzard's static "snow" SVG icon
// overlay (see StatusEffects.ts) so the two effects don't read as the same thing.

const SNOWFLAKE_INTERVAL_MS = 400;
const SNOWFLAKE_LIFE_FRAMES = 60;
const SNOWFLAKE_SIZE_PX = 3;
const SNOWFLAKE_FALL_SPEED = 0.6;
const SNOWFLAKE_DRIFT_AMPLITUDE = 6;

function spawnSnowflakeEmitter(parent: Container, ticker: Ticker, color: number, ticks: (() => void)[]): void {
  let nextSpawn = now();
  const tick = safeTick(() => {
    const t = now();
    if (t >= nextSpawn) {
      nextSpawn = t + SNOWFLAKE_INTERVAL_MS;
      spawnSnowflakeParticle(parent, ticker, color, ticks);
    }
  });
  ticker.add(tick);
  ticks.push(tick);
}

/** See spawnEmberParticle's doc comment - registered in the shared `ticks` array for the same reason. */
function spawnSnowflakeParticle(parent: Container, ticker: Ticker, color: number, ticks: (() => void)[]): void {
  const flake = new Graphics();
  drawSnowflake(flake, SNOWFLAKE_SIZE_PX, color);
  const startX = (Math.random() - 0.5) * 28;
  flake.position.set(startX, -18);
  parent.addChild(flake);
  const driftPhase = Math.random() * Math.PI * 2;
  let elapsed = 0;
  const tick = safeTick(() => {
    elapsed += 1;
    flake.position.y += SNOWFLAKE_FALL_SPEED;
    flake.position.x = startX + Math.sin(driftPhase + elapsed * 0.15) * SNOWFLAKE_DRIFT_AMPLITUDE;
    flake.rotation += 0.03;
    flake.alpha = Math.max(0, 1 - elapsed / SNOWFLAKE_LIFE_FRAMES);
    if (elapsed >= SNOWFLAKE_LIFE_FRAMES) {
      ticker.remove(tick);
      removeFromTicks(ticks, tick);
      flake.destroy();
    }
  });
  ticks.push(tick);
  ticker.add(tick);
}

/** A simple 6-point asterisk, cheaper than parsing an SVG for a shape this small. */
function drawSnowflake(g: Graphics, size: number, color: number): void {
  for (let i = 0; i < 3; i++) {
    const angle = (i * Math.PI) / 3;
    const dx = Math.cos(angle) * size;
    const dy = Math.sin(angle) * size;
    g.moveTo(-dx, -dy).lineTo(dx, dy);
  }
  g.stroke({ width: 1, color });
}

// --- translucent circle: a static overlay shape (not a filter - see StatusEffects.ts) ---

const OBLIVION_CIRCLE_ALPHA = 0.35;

function spawnTranslucentCircle(parent: Container, color: number, radiusPx: number): (() => void)[] {
  const g = new Graphics().circle(0, 0, radiusPx).fill({ color, alpha: OBLIVION_CIRCLE_ALPHA });
  parent.addChild(g);
  return [];
}

// --- smoke: thin lines drift up from random spots and fade, one every 0.5s ---
// Shared by Steady Focus, Feast, Nanobots, Poison, and Poison Bloom - only the
// colour (and Poison Bloom's `thick` flag) differs. See temp/abilities.txt.

const SMOKE_INTERVAL_MS = 500;
const SMOKE_LIFE_FRAMES = 45;

function spawnSmokeEmitter(
  parent: Container,
  ticker: Ticker,
  color: number,
  thick: boolean,
  radiusPx: number,
  ticks: (() => void)[],
): void {
  let nextSpawn = now();
  const tick = safeTick(() => {
    const t = now();
    if (t >= nextSpawn) {
      nextSpawn = t + SMOKE_INTERVAL_MS;
      spawnSmokeWisp(parent, ticker, color, thick, radiusPx, ticks);
    }
  });
  ticker.add(tick);
  ticks.push(tick);
}

/** See spawnEmberParticle's doc comment - registered in the shared `ticks` array for the same reason. */
function spawnSmokeWisp(
  parent: Container,
  ticker: Ticker,
  color: number,
  thick: boolean,
  radiusPx: number,
  ticks: (() => void)[],
): void {
  // `thick` is Poison Bloom's alone - bumped substantially larger per temp/abilities.txt
  // ("much larger particles"), safe to change in isolation since nothing else sets it.
  const lineLenPx = thick ? 16 : 5;
  const widthPx = thick ? 5 : 1;
  const wisp = new Graphics().moveTo(0, 0).lineTo(0, -lineLenPx).stroke({ width: widthPx, color, alpha: 0.8 });
  wisp.position.set((Math.random() - 0.5) * radiusPx * 1.4, (Math.random() - 0.5) * radiusPx * 1.2);
  parent.addChild(wisp);
  const drift = (Math.random() - 0.5) * 0.3;
  let elapsed = 0;
  const tick = safeTick(() => {
    elapsed += 1;
    wisp.position.x += drift;
    wisp.position.y -= 0.5;
    wisp.alpha = Math.max(0, 0.8 * (1 - elapsed / SMOKE_LIFE_FRAMES));
    if (elapsed >= SMOKE_LIFE_FRAMES) {
      ticker.remove(tick);
      removeFromTicks(ticks, tick);
      wisp.destroy();
    }
  });
  ticks.push(tick);
  ticker.add(tick);
}

// --- ice / shield / snow: recoloured SVG overlay, static (no ticker) ---
// Same currentColor + GraphicsContext().svg() recolour pipeline as
// AttackAnimationPlayer.ts/DamageIndicator.ts, cached per (kind, colour).

const ICON_COLOR_PLACEHOLDER = "currentColor";
const OVERLAY_ICON_SOURCE: Record<"ice" | "shield" | "snow", string> = { ice: iceSvg, shield: shieldSvg, snow: snowSvg };
const overlayIconContexts = new Map<string, GraphicsContext>();

function overlayIconContextFor(kind: "ice" | "shield" | "snow", color: number): GraphicsContext {
  const key = `${kind}:${cssHex(color)}`;
  const cached = overlayIconContexts.get(key);
  if (cached) return cached;
  const context = new GraphicsContext().svg(OVERLAY_ICON_SOURCE[kind].replaceAll(ICON_COLOR_PLACEHOLDER, cssHex(color)));
  overlayIconContexts.set(key, context);
  return context;
}

function spawnIconOverlay(
  parent: Container,
  kind: "ice" | "shield" | "snow",
  color: number,
  heightPx: number,
  alpha: number,
  scaleXMultiplier = 1,
  scaleYMultiplier = 1,
): (() => void)[] {
  const view = new Graphics({ context: overlayIconContextFor(kind, color) });
  const bounds = view.getLocalBounds();
  const scale = bounds.height > 0 ? heightPx / bounds.height : 1;
  view.scale.set(scale * scaleXMultiplier, scale * scaleYMultiplier);
  view.pivot.set(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
  view.alpha = alpha;
  parent.addChild(view);
  return [];
}

// --- doom: a pentagram inside a circle, both slowly rotating together ---

// Rotation speed halved (period doubled, was 6000) and opacity dropped to ~70% (was fully
// opaque) per temp/abilities.txt.
const DOOM_ROTATE_PERIOD_MS = 12000;
const DOOM_RADIUS_PX = 16;
const DOOM_ALPHA = 0.7;

function spawnDoom(parent: Container, ticker: Ticker, color: number): (() => void)[] {
  const g = new Graphics();
  drawPentagram(g, DOOM_RADIUS_PX, color);
  g.alpha = DOOM_ALPHA;
  parent.addChild(g);
  const tick = safeTick(() => {
    g.rotation = ((now() % DOOM_ROTATE_PERIOD_MS) / DOOM_ROTATE_PERIOD_MS) * Math.PI * 2;
  });
  ticker.add(tick);
  return [tick];
}

/** Drawn once and rotated wholesale via view.rotation - cheaper than redrawing every frame, and rotation alone is all the "slowly rotating" ask needs. */
function drawPentagram(g: Graphics, radius: number, color: number): void {
  g.circle(0, 0, radius).stroke({ width: 2, color });
  const points: PixelCoord[] = [];
  for (let i = 0; i < 5; i++) {
    const angle = -Math.PI / 2 + (i * 2 * Math.PI) / 5;
    points.push({ x: Math.cos(angle) * radius * 0.85, y: Math.sin(angle) * radius * 0.85 });
  }
  g.moveTo(points[0].x, points[0].y);
  for (let step = 1; step <= 5; step++) {
    const p = points[(step * 2) % 5];
    g.lineTo(p.x, p.y);
  }
  g.stroke({ width: 2, color, join: "round" });
}

// --- chains: two curved arcs crossing in an X, static (no ticker) ---
// Infernal Blade only - procedural rather than an SVG, same call as Frostbite's
// snowflakes: a simple enough shape not to need real artwork.

// Was 1.3 (deliberately larger than the token, which is exactly why it overflowed the
// portrait) - shrunk ~40% per the user's own estimate so the X now sits inside it.
const CHAIN_RADIUS_MULTIPLIER = 0.78;
const CHAIN_WIDTH_PX = 3;
const CHAIN_BULGE_FRACTION = 0.18;

function spawnChainsOverlay(parent: Container, color: number, radiusPx: number): (() => void)[] {
  const g = new Graphics();
  const r = radiusPx * CHAIN_RADIUS_MULTIPLIER;
  drawChainArc(g, -r, -r, r, r, color); // top-left to bottom-right
  drawChainArc(g, r, -r, -r, r, color); // top-right to bottom-left
  parent.addChild(g);
  return [];
}

/** One curved stroke of the X, bulging perpendicular to its own line for a chain-like sag. */
function drawChainArc(g: Graphics, x1: number, y1: number, x2: number, y2: number, color: number): void {
  const dx = x2 - x1;
  const dy = y2 - y1;
  const len = Math.hypot(dx, dy) || 1;
  const nx = -dy / len;
  const ny = dx / len;
  const bulge = len * CHAIN_BULGE_FRACTION;
  const cx = (x1 + x2) / 2 + nx * bulge;
  const cy = (y1 + y2) / 2 + ny * bulge;
  g.moveTo(x1, y1).quadraticCurveTo(cx, cy, x2, y2).stroke({ width: CHAIN_WIDTH_PX, color, cap: "round" });
}

// --- crosshair: High Noon's mark, static (no ticker) ---
// The exact reticle Board.ts used to draw on Homing Missile's impact tile (arm = HEX_SIZE *
// 0.34), moved here onto the marked unit's own token.

const CROSSHAIR_ARM_PX = 11.5;
const CROSSHAIR_RING_RADIUS_PX = CROSSHAIR_ARM_PX * 0.55;
const CROSSHAIR_ALPHA = 0.9;

function spawnCrosshairOverlay(parent: Container, color: number): void {
  const g = new Graphics();
  g.moveTo(-CROSSHAIR_ARM_PX, 0).lineTo(CROSSHAIR_ARM_PX, 0).moveTo(0, -CROSSHAIR_ARM_PX).lineTo(0, CROSSHAIR_ARM_PX)
    .stroke({ width: 2, color, alpha: CROSSHAIR_ALPHA });
  g.circle(0, 0, CROSSHAIR_RING_RADIUS_PX).stroke({ width: 2, color, alpha: CROSSHAIR_ALPHA });
  parent.addChild(g);
}

// --- inward particles: Bloodwake - large dots drawn in from outside the portrait ---
// Each spawns on a ring outside the token and eases toward its centre, but is fully faded
// by INWARD_FADE_OUT_AT of the way in, so none ever actually reaches the centre.

const INWARD_INTERVAL_MS = 120;
const INWARD_LIFE_FRAMES = 36;
const INWARD_RADIUS_PX = 3.5;
const INWARD_SPAWN_RADIUS_MULTIPLIER = 1.7;
const INWARD_FADE_OUT_AT = 0.6;

function spawnInwardParticleEmitter(parent: Container, ticker: Ticker, color: number, radiusPx: number, ticks: (() => void)[]): void {
  let nextSpawn = now();
  const tick = safeTick(() => {
    const t = now();
    if (t >= nextSpawn) {
      nextSpawn = t + INWARD_INTERVAL_MS;
      spawnInwardParticle(parent, ticker, color, radiusPx, ticks);
    }
  });
  ticker.add(tick);
  ticks.push(tick);
}

/** See spawnEmberParticle's doc comment - registered in the shared `ticks` array for the same reason. */
function spawnInwardParticle(parent: Container, ticker: Ticker, color: number, radiusPx: number, ticks: (() => void)[]): void {
  const angle = Math.random() * Math.PI * 2;
  const startX = Math.cos(angle) * radiusPx * INWARD_SPAWN_RADIUS_MULTIPLIER;
  const startY = Math.sin(angle) * radiusPx * INWARD_SPAWN_RADIUS_MULTIPLIER;
  const dot = new Graphics().circle(0, 0, INWARD_RADIUS_PX).fill({ color });
  dot.position.set(startX, startY);
  dot.alpha = 0;
  parent.addChild(dot);
  let elapsed = 0;
  const tick = safeTick(() => {
    elapsed += 1;
    const t = Math.min(1, elapsed / INWARD_LIFE_FRAMES);
    const travelled = INWARD_FADE_OUT_AT * t * t; // ease-in, never past INWARD_FADE_OUT_AT of the way
    dot.position.set(startX * (1 - travelled), startY * (1 - travelled));
    // Quick fade in, then out to nothing by the end of its (shortened) path.
    dot.alpha = t < 0.2 ? t / 0.2 : 1 - (t - 0.2) / 0.8;
    if (t >= 1) {
      ticker.remove(tick);
      removeFromTicks(ticks, tick);
      dot.destroy();
    }
  });
  ticks.push(tick);
  ticker.add(tick);
}

// --- energy shield: 3 thin jittering lines across the token, like jail bars ---

const ENERGY_BAR_COUNT = 3;
const ENERGY_JITTER_INTERVAL_FRAMES = 4;
const ENERGY_JITTER_PX = 3;

function spawnEnergyBars(parent: Container, ticker: Ticker, color: number, radiusPx: number): (() => void)[] {
  const g = new Graphics();
  parent.addChild(g);
  let frame = 0;
  const draw = () => {
    g.clear();
    for (let i = 0; i < ENERGY_BAR_COUNT; i++) {
      const x = -radiusPx * 0.6 + (i * radiusPx * 1.2) / (ENERGY_BAR_COUNT - 1);
      g.moveTo(x + jitter(), -radiusPx)
        .lineTo(x + jitter(), 0)
        .lineTo(x + jitter(), radiusPx);
    }
    g.stroke({ width: 1.5, color, alpha: 0.85 });
  };
  const jitter = () => (Math.random() - 0.5) * 2 * ENERGY_JITTER_PX;
  draw();
  const tick = safeTick(() => {
    frame += 1;
    if (frame % ENERGY_JITTER_INTERVAL_FRAMES === 0) draw();
  });
  ticker.add(tick);
  return [tick];
}

// --- Cloak and Dagger: a tile-level decoration, not a per-unit overlay ---
// Not a real TileEffectSnapshot on the wire (CloakEffect is a unit-attached
// Effect on the caster) - Board synthesizes this from the caster's own
// q,r/effects list. dagger.svg is used as-is (already coloured, not
// recoloured) and points downward in its own art.

const CLOAK_TILE_FILL = 0x7c3aed;
const CLOAK_TILE_ALPHA = 0.28;
const CLOAK_DAGGER_HEIGHT_PX = 16;
let daggerContextCache: GraphicsContext | null = null;

function daggerGraphicsContext(): GraphicsContext {
  daggerContextCache ??= new GraphicsContext().svg(daggerSvg);
  return daggerContextCache;
}

export function drawCloakTile(parent: Container, center: PixelCoord, hexSize: number): void {
  const points = hexPolygonPoints({ x: 0, y: 0 }, hexSize - 2);
  const tile = new Graphics().poly(points).fill({ color: CLOAK_TILE_FILL, alpha: CLOAK_TILE_ALPHA });
  tile.position.set(center.x, center.y);
  parent.addChild(tile);

  const context = daggerGraphicsContext();
  for (let i = 0; i < 6; i++) {
    const corner = hexCorner({ x: 0, y: 0 }, hexSize - 2, i);
    const dagger = new Graphics({ context });
    const bounds = dagger.getLocalBounds();
    const scale = bounds.height > 0 ? CLOAK_DAGGER_HEIGHT_PX / bounds.height : 1;
    dagger.scale.set(scale);
    dagger.pivot.set(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    // hexCorner's corner i sits at angle (60*i - 30) deg from the hex centre;
    // pointing inward is the reverse of that direction, and the art's own
    // rest pose already points downward (90deg), hence the extra -90deg.
    const angleDeg = 60 * i - 30 + 180;
    dagger.rotation = (angleDeg * Math.PI) / 180 - Math.PI / 2;
    dagger.position.set(center.x + corner.x, center.y + corner.y);
    parent.addChild(dagger);
  }
}

// --- Duel: a banner planted beside each duelist, facing away from the other ---

const BANNER_URL = "/assets/effects/banner.webp";
const BANNER_HEIGHT_PX = 46;
const BANNER_OFFSET_PX = 30;
let bannerTexturePromise: Promise<Texture> | null = null;

function loadBannerTexture(): Promise<Texture> {
  bannerTexturePromise ??= Assets.load<Texture>(BANNER_URL);
  return bannerTexturePromise;
}

/** Async (a texture load) - Board awaits this alongside its other per-snapshot texture work. Fails silently, same fallback philosophy as a missing icon. */
export async function spawnDuelBanners(parent: Container, a: PixelCoord, b: PixelCoord): Promise<void> {
  let texture: Texture;
  try {
    texture = await loadBannerTexture();
  } catch {
    return;
  }
  const dx = b.x - a.x;
  const dy = b.y - a.y;
  const len = Math.hypot(dx, dy) || 1;
  const ux = dx / len;
  const uy = dy / len;
  placeBanner(parent, texture, { x: a.x - ux * BANNER_OFFSET_PX, y: a.y - uy * BANNER_OFFSET_PX });
  placeBanner(parent, texture, { x: b.x + ux * BANNER_OFFSET_PX, y: b.y + uy * BANNER_OFFSET_PX });
}

function placeBanner(parent: Container, texture: Texture, pos: PixelCoord): void {
  const sprite = new Sprite(texture);
  sprite.anchor.set(0.5, 1);
  sprite.scale.set(BANNER_HEIGHT_PX / texture.height);
  sprite.position.set(pos.x, pos.y);
  parent.addChild(sprite);
}

// --- Static Link: a thin continuous jittering line between the two linked units ---

const STATIC_LINK_WIDTH_PX = 1.5;
const STATIC_LINK_JITTER_PX = 4;
const STATIC_LINK_JITTER_INTERVAL_FRAMES = 4;
const STATIC_LINK_SEGMENTS = 6;

export function spawnStaticLink(parent: Container, ticker: Ticker, a: PixelCoord, b: PixelCoord, color: number): () => void {
  const g = new Graphics();
  parent.addChild(g);
  let frame = 0;
  const draw = () => drawJitterLine(g, a, b, STATIC_LINK_SEGMENTS, STATIC_LINK_JITTER_PX, STATIC_LINK_WIDTH_PX, color);
  draw();
  const tick = safeTick(() => {
    frame += 1;
    if (frame % STATIC_LINK_JITTER_INTERVAL_FRAMES === 0) draw();
  });
  ticker.add(tick);
  return tick;
}

function drawJitterLine(
  g: Graphics,
  from: PixelCoord,
  to: PixelCoord,
  segments: number,
  jitterPx: number,
  widthPx: number,
  color: number,
): void {
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  const len = Math.hypot(dx, dy) || 1;
  const nx = -dy / len;
  const ny = dx / len;
  const points: PixelCoord[] = [from];
  for (let i = 1; i < segments; i++) {
    const segT = i / segments;
    const offset = (Math.random() - 0.5) * 2 * jitterPx;
    points.push({ x: from.x + dx * segT + nx * offset, y: from.y + dy * segT + ny * offset });
  }
  points.push(to);
  g.clear();
  g.moveTo(points[0].x, points[0].y);
  for (let i = 1; i < points.length; i++) g.lineTo(points[i].x, points[i].y);
  g.stroke({ width: widthPx, color, cap: "round", join: "round" });
}
