// Unit tokens for the hex board: the face art from /icons/<artId>.webp, masked
// to a circle and ringed in the unit's team and type colours, cached per
// (artId, team) pair as a Pixi RenderTexture. A unit with no art file falls back
// to the original procedurally-drawn badge (team-coloured circle + type-coloured
// ring + the unit's first-letter glyph), so nothing ever renders empty.
//
// The rings are load-bearing, not decoration. The badge used to carry team as
// its *fill*, but the face art is full-bleed and opaque and covers that
// completely - without a ring, twenty blue units and twenty red ones would look
// identical. Team therefore moves to the inner ring and type stays on the outer
// one, so a token reads exactly as the badge does today: blue/red for side, gold
// for champions, silver for elites, grey for basics.

import { Assets, Container, Graphics, Sprite, Text, RenderTexture, Texture } from "pixi.js";
import type { Renderer } from "pixi.js";
import type { Team, UnitType } from "../types/contract";
import { artId, faceUrl } from "./UnitArt";
import { TEAM_COLOR as TEAM_COLORS } from "../ui/Colors";

const TYPE_RING_COLORS: Record<UnitType, number> = {
  CHAMPION: 0xd4af37, // gold
  ELITE: 0xc0c0c0, // silver
  BASIC: 0x9ca3af, // gray
};

const BADGE_SIZE = 64;

// The composed token, in texture pixels. Built from the outside in - the type
// ring sits flush with the outer edge, the team ring flush inside that, and the
// art fills whatever is left - so the bands are always gapless no matter how the
// widths are tuned. The board draws these at ~42px, so an 8px stroke here lands
// at just over 2px on screen.
const TOKEN_SIZE = 160;
const TOKEN_RADIUS = TOKEN_SIZE / 2;
const TEAM_RING_WIDTH = 8;

// A champion's ring is deliberately the heaviest thing on the board - there is
// exactly one per side, and it should be findable at a glance.
const TYPE_RING_WIDTHS: Record<UnitType, number> = {
  CHAMPION: 9,
  ELITE: 6,
  BASIC: 6,
};
const DEFAULT_TYPE_RING_WIDTH = 6;

export class UnitIconFactory {
  private cache = new Map<string, Texture>();
  private confirmedMissingArt = new Set<string>();
  private renderer: Renderer;

  constructor(renderer: Renderer) {
    this.renderer = renderer;
  }

  async getTexture(
    definitionId: string,
    team: Team,
    unitType: UnitType,
    glyph: string,
    name: string,
  ): Promise<Texture> {
    const id = artId(definitionId, name, team);
    const cacheKey = `${id}:${team}`;
    const cached = this.cache.get(cacheKey);
    if (cached) return cached;

    const art = await this.tryLoadArt(id);
    const texture = art
      ? this.composeArtTexture(art, team, unitType)
      : this.buildBadgeTexture(team, unitType, glyph);
    this.cache.set(cacheKey, texture);
    return texture;
  }

  /**
   * Pulls a roster's tokens into the cache before the board first draws them.
   * Each art id is composed for BOTH teams: the underlying file is fetched once
   * (Pixi's own Assets cache), so all the second pass costs is a cheap
   * render-to-texture, and the unit is then a guaranteed hit whichever side it
   * turns out to belong to - placement, which is the earliest point a roster is
   * known, doesn't say.
   */
  async preload(units: { definitionId: string; name: string; unitType: UnitType }[]): Promise<void> {
    const seen = new Set<string>();
    await Promise.all(
      units.flatMap((unit) =>
        (Object.keys(TEAM_COLORS) as Team[]).map((team) => {
          const key = `${artId(unit.definitionId, unit.name, team)}:${team}`;
          if (seen.has(key)) return null;
          seen.add(key);
          return this.getTexture(unit.definitionId, team, unit.unitType, unit.name.charAt(0), unit.name);
        }),
      ).filter((p) => p !== null),
    );
  }

  private async tryLoadArt(id: string): Promise<Texture | null> {
    if (this.confirmedMissingArt.has(id)) return null;
    const url = faceUrl(id);
    try {
      const head = await fetch(url, { method: "HEAD" });
      if (!head.ok) {
        this.confirmedMissingArt.add(id);
        return null;
      }
      return await Assets.load<Texture>(url);
    } catch {
      this.confirmedMissingArt.add(id);
      return null;
    }
  }

  private composeArtTexture(art: Texture, team: Team, unitType: UnitType): Texture {
    const half = TOKEN_RADIUS;
    const teamColor = TEAM_COLORS[team] ?? 0x999999;
    const typeWidth = TYPE_RING_WIDTHS[unitType] ?? DEFAULT_TYPE_RING_WIDTH;
    // Outside in, each band flush against the next.
    const typeRadius = TOKEN_RADIUS - typeWidth / 2;
    const teamRadius = TOKEN_RADIUS - typeWidth - TEAM_RING_WIDTH / 2;
    const artRadius = TOKEN_RADIUS - typeWidth - TEAM_RING_WIDTH;

    const container = new Container();

    // Sits under the art so a face with any transparency left in it reads
    // against the team colour rather than against nothing.
    container.addChild(new Graphics().circle(half, half, artRadius).fill({ color: teamColor }));

    const sprite = new Sprite(art);
    sprite.anchor.set(0.5);
    sprite.position.set(half, half);
    sprite.width = artRadius * 2;
    sprite.height = artRadius * 2;
    // Every piece of art is drawn facing right, so one side has to be flipped for
    // the two armies to face each other across the board rather than march the
    // same way. PLAYER_TWO is the one that turns - the board is not mirrored per
    // viewer, so both players see the same confrontation.
    if (team === "PLAYER_TWO") sprite.scale.x *= -1;
    const mask = new Graphics().circle(half, half, artRadius).fill({ color: 0xffffff });
    sprite.mask = mask;
    container.addChild(mask);
    container.addChild(sprite);

    container.addChild(
      new Graphics()
        .circle(half, half, teamRadius)
        .stroke({ width: TEAM_RING_WIDTH, color: teamColor }),
    );
    container.addChild(
      new Graphics()
        .circle(half, half, typeRadius)
        .stroke({ width: typeWidth, color: TYPE_RING_COLORS[unitType] ?? 0xffffff }),
    );

    return this.renderToTexture(container, TOKEN_SIZE);
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

    return this.renderToTexture(container, BADGE_SIZE);
  }

  private renderToTexture(container: Container, size: number): Texture {
    const renderTexture = RenderTexture.create({ width: size, height: size, antialias: true });
    this.renderer.render({ container, target: renderTexture });
    container.destroy({ children: true });
    return renderTexture;
  }
}
