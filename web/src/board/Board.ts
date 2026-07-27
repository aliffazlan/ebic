// Hex board rendering: layered Containers (board tiles -> units -> vfx -> ui
// overlay), driven by GameStateSnapshot pushed through GameStateStore.

import { Application, Container, Graphics, Sprite, Ticker } from "pixi.js";
import { type AxialCoord, axialToPixel, hexPolygonPoints } from "../hex/HexMath";
import { UnitIconFactory } from "../units/UnitIconFactory";
import { GameStateStore, type MatchUiState } from "../state/GameStateStore";
import { spawnParticleBurst, colorForVfxType } from "../vfx/ParticleBurst";
import type { PlacementUnitSnapshot, UnitSnapshot, VfxEvent } from "../types/contract";

export const HEX_SIZE = 34;

const TILE_FILL = 0x1e293b;
const TILE_STROKE = 0x334155;
const TILE_HOVER = 0x334155;
const SELECTED_RING_COLOR = 0xfacc15;
const LEGAL_TILE_COLOR = 0x4ade80;
const LEGAL_UNIT_RING_COLOR = 0x4ade80;
const STROBE_RING_COLOR = 0xf97316;
// Frame counts assume Ticker.shared's default ~60fps - matches the
// frame-counting style already used in ParticleBurst.ts rather than
// deltaMS-based timing, for consistency with that existing pattern.
const STROBE_TOGGLE_FRAMES = 9; // ~150ms per on/off half-cycle
const STROBE_TOTAL_FRAMES = 108; // ~1.8s total, within the ~1.5-2s target

// PlacementStateSnapshot carries no map radius (see API_CONTRACT.md) -
// placement always happens before the first real GameStateSnapshot, which is
// the only message that carries the authoritative one. Assume this radius
// (matches Game.newFullDraftMatch()'s radius 8, per CLAUDE.md) until then;
// ensureMap() will correct it automatically the moment a real snapshot
// arrives with a different value.
const PLACEMENT_MAP_RADIUS = 8;

export interface BoardCallbacks {
  onTileClick(coord: AxialCoord): void;
  onUnitClick(unit: UnitSnapshot): void;
}

export class Board {
  readonly root = new Container();
  readonly boardLayer = new Container();
  readonly unitsLayer = new Container();
  readonly vfxLayer = new Container();
  // Pre-encounter strobe rings live in their own layer, separate from
  // uiLayer, because refreshHighlights() unconditionally clears uiLayer on
  // every store update (selection changes, message log pushes, etc.) - a
  // strobe needs to keep animating across those without being wiped
  // mid-flash by an unrelated state change.
  readonly strobeLayer = new Container();
  readonly uiLayer = new Container();

  private iconFactory: UnitIconFactory;
  private tileGraphics = new Map<string, Graphics>();
  private unitSprites = new Map<string, Container>();
  private mapRadius = -1;
  private unsubscribe: () => void;
  private app: Application;
  private store: GameStateStore;
  private callbacks: BoardCallbacks;
  // Tracked so destroy() can stop any in-flight strobe tickers rather than
  // leaving Ticker.shared calling back into a destroyed Board.
  private activeStrobeTicks = new Set<() => void>();

  constructor(app: Application, store: GameStateStore, callbacks: BoardCallbacks) {
    this.app = app;
    this.store = store;
    this.callbacks = callbacks;
    this.iconFactory = new UnitIconFactory(app.renderer);
    this.root.addChild(this.boardLayer, this.unitsLayer, this.vfxLayer, this.strobeLayer, this.uiLayer);
    this.recenter();

    this.unsubscribe = this.store.subscribe((state) => {
      if (state.snapshot) {
        void this.applySnapshot(state.snapshot);
      } else if (state.placementState) {
        this.applyPlacementSnapshot(state.placementState.units);
      }
      this.refreshHighlights(state);
    });
  }

  recenter(): void {
    this.root.position.set(this.app.screen.width / 2, this.app.screen.height / 2);
  }

  destroy(): void {
    this.unsubscribe();
    for (const tick of this.activeStrobeTicks) Ticker.shared.remove(tick);
    this.activeStrobeTicks.clear();
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

  /**
   * Flashes an on/off ring around each given unit's sprite for ~1.5-2s -
   * played on receiving an "attribute" prompt, before the attribute-choice
   * modal appears, so the two units in the encounter are visibly called out
   * on the board first. See API_CONTRACT.md's explanation of why the
   * `attribute` prompt's arrival is itself the earliest available signal for
   * "encounter starting." A unit missing from the current snapshot (shouldn't
   * happen - both ids come from the last real state push) is silently
   * skipped rather than erroring.
   */
  strobeUnits(unitIds: string[]): void {
    for (const unitId of new Set(unitIds)) {
      const sprite = this.unitSprites.get(unitId);
      if (sprite) this.strobeSprite(sprite);
    }
  }

  private strobeSprite(sprite: Container): void {
    const ring = new Graphics().circle(0, 0, HEX_SIZE * 0.85).stroke({ width: 4, color: STROBE_RING_COLOR });
    ring.position.copyFrom(sprite.position);
    this.strobeLayer.addChild(ring);

    let elapsed = 0;
    const tick = () => {
      elapsed += 1;
      ring.visible = Math.floor(elapsed / STROBE_TOGGLE_FRAMES) % 2 === 0;
      if (elapsed >= STROBE_TOTAL_FRAMES) {
        Ticker.shared.remove(tick);
        this.activeStrobeTicks.delete(tick);
        ring.destroy();
      }
    };
    this.activeStrobeTicks.add(tick);
    Ticker.shared.add(tick);
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

  /**
   * Renders this player's own placement-phase units. Reuses the normal
   * snapshot-apply pipeline by synthesizing minimal UnitSnapshots - no
   * hp/status/ability data exists yet at this phase (fog of war means the
   * opponent's units never appear here at all, so `team` is always our own),
   * so hp is left at 0/0 (draws as an empty sliver rather than a misleading
   * full bar) instead of guessing.
   */
  private applyPlacementSnapshot(units: PlacementUnitSnapshot[]): void {
    const yourTeam = this.store.getState().yourTeam;
    const synthetic: UnitSnapshot[] = units.map((u) => ({
      id: u.unitId,
      name: u.name,
      definitionId: u.definitionId,
      team: yourTeam,
      unitType: u.unitType,
      q: u.q,
      r: u.r,
      currentHp: 0,
      maxHp: 0,
      strength: 0,
      agility: 0,
      intelligence: 0,
      dead: false,
      hasMovedThisTurn: false,
      hasAttackedThisTurn: false,
      statusFlags: [],
      abilities: [],
      effects: [],
    }));
    void this.applySnapshot({ mapRadius: PLACEMENT_MAP_RADIUS, units: synthetic });
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

    // Active-status display moved entirely into the sidebar effects list (see
    // Hud.renderUnitPanel) - the board itself no longer renders floating
    // status-flag text above units, just sprite + HP bar + strobe ring.
    container.alpha = unit.dead ? 0.3 : 1;

    const pos = axialToPixel({ q: unit.q, r: unit.r }, HEX_SIZE);
    container.position.set(pos.x, pos.y);
  }

  /**
   * Draws (a) the placement-phase legal-move zone (PlacementStateSnapshot's
   * `legalTiles`, constant for the whole phase), (b) a ring around the
   * currently-selected unit - doubles as the placement-mode selection
   * indicator, since selectedUnitId is reused for both - and (c) once a
   * unit+ability is selected during the match proper, a highlight over every
   * tile/unit in the matching "action" prompt's legalTargets entry, straight
   * from Ability.getLegalTargets on the server. Placement's `move` edit still
   * has no client-side legality *validation* (see API_CONTRACT.md) - a click
   * outside the highlighted zone is still sent through and the server
   * accepts or rejects it - the zone highlight here is purely informational.
   */
  private refreshHighlights(state: MatchUiState): void {
    this.uiLayer.removeChildren();

    // Placement's legal zone is constant for the whole phase (see
    // API_CONTRACT.md's PlacementStateSnapshot.legalTiles) - highlight it
    // with the same green tile overlay used for ability legal targets so
    // players don't have to trial-and-error find the boundary. Purely
    // informational: the server still validates/rejects `move` edits itself.
    if (state.placementState && !state.placementState.confirmed) {
      for (const tile of state.placementState.legalTiles) {
        this.highlightTile(tile.q, tile.r);
      }
    }

    if (state.selectedUnitId) {
      const sprite = this.unitSprites.get(state.selectedUnitId);
      if (sprite) {
        const ring = new Graphics().circle(0, 0, HEX_SIZE * 0.75).stroke({ width: 3, color: SELECTED_RING_COLOR });
        ring.position.copyFrom(sprite.position);
        this.uiLayer.addChild(ring);
      }
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
