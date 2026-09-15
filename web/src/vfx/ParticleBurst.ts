// VFX seam (deliberately minimal for v1 — a polish pass comes later).
// Spawns N fading Graphics dots with velocity, removed on full fade.
// Wired by MatchScreen: every incoming `vfx` message calls this with a
// generic white burst at the relevant unit's position so the plumbing is
// proven end-to-end even before real per-event-type effects exist.

import { Container, Graphics, Ticker } from "pixi.js";
import { safeTick } from "./SafeTick";

export interface BurstOptions {
  x: number;
  y: number;
  color?: number;
  count?: number;
  speed?: number;
  /** lifetime in ticker frames */
  life?: number;
  radius?: number;
  /** Degrees, 0 = right, 90 = down, -90 = up (screen convention). Spread is centred on this. Omit for a full 360° burst. */
  directionDeg?: number;
  /** Total angular spread in degrees around directionDeg. Default 70. Ignored when directionDeg is unset. */
  spreadDeg?: number;
}

export function spawnParticleBurst(parent: Container, ticker: Ticker, opts: BurstOptions): void {
  const { x, y, color = 0xffffff, count = 14, speed = 2.2, life = 32, radius = 3, directionDeg, spreadDeg = 70 } = opts;

  for (let i = 0; i < count; i++) {
    const angle =
      directionDeg === undefined
        ? (Math.PI * 2 * i) / count + Math.random() * 0.4
        : ((directionDeg + (Math.random() - 0.5) * spreadDeg) * Math.PI) / 180;
    const velocity = speed * (0.5 + Math.random() * 0.6);
    const vx = Math.cos(angle) * velocity;
    const vy = Math.sin(angle) * velocity;

    const dot = new Graphics().circle(0, 0, radius).fill({ color });
    dot.position.set(x, y);
    parent.addChild(dot);

    let elapsed = 0;
    const tick = safeTick(() => {
      elapsed += 1;
      dot.position.x += vx;
      dot.position.y += vy;
      dot.alpha = Math.max(0, 1 - elapsed / life);
      if (elapsed >= life) {
        ticker.remove(tick);
        dot.destroy();
      }
    });
    ticker.add(tick);
  }
}

/** Picks a burst color per VfxEvent.type — same shape for every event today, easy to differentiate later. */
export function colorForVfxType(type: string): number {
  switch (type) {
    case "damage":
      return 0xef4444;
    case "heal":
      return 0x22c55e;
    case "death":
      return 0x9ca3af;
    case "status_applied":
      return 0xfacc15;
    case "ability_used":
      return 0x60a5fa;
    default:
      return 0xffffff;
  }
}
