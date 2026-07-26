// Hex board rendering: layered Containers (board tiles -> units -> vfx -> ui
// overlay), driven by GameStateSnapshot pushed through GameStateStore.

import { Application, Container, Graphics, Sprite, Text, Ticker } from "pixi.js";
import { type AxialCoord, axialToPixel, hexPolygonPoints } from "../hex/HexMath";
import { UnitIconFactory } from "../units/UnitIconFactory";
import { GameStateStore, type MatchUiState } from "../state/GameStateStore";
import { spawnParticleBurst, colorForVfxType } from "../vfx/ParticleBurst";
import type { UnitSnapshot, VfxEvent } from "../types/contract";

export const HEX_SIZE = 34;

const TILE_FILL = 0x1e293b;
const TILE_STROKE = 0x334155;
const TILE_HOVER = 0x334155;
const SELECTED_RING_COLOR = 0xfacc15;
const LEGAL_TILE_COLOR = 0x4ade80;
const LEGAL_UNIT_RING_COLOR = 0x4ade80;

export interface BoardCallbacks {
  onTileClick(coord: AxialCoord): void;
  onUnitClick(unit: UnitSnapshot): void;
}

export class Board {
  readonly root = new Container();
  readonly boardLayer = new Container();
  readonly unitsLayer = new Container();
  readonly vfxLayer = new Container();
  readonly uiLayer = new Container();

  private iconFactory: UnitIconFactory;
  private tileGraphics = new Map<string, Graphics>();
  private unitSprites = new Map<string, Container>();
  private mapRadius = -1;
  private unsubscribe: () => void;
  private app: Application;
  private store: GameStateStore;
  private callbacks: BoardCallbacks;

  constructor(app: Application, store: GameStateStore, callbacks: BoardCallbacks) {
    this.app = app;
    this.store = store;
    this.callbacks = callbacks;
    this.iconFactory = new UnitIconFactory(app.renderer);
    this.root.addChild(this.boardLayer, this.unitsLayer, this.vfxLayer, this.uiLayer);
    this.recenter();

    this.unsubscribe = this.store.subscribe((state) => {
      if (state.snapshot) {
        void this.applySnapshot(state.snapshot);
      }
      this.refreshHighlights(state);
    });
  }

  recenter(): void {
    this.root.position.set(this.app.screen.width / 2, this.app.screen.height / 2);
  }

  destroy(): void {
    this.unsubscribe();
    this.root.destroy({ children: true });
  }

  /** Plays a generic burst at the position of a unit from the *pre-update* snapshot (vfx arrives before state). */
  playVfx(events: VfxEvent[]): void {
    const snapshot = this.store.getState().snapshot;
    for (const event of events) {
      const unitId = event.targetUnitId ?? event.sourceUnitId;
      const unit = unitId ? snapshot?.units.find((u) => u.id === unitId) : null;
      const pos = unit ? axialToPixel({ q: unit.q, r: unit.r }, HEX_SIZE) : { x: 0, y: 0 };
      spawnParticleBurst(this.vfxLayer, Ticker.shared, {
        x: pos.x,
        y: pos.y,
        color: colorForVfxType(event.type),
      });
    }
  }

  private ensureMap(radius: number): void {
    if (radius === this.mapRadius) return;
    this.mapRadius = radius;
    this.boardLayer.removeChildren();
    this.tileGraphics.clear();

    for (let q = -radius; q <= radius; q++) {
      const rMin = Math.max(-radius, -q - radius);
      const rMax = Math.min(radius, -q + radius);
      for (let r = rMin; r <= rMax; r++) {
        const tile = this.drawTile({ q, r });
        this.tileGraphics.set(`${q},${r}`, tile);
        this.boardLayer.addChild(tile);
      }
    }
  }

  private drawTile(coord: AxialCoord): Graphics {
    const center = axialToPixel(coord, HEX_SIZE);
    const points = hexPolygonPoints({ x: 0, y: 0 }, HEX_SIZE - 1);
    const g = new Graphics().poly(points).fill({ color: TILE_FILL }).stroke({ width: 1, color: TILE_STROKE });
    g.position.set(center.x, center.y);
    g.eventMode = "static";
    g.cursor = "pointer";
    g.on("pointerover", () => {
      g.clear().poly(points).fill({ color: TILE_HOVER }).stroke({ width: 1, color: TILE_STROKE });
    });
    g.on("pointerout", () => {
      g.clear().poly(points).fill({ color: TILE_FILL }).stroke({ width: 1, color: TILE_STROKE });
    });
    g.on("pointertap", () => this.callbacks.onTileClick(coord));
    return g;
  }

  private async applySnapshot(snapshot: { mapRadius: number; units: UnitSnapshot[] }): Promise<void> {
    this.ensureMap(snapshot.mapRadius);

    const seen = new Set<string>();
    for (const unit of snapshot.units) {
      seen.add(unit.id);
      await this.upsertUnit(unit);
    }
    for (const [id, sprite] of this.unitSprites) {
      if (!seen.has(id)) {
        sprite.destroy({ children: true });
        this.unitSprites.delete(id);
      }
    }
  }

  private async upsertUnit(unit: UnitSnapshot): Promise<void> {
    let container = this.unitSprites.get(unit.id);
    if (!container) {
      container = new Container();
      container.eventMode = "static";
      container.cursor = "pointer";
      container.on("pointertap", (e) => {
        e.stopPropagation();
        this.callbacks.onUnitClick(unit);
      });
      this.unitsLayer.addChild(container);
      this.unitSprites.set(unit.id, container);
    }
    container.removeChildren();

    const texture = await this.iconFactory.getTexture(
      unit.definitionId,
      unit.team,
      unit.unitType,
      unit.name.charAt(0),
    );
    const sprite = new Sprite(texture);
    sprite.anchor.set(0.5);
    sprite.width = HEX_SIZE * 1.15;
    sprite.height = HEX_SIZE * 1.15;
    container.addChild(sprite);

    const barWidth = HEX_SIZE * 1.1;
    const barY = HEX_SIZE / 2 + 3;
    container.addChild(
      new Graphics().rect(-barWidth / 2, barY, barWidth, 5).fill({ color: 0x000000, alpha: 0.6 }),
    );
    const hpFraction = unit.maxHp > 0 ? Math.max(0, unit.currentHp / unit.maxHp) : 0;
    const hpColor = hpFraction > 0.5 ? 0x22c55e : hpFraction > 0.25 ? 0xeab308 : 0xef4444;
    container.addChild(
      new Graphics().rect(-barWidth / 2, barY, barWidth * hpFraction, 5).fill({ color: hpColor }),
    );

    if (unit.statusFlags.length > 0) {
      const label = new Text({
        text: unit.statusFlags.map((f) => f.slice(0, 4)).join(" "),
        style: { fontSize: 8, fill: 0xfacc15, fontFamily: "sans-serif" },
      });
      label.anchor.set(0.5, 1);
      label.position.set(0, -HEX_SIZE / 2 - 3);
      container.addChild(label);
    }

    container.alpha = unit.dead ? 0.3 : 1;

    const pos = axialToPixel({ q: unit.q, r: unit.r }, HEX_SIZE);
    container.position.set(pos.x, pos.y);
  }

  /**
   * Draws (a) a ring around the currently-selected unit, and (b) a highlight over
   * every tile/unit that's actually legal to click next - either the server's
   * placement candidates, or (once a unit+ability is both selected) the matching
   * entry in the "action" prompt's legalTargets, straight from Ability.getLegalTargets
   * on the server. Replaces accept-then-reject with "only the clickable things glow."
   */
  private refreshHighlights(state: MatchUiState): void {
    this.uiLayer.removeChildren();

    if (state.selectedUnitId) {
      const sprite = this.unitSprites.get(state.selectedUnitId);
      if (sprite) {
        const ring = new Graphics().circle(0, 0, HEX_SIZE * 0.75).stroke({ width: 3, color: SELECTED_RING_COLOR });
        ring.position.copyFrom(sprite.position);
        this.uiLayer.addChild(ring);
      }
    }

    if (state.prompt?.kind === "placement" && state.prompt.candidates) {
      for (const tile of state.prompt.candidates) {
        this.highlightTile(tile.q, tile.r);
      }
      return;
    }

    if (state.prompt?.kind === "action" && state.selectedUnitId && state.selectedAbilityId) {
      const legal = state.prompt.legalTargets?.[state.selectedUnitId]?.[state.selectedAbilityId];
      if (!legal) return;
      for (const tile of legal.tiles) {
        this.highlightTile(tile.q, tile.r);
      }
      for (const unitId of legal.unitIds) {
        const sprite = this.unitSprites.get(unitId);
        if (!sprite) continue;
        const ring = new Graphics().circle(0, 0, HEX_SIZE * 0.75).stroke({ width: 3, color: LEGAL_UNIT_RING_COLOR });
        ring.position.copyFrom(sprite.position);
        this.uiLayer.addChild(ring);
      }
    }
  }

  private highlightTile(q: number, r: number): void {
    const center = axialToPixel({ q, r }, HEX_SIZE);
    const points = hexPolygonPoints({ x: 0, y: 0 }, HEX_SIZE - 3);
    const highlight = new Graphics()
      .poly(points)
      .fill({ color: LEGAL_TILE_COLOR, alpha: 0.25 })
      .stroke({ width: 2, color: LEGAL_TILE_COLOR, alpha: 0.8 });
    highlight.position.set(center.x, center.y);
    this.uiLayer.addChild(highlight);
  }
}
