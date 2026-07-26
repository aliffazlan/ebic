// Unit "art" for v1: checks for /icons/<definitionId>.png first, falls back
// to a procedurally-drawn placeholder badge (team-colored circle + a ring
// colored by unitType + the unit's first-letter glyph), cached per
// (definitionId, team) pair as a Pixi RenderTexture. Real art can be dropped
// into web/public/icons/ later with zero code changes.

import { Assets, Container, Graphics, Text, RenderTexture, Texture } from "pixi.js";
import type { Renderer } from "pixi.js";
import type { Team, UnitType } from "../types/contract";

const TEAM_COLORS: Record<Team, number> = {
  PLAYER_ONE: 0x3b82f6, // blue
  PLAYER_TWO: 0xef4444, // red
};

const TYPE_RING_COLORS: Record<UnitType, number> = {
  CHAMPION: 0xd4af37, // gold
  ELITE: 0xc0c0c0, // silver
  BASIC: 0x9ca3af, // gray
};

const BADGE_SIZE = 64;

export class UnitIconFactory {
  private cache = new Map<string, Texture>();
  private confirmedMissingPng = new Set<string>();
  private renderer: Renderer;

  constructor(renderer: Renderer) {
    this.renderer = renderer;
  }

  async getTexture(
    definitionId: string,
    team: Team,
    unitType: UnitType,
    glyph: string,
  ): Promise<Texture> {
    const cacheKey = `${definitionId}:${team}`;
    const cached = this.cache.get(cacheKey);
    if (cached) return cached;

    const pngTexture = await this.tryLoadPng(definitionId);
    const texture = pngTexture ?? this.buildBadgeTexture(team, unitType, glyph);
    this.cache.set(cacheKey, texture);
    return texture;
  }

  private async tryLoadPng(definitionId: string): Promise<Texture | null> {
    if (this.confirmedMissingPng.has(definitionId)) return null;
    const url = `/icons/${definitionId}.png`;
    try {
      const head = await fetch(url, { method: "HEAD" });
      if (!head.ok) {
        this.confirmedMissingPng.add(definitionId);
        return null;
      }
      return await Assets.load<Texture>(url);
    } catch {
      this.confirmedMissingPng.add(definitionId);
      return null;
    }
  }

  private buildBadgeTexture(team: Team, unitType: UnitType, glyph: string): Texture {
    const half = BADGE_SIZE / 2;
    const container = new Container();

    const disc = new Graphics()
      .circle(half, half, half - 5)
      .fill({ color: TEAM_COLORS[team] ?? 0x999999 });
    container.addChild(disc);

    const ring = new Graphics()
      .circle(half, half, half - 5)
      .stroke({ width: 5, color: TYPE_RING_COLORS[unitType] ?? 0xffffff });
    container.addChild(ring);

    const text = new Text({
      text: glyph.slice(0, 2).toUpperCase(),
      style: { fill: 0xffffff, fontSize: 26, fontWeight: "bold", fontFamily: "sans-serif" },
    });
    text.anchor.set(0.5);
    text.position.set(half, half);
    container.addChild(text);

    const renderTexture = RenderTexture.create({ width: BADGE_SIZE, height: BADGE_SIZE });
    this.renderer.render({ container, target: renderTexture });
    container.destroy({ children: true });
    return renderTexture;
  }
}
