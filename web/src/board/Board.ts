// Hex board rendering: layered Containers (board tiles -> units -> vfx -> ui
// overlay), driven by GameStateSnapshot pushed through GameStateStore.

import { Application, Container, Graphics, Sprite, Ticker } from "pixi.js";
import { type AxialCoord, axialToPixel, hexDistance, hexPolygonPoints } from "../hex/HexMath";
import { UnitIconFactory } from "../units/UnitIconFactory";
import { GameStateStore, type MatchUiState } from "../state/GameStateStore";
import { spawnParticleBurst, colorForVfxType } from "../vfx/ParticleBurst";
import type {
  PlacementUnitSnapshot,
  TileEffectSnapshot,
  UnitSnapshot,
  VfxEvent,
} from "../types/contract";

export const HEX_SIZE = 34;

const TILE_FILL = 0x1e293b;
const TILE_STROKE = 0x334155;
const TILE_HOVER = 0x334155;
const SELECTED_RING_COLOR = 0xfacc15;
const LEGAL_TILE_COLOR = 0x4ade80;
const LEGAL_UNIT_RING_COLOR = 0x4ade80;
const STROBE_RING_COLOR = 0xf97316;
// The castable band for the selected ability. Deliberately cooler and fainter than
// LEGAL_TILE_COLOR so "where this reaches" never competes with "what you can click".
const CAST_RANGE_COLOR = 0x38bdf8;
// Frame counts assume Ticker.shared's default ~60fps - matches the
// frame-counting style already used in ParticleBurst.ts rather than
// deltaMS-based timing, for consistency with that existing pattern.
const STROBE_TOGGLE_FRAMES = 9; // ~150ms per on/off half-cycle
const STROBE_TOTAL_FRAMES = 108; // ~1.8s total, within the ~1.5-2s target
const MOVE_TWEEN_FRAMES = 20; // ~330ms at 60fps - short slide, not a full animation set piece

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
  // Persistent tile effects (Ember's burning ground). Its own layer, below units
  // but above the tiles, and untouched by refreshHighlights()'s uiLayer wipe - the
  // same reasoning as strobeLayer.
  readonly tileEffectLayer = new Container();
  readonly unitsLayer = new Container();
  readonly vfxLayer = new Container();
  // Pre-encounter strobe rings live in their own layer, separate from
  // uiLayer, because refreshHighlights() unconditionally clears uiLayer on
  // every store update (selection changes, message log pushes, etc.) - a
  // strobe needs to keep animating across those without being wiped
  // mid-flash by an unrelated state change.
  readonly strobeLayer = new Container();
  readonly uiLayer = new Container();
  // Sits above everything else so a stack picker's icons are always
  // clickable, and is never touched by refreshHighlights()'s uiLayer wipe -
  // same reasoning as strobeLayer, this needs to survive unrelated state
  // updates (e.g. a new snapshot arriving) without being torn down mid-pick,
  // it's only ever rebuilt by renderStackPicker() itself.
  readonly stackPickerLayer = new Container();

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
  // Keyed by unitId (unlike activeStrobeTicks) so a second position update
  // arriving mid-tween can look up and cancel the unit's own in-flight move
  // tween rather than fighting it - see animateUnitMove.
  private activeMoveTicks = new Map<string, () => void>();
  // The latest full unit list (from a real snapshot or a synthesized
  // placement one) - kept so a unit click handler can resolve the *current*
  // UnitSnapshot for its tile instead of the one captured when its container
  // was first created, and so a tile with more than one occupant can be
  // detected at all (see upsertUnit's pointertap handler and
  // renderStackPicker).
  private currentUnits: UnitSnapshot[] = [];
  // Which tile's stack picker (if any) is currently open - a stacked tile
  // (more than one unit sharing a q,r, which only normal per-tile occupancy
  // rules would otherwise prevent - see CLAUDE.md's tile-stacking abilities)
  // hides every occupant but the front-most one from clicks, since Pixi hit
  // testing only ever returns the topmost overlapping display object. This
  // renders a small row of individually-clickable icons above the tile so
  // every occupant becomes reachable.
  private stackPickerTile: AxialCoord | null = null;

  constructor(app: Application, store: GameStateStore, callbacks: BoardCallbacks) {
    this.app = app;
    this.store = store;
    this.callbacks = callbacks;
    this.iconFactory = new UnitIconFactory(app.renderer);
    this.root.addChild(
      this.boardLayer,
      this.tileEffectLayer,
      this.unitsLayer,
      this.vfxLayer,
      this.strobeLayer,
      this.uiLayer,
      this.stackPickerLayer,
    );
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
    for (const tick of this.activeMoveTicks.values()) Ticker.shared.remove(tick);
    this.activeMoveTicks.clear();
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
    g.on("pointertap", () => {
      this.closeStackPicker();
      this.callbacks.onTileClick(coord);
    });
    return g;
  }

  private async applySnapshot(
    snapshot: { mapRadius: number; units: UnitSnapshot[]; tileEffects?: TileEffectSnapshot[] },
  ): Promise<void> {
    this.ensureMap(snapshot.mapRadius);
    this.currentUnits = snapshot.units;
    this.renderTileEffects(snapshot.tileEffects ?? []);

    const seen = new Set<string>();
    for (const unit of snapshot.units) {
      seen.add(unit.id);
      await this.upsertUnit(unit);
    }
    for (const [id, sprite] of this.unitSprites) {
      if (!seen.has(id)) {
        const tick = this.activeMoveTicks.get(id);
        if (tick) {
          Ticker.shared.remove(tick);
          this.activeMoveTicks.delete(id);
        }
        sprite.destroy({ children: true });
        this.unitSprites.delete(id);
      }
    }
    // The stack composition (or its existence at all) may have just changed
    // (a stacked unit died, moved away, etc.) - re-render so a stale picker
    // never lingers, and closes itself automatically once fewer than 2
    // occupants remain.
    this.renderStackPicker();
  }

  /**
   * Redraws every tile effect from scratch rather than diffing - there are only ever a
   * handful, and this mirrors refreshHighlights' own clear-and-redraw approach.
   */
  private renderTileEffects(tileEffects: TileEffectSnapshot[]): void {
    this.tileEffectLayer.removeChildren();
    for (const effect of tileEffects) {
      const { x, y } = axialToPixel({ q: effect.q, r: effect.r }, HEX_SIZE);
      const points = hexPolygonPoints({ x: 0, y: 0 }, HEX_SIZE - 2);
      const glow = new Graphics();
      glow.poly(points).fill({ color: 0xff5722, alpha: 0.28 });
      glow.poly(points).stroke({ width: 2, color: 0xff8a50, alpha: 0.85 });
      glow.position.set(x, y);
      glow.eventMode = "none";
      this.tileEffectLayer.addChild(glow);
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
      // Placement snapshots carry no combat data; 1/0 is the plain melee default
      // rather than a guess, and nothing reads it during this phase anyway.
      attackRange: 1,
      minAttackRange: 0,
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
    // Knowable before the lookup/creation branch below - covers both a
    // unit's very first appearance and a fog-of-war reveal (the opponent's
    // roster only ever appearing once the match actually starts). A reveal
    // shouldn't slide in from some arbitrary prior spot, so this is the flag
    // that decides "place immediately" vs "animate a slide" at the bottom.
    const isNewUnit = !this.unitSprites.has(unit.id);
    let container = this.unitSprites.get(unit.id);
    if (!container) {
      container = new Container();
      container.eventMode = "static";
      container.cursor = "pointer";
      const unitId = unit.id;
      container.on("pointertap", (e) => {
        e.stopPropagation();
        // Resolve the *current* unit and its tile-mates fresh from the last
        // snapshot rather than relying on `unit`, which is only ever the
        // object this container was first created with (a stale reference
        // on every snapshot after the first, since a new UnitSnapshot object
        // arrives every time) - id/team never change so callers relying on
        // just those were unaffected before, but current HP/status wasn't.
        const current = this.currentUnits.find((u) => u.id === unitId);
        if (!current) return;
        const stack = this.currentUnits.filter((u) => !u.dead && u.q === current.q && u.r === current.r);
        if (stack.length > 1) {
          this.toggleStackPicker({ q: current.q, r: current.r });
        } else {
          this.closeStackPicker();
          this.callbacks.onUnitClick(current);
        }
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
    if (isNewUnit) {
      // First appearance / fog-of-war reveal - place immediately, no slide.
      container.position.set(pos.x, pos.y);
    } else if (container.position.x !== pos.x || container.position.y !== pos.y) {
      // Generic "did this unit's position change" check - covers the plain
      // Move ability, Backtrack, Dislocation, Cloak and Dagger's teleport,
      // anything, since they all just result in a different q,r in the next
      // snapshot. No ability-specific special-casing needed.
      this.animateUnitMove(unit.id, container, pos);
    }
    // else: position is unchanged, leave it exactly where it is (including
    // mid-tween, if a tween somehow lands on the same target - nothing to do).
  }

  /**
   * Slides a unit's container from wherever it's currently visually sitting
   * to `target`, using the same frame-counted Ticker.shared pattern
   * strobeSprite already establishes in this file. If a tween for this unit
   * is already in flight (a second position update arrived before the first
   * finished), it's cancelled first and the new tween starts from the
   * sprite's current (mid-tween) position rather than the old target, so the
   * motion reads as continuous instead of snapping or fighting itself.
   */
  private animateUnitMove(unitId: string, container: Container, target: { x: number; y: number }): void {
    const existingTick = this.activeMoveTicks.get(unitId);
    if (existingTick) {
      Ticker.shared.remove(existingTick);
      this.activeMoveTicks.delete(unitId);
    }

    const start = { x: container.position.x, y: container.position.y };
    const dx = target.x - start.x;
    const dy = target.y - start.y;
    let elapsed = 0;

    const tick = () => {
      elapsed += 1;
      const t = Math.min(1, elapsed / MOVE_TWEEN_FRAMES);
      const eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
      container.position.set(start.x + dx * eased, start.y + dy * eased);
      if (t >= 1) {
        Ticker.shared.remove(tick);
        this.activeMoveTicks.delete(unitId);
      }
    };
    this.activeMoveTicks.set(unitId, tick);
    Ticker.shared.add(tick);
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
      this.drawCastRange(state);
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

  /**
   * Outlines how far the selected ability reaches, independent of what happens to be a
   * legal target right now - a player needs to see "this spell covers three tiles" even
   * when nothing is standing in that area. Abilities with an effective range of 0 (pure
   * self-casts) draw nothing.
   */
  private drawCastRange(state: MatchUiState): void {
    const caster = this.currentUnits.find((u) => u.id === state.selectedUnitId);
    if (!caster) return;
    const ability = caster.abilities.find((a) => a.id === state.selectedAbilityId);
    // range < 0 means the ability reaches the whole map (Pylon, Manifestation,
    // Sprout) - a band would just be a blue wash over everything, so skip it and
    // let the legal-target highlights speak for themselves.
    if (!ability || ability.range <= 0) return;

    const origin = { q: caster.q, r: caster.r };
    const minRange = ability.minRange ?? 0;
    for (let q = -this.mapRadius; q <= this.mapRadius; q++) {
      const rMin = Math.max(-this.mapRadius, -q - this.mapRadius);
      const rMax = Math.min(this.mapRadius, -q + this.mapRadius);
      for (let r = rMin; r <= rMax; r++) {
        const distance = hexDistance(origin, { q, r });
        if (distance > ability.range || distance < minRange) continue;
        // The caster's own tile is only worth shading when the ability can actually
        // be aimed there (minRange 0), and even then it already has a selection ring.
        if (distance === 0) continue;
        const center = axialToPixel({ q, r }, HEX_SIZE);
        const band = new Graphics()
          .poly(hexPolygonPoints({ x: 0, y: 0 }, HEX_SIZE - 2))
          .fill({ color: CAST_RANGE_COLOR, alpha: 0.08 })
          .stroke({ width: 1, color: CAST_RANGE_COLOR, alpha: 0.45 });
        band.position.set(center.x, center.y);
        this.uiLayer.addChild(band);
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

  /** Opens the picker for `tile` if it's not already open there, otherwise closes it (a second click dismisses it). */
  private toggleStackPicker(tile: AxialCoord): void {
    if (this.stackPickerTile && this.stackPickerTile.q === tile.q && this.stackPickerTile.r === tile.r) {
      this.stackPickerTile = null;
    } else {
      this.stackPickerTile = tile;
    }
    this.renderStackPicker();
  }

  private closeStackPicker(): void {
    if (!this.stackPickerTile) return;
    this.stackPickerTile = null;
    this.renderStackPicker();
  }

  /**
   * Draws a row of individually-clickable icons, one per unit currently
   * sharing `stackPickerTile`, floating just above that tile - the only way
   * to reach anything but the front-most occupant, since overlapping display
   * objects only ever hit-test to whichever one is on top. Rebuilt from
   * scratch on every call (cheap - at most a handful of icons) rather than
   * diffed, mirroring uiLayer/refreshHighlights' own "clear and redraw"
   * approach elsewhere in this file.
   */
  private renderStackPicker(): void {
    this.stackPickerLayer.removeChildren();
    if (!this.stackPickerTile) return;

    const tile = this.stackPickerTile;
    const units = this.currentUnits.filter((u) => !u.dead && u.q === tile.q && u.r === tile.r);
    if (units.length < 2) {
      // The stack no longer exists (a unit died/moved away since this was
      // opened) - nothing to show, and nothing left to pick between.
      this.stackPickerTile = null;
      return;
    }

    const center = axialToPixel(tile, HEX_SIZE);
    const iconSize = HEX_SIZE * 0.9;
    const spacing = iconSize + 8;
    const startX = center.x - (spacing * (units.length - 1)) / 2;
    const y = center.y - HEX_SIZE * 1.7;

    const panel = new Container();
    panel.addChild(
      new Graphics()
        .roundRect(startX - iconSize / 2 - 8, y - iconSize / 2 - 8, spacing * (units.length - 1) + iconSize + 16, iconSize + 16, 8)
        .fill({ color: 0x0f172a, alpha: 0.92 })
        .stroke({ width: 1, color: 0x334155 }),
    );
    this.stackPickerLayer.addChild(panel);

    void this.populateStackPickerIcons(panel, units, startX, y, spacing, iconSize);
  }

  private async populateStackPickerIcons(
    panel: Container,
    units: UnitSnapshot[],
    startX: number,
    y: number,
    spacing: number,
    iconSize: number,
  ): Promise<void> {
    for (let i = 0; i < units.length; i++) {
      const unit = units[i];
      const x = startX + i * spacing;
      const texture = await this.iconFactory.getTexture(unit.definitionId, unit.team, unit.unitType, unit.name.charAt(0));

      const ring = new Graphics().circle(0, 0, iconSize / 2 + 3).stroke({ width: 2, color: SELECTED_RING_COLOR });
      ring.position.set(x, y);
      panel.addChild(ring);

      const sprite = new Sprite(texture);
      sprite.anchor.set(0.5);
      sprite.width = iconSize;
      sprite.height = iconSize;
      sprite.position.set(x, y);
      sprite.eventMode = "static";
      sprite.cursor = "pointer";
      sprite.on("pointertap", (e) => {
        e.stopPropagation();
        this.closeStackPicker();
        this.callbacks.onUnitClick(unit);
      });
      panel.addChild(sprite);
    }
  }
}
