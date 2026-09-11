// Hex board rendering: layered Containers (board tiles -> units -> vfx -> ui
// overlay), driven by GameStateSnapshot pushed through GameStateStore.

import { Application, Container, Graphics, Sprite, Ticker } from "pixi.js";
import { type AxialCoord, axialToPixel, hexDistance, hexPolygonPoints, mapTiles } from "../hex/HexMath";
import { contentBounds } from "./Camera";
import { CameraController } from "./CameraController";
import { UnitIconFactory } from "../units/UnitIconFactory";
import { GameStateStore, type MatchUiState } from "../state/GameStateStore";
import { spawnParticleBurst, colorForVfxType } from "../vfx/ParticleBurst";
import { spawnDamageIndicator } from "../vfx/DamageIndicator";
import { spawnAttackAnimation } from "../vfx/AttackAnimationPlayer";
import { DisplayedUnitState } from "../vfx/DisplayedUnitState";
import { statusVisualFor } from "../vfx/StatusEffects";
import { spawnUnitStatusOverlay, drawCloakTile, spawnDuelBanners, spawnStaticLink } from "../vfx/StatusEffectPlayer";
import type { IndicatorSpec } from "../vfx/VfxIndicators";
import type { AttackAnimationSpec } from "../vfx/AttackAnimations";
import type {
  GameStateSnapshot,
  PlacementUnitSnapshot,
  TileEffectSnapshot,
  UnitSnapshot,
  UnitType,
  VfxEvent,
} from "../types/contract";

export const HEX_SIZE = 34;

// Unit tokens are drawn a little larger than a hex. The face art inside a token
// is ringed in team and type colours (see UnitIconFactory), and those rings eat
// into the visible art, so this is a touch more generous than the bare
// placeholder badge needed.
const UNIT_SPRITE_SCALE = 1.25;

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
const PLACEMENT_MAP_RADIUS = 7;
const PLACEMENT_MAP_ROW_LIMIT = 5;

/**
 * How each kind of tile effect paints. `kind` is open-ended per API_CONTRACT.md, so an
 * unrecognised one falls back to a neutral overlay instead of breaking the board.
 *
 * The insets follow the file's existing convention (tiles HEX_SIZE-1, highlights -3) so
 * two overlays on one hex stay individually legible. Eclipse is deliberately a different
 * hue and a far lower alpha than CAST_RANGE_COLOR - "a blast is coming here" must not
 * read as "the ability you are aiming reaches here".
 */
const TILE_EFFECT_STYLES: Record<string, {
  fill: number; fillAlpha: number; stroke: number; strokeAlpha: number; inset: number;
  reticle?: boolean; aboveUnits?: boolean;
}> = {
  burning: { fill: 0xff5722, fillAlpha: 0.28, stroke: 0xff8a50, strokeAlpha: 0.85, inset: 2 },
  // Shawl's Acidic Brew. Sickly green, and a touch fainter than burning ground: it does
  // no damage on contact, it only softens whoever is standing in it.
  acid: { fill: 0x84cc16, fillAlpha: 0.24, stroke: 0xa3e635, strokeAlpha: 0.8, inset: 2 },
  eclipse: { fill: 0x93c5fd, fillAlpha: 0.16, stroke: 0xbfdbfe, strokeAlpha: 0.45, inset: 2 },
  missile: { fill: 0xfb923c, fillAlpha: 0.18, stroke: 0xf97316, strokeAlpha: 0.9, inset: 5, reticle: true, aboveUnits: true },
  unknown: { fill: 0x94a3b8, fillAlpha: 0.2, stroke: 0xcbd5e1, strokeAlpha: 0.6, inset: 2 },
};

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
  // Tile effects that mark a unit rather than the ground - a homing missile's impact tile
  // is always the tile its target is standing on, so drawn under unitsLayer the marker
  // would be permanently hidden by the very sprite it is pointing at.
  readonly tileMarkerLayer = new Container();
  // Duel banners / Static Link lightning - visuals spanning two specific
  // units rather than belonging to either one's own token container. Sits
  // above tile markers but below vfx/attack animations, wiped and redrawn
  // every applySnapshot the same way tileEffectLayer is - see
  // renderPairEffects.
  readonly pairEffectLayer = new Container();
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
  // Fallen units, parked off the hex grid in two columns at the board's edges
  // like captured chess pieces. Its own layer so a corpse is never mistaken for
  // a board occupant by anything that walks unitsLayer.
  readonly graveyardLayer = new Container();
  // Floating damage/heal numbers. Topmost of everything: a number that a cast-range
  // band or a stack picker could paint over would defeat the point of drawing it.
  // Like vfxLayer it survives refreshHighlights()'s uiLayer wipe.
  readonly indicatorLayer = new Container();

  private iconFactory: UnitIconFactory;
  // Pan/zoom. Camera state lives here rather than in GameStateStore on
  // purpose: that store is shared with the DOM Hud, and every setState
  // rebuilds uiLayer and the whole sidebar - a pan frame must not do that.
  private cameraController: CameraController;
  private tileGraphics = new Map<string, Graphics>();
  private unitSprites = new Map<string, Container>();
  private mapRadius = -1;
  private mapRowLimit = -1;
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
  // Same reasoning as activeStrobeTicks: a number still floating when the board
  // is torn down would otherwise keep ticking against a destroyed Text.
  private activeIndicatorTicks = new Set<() => void>();
  // Same reasoning again: a slash/arrow/projectile/lightning/beam mid-flight
  // when the board is torn down would otherwise keep ticking against a
  // destroyed Graphics.
  private activeAttackAnimTicks = new Set<() => void>();
  // A unit's currently-running status-effect ticks (stun orbit, smoke
  // emitter, ...), keyed by unit id so upsertUnit's next rebuild can stop the
  // previous ones before starting fresh - otherwise every snapshot would
  // leak one more orphaned ticker callback per active effect. See
  // renderUnitStatusEffects/clearStatusEffectTicks.
  private activeStatusEffectTicks = new Map<string, (() => void)[]>();
  // Static Link's jittering lightning ticks, cleared and rebuilt wholesale
  // each renderPairEffects call (same reasoning as activeStatusEffectTicks,
  // just not keyed per-unit since pairEffectLayer itself is wiped every time).
  private activePairEffectTicks = new Set<() => void>();
  // Each unit's *displayed* hp/dead, separate from the truth in
  // this.currentUnits - lets the HP bar and the graveyard transition lag a
  // lethal hit's own animation/indicator instead of snapping the moment a
  // "state" message lands. See applySnapshot, spawnIndicatorAt.
  private displayedState = new DisplayedUnitState();
  // Watches the canvas host for layout-driven size changes - see the constructor.
  private hostResizeObserver: ResizeObserver | null = null;
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
  // Death order per team. A corpse keeps the slot it first got, so a later
  // casualty is appended below rather than reshuffling the whole column.
  private graveyard = new Map<string, string[]>();

  constructor(app: Application, store: GameStateStore, callbacks: BoardCallbacks) {
    this.app = app;
    this.store = store;
    this.callbacks = callbacks;
    this.iconFactory = new UnitIconFactory(app.renderer);
    this.root.addChild(
      this.boardLayer,
      this.tileEffectLayer,
      this.unitsLayer,
      this.tileMarkerLayer,
      this.pairEffectLayer,
      this.vfxLayer,
      this.strobeLayer,
      this.uiLayer,
      this.stackPickerLayer,
      this.graveyardLayer,
      this.indicatorLayer,
    );

    this.cameraController = new CameraController(app.canvas, {
      getViewport: () => ({ width: this.app.screen.width, height: this.app.screen.height }),
      // Before the first snapshot arrives the board is drawn at the placement
      // defaults, so clamp against those rather than the -1 sentinel.
      getContentBounds: () => contentBounds(
        this.mapRadius < 0 ? PLACEMENT_MAP_RADIUS : this.mapRadius,
        this.mapRowLimit < 0 ? PLACEMENT_MAP_ROW_LIMIT : this.mapRowLimit,
        HEX_SIZE,
      ),
      onChange: (camera) => {
        this.root.position.set(camera.x, camera.y);
        this.root.scale.set(camera.zoom);
      },
    });
    this.app.renderer.on("resize", this.handleViewportResize);
    // Pixi's `resizeTo` only listens to window resize - it installs no
    // ResizeObserver - so a layout change that resizes the canvas host without
    // resizing the window (collapsing the log sidebar, say) would leave the
    // renderer at its old size and every click landing on the wrong hex.
    const canvasHost = app.canvas.parentElement;
    if (canvasHost) {
      this.hostResizeObserver = new ResizeObserver(() => this.app.resize());
      this.hostResizeObserver.observe(canvasHost);
    }
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

  /** Resets the camera to the default view: board centred, unzoomed. */
  recenter(): void {
    this.cameraController.reset();
  }

  /**
   * Re-clamps the camera after the canvas changed size. Deliberately not a
   * recenter - resizing the window shouldn't throw away where the user had
   * panned and zoomed to.
   *
   * Driven off the renderer's own "resize" rather than a window listener: with
   * `resizeTo` the renderer resizes on its own schedule, so a window handler
   * would clamp against a stale app.screen. This also covers the canvas host
   * changing size without the window doing so.
   */
  private handleViewportResize = (): void => {
    this.cameraController.reclamp();
  };

  destroy(): void {
    this.unsubscribe();
    this.app.renderer.off("resize", this.handleViewportResize);
    this.hostResizeObserver?.disconnect();
    this.hostResizeObserver = null;
    this.cameraController.destroy();
    for (const tick of this.activeStrobeTicks) Ticker.shared.remove(tick);
    this.activeStrobeTicks.clear();
    for (const tick of this.activeMoveTicks.values()) Ticker.shared.remove(tick);
    this.activeMoveTicks.clear();
    for (const tick of this.activeIndicatorTicks) Ticker.shared.remove(tick);
    this.activeIndicatorTicks.clear();
    for (const tick of this.activeAttackAnimTicks) Ticker.shared.remove(tick);
    this.activeAttackAnimTicks.clear();
    for (const ticks of this.activeStatusEffectTicks.values()) {
      for (const tick of ticks) Ticker.shared.remove(tick);
    }
    this.activeStatusEffectTicks.clear();
    for (const tick of this.activePairEffectTicks) Ticker.shared.remove(tick);
    this.activePairEffectTicks.clear();
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
   * Floats a batch of damage/heal numbers over their units. Called by
   * MatchScreen on a stagger (see IndicatorScheduler), not straight off the
   * wire, so one call is one readable group.
   *
   * Position comes from the unit's sprite where there is one, so a number
   * follows a unit that's mid-move-tween, and falls back to the last snapshot's
   * hex otherwise (a unit that just died has a sprite parked off-grid in the
   * graveyard, but is still at its old q,r in the pre-update snapshot - vfx
   * always arrives before the state it reflects). A unit that resolves to
   * neither is skipped rather than defaulting to the board origin, which would
   * drop an unattached number in the middle of the map.
   */
  showIndicators(specs: IndicatorSpec[]): void {
    const snapshot = this.store.getState().snapshot;
    // Two hits on one unit in the same group would otherwise print on top of
    // each other, so each gets bumped a line further up.
    const perUnitCount = new Map<string, number>();

    for (const spec of specs) {
      const pos = this.resolveLivePosition(spec.unitId, snapshot);
      if (!pos) continue;

      const stackIndex = perUnitCount.get(spec.unitId) ?? 0;
      perUnitCount.set(spec.unitId, stackIndex + 1);
      this.spawnIndicatorAt(pos, spec, stackIndex);
    }
  }

  /**
   * Shows one indicator at an already-resolved position - used by
   * ScheduleVfxBatch's attack-animation completion callback, where the
   * position was frozen eagerly rather than looked up live (see
   * resolveUnitPosition).
   */
  showIndicatorAt(pos: { x: number; y: number }, spec: IndicatorSpec): void {
    this.spawnIndicatorAt(pos, spec, 0);
  }

  /**
   * Applies the spec's HP delta to the unit's *displayed* state and
   * re-renders its token (HP bar, or the graveyard transition if this is
   * the change that kills it) before spawning the floating number - so the
   * bar/graveyard move and the number that explains it can never land out
   * of step with each other.
   */
  private spawnIndicatorAt(pos: { x: number; y: number }, spec: IndicatorSpec, stackIndex: number): void {
    this.displayedState.applyChange(spec.unitId, spec.hpDelta);
    this.refreshUnitDisplay(spec.unitId);

    const tick = spawnDamageIndicator(this.indicatorLayer, Ticker.shared, {
      x: pos.x,
      // Above the token: the HP bar already occupies the space below it.
      y: pos.y - HEX_SIZE * 0.5,
      text: spec.text,
      color: spec.color,
      kind: spec.kind,
      stackIndex,
      getBoardScale: () => this.root.scale.x,
      onComplete: () => this.activeIndicatorTicks.delete(tick),
    });
    this.activeIndicatorTicks.add(tick);
  }

  /** Registers one more event still to be visually applied to this unit - see ScheduleVfxBatch and DisplayedUnitState. */
  beginPendingHpChange(unitId: string): void {
    this.displayedState.beginPendingChange(unitId);
  }

  /** Resolves a unit's current position from Board's own live store state, for an eager (pre-"state") caller. */
  resolveUnitPosition(unitId: string | null): { x: number; y: number } | null {
    return this.resolveLivePosition(unitId, this.store.getState().snapshot);
  }

  /**
   * Builds a unit's status-effect overlay (stun stars, smoke, ice/shield/snow,
   * etc.) from its current `effects` list, straight off the truth snapshot -
   * unlike HP/death these are persistent state, not something that needs to
   * lag a damage indicator (see DisplayedUnitState). Tile- and pair-level
   * effects (Cloak and Dagger, Duel, Static Link) are handled separately by
   * renderCloakTiles/renderPairEffects, not here.
   */
  private renderUnitStatusEffects(container: Container, unit: UnitSnapshot): void {
    const ticks: (() => void)[] = [];
    const tokenRadiusPx = (HEX_SIZE * UNIT_SPRITE_SCALE) / 2;
    for (const effect of unit.effects) {
      const spec = statusVisualFor(effect);
      if (!spec || spec.mode !== "unit") continue;
      ticks.push(...spawnUnitStatusOverlay(container, Ticker.shared, spec, tokenRadiusPx));
    }
    if (ticks.length > 0) this.activeStatusEffectTicks.set(unit.id, ticks);
  }

  /** Stops and forgets a unit's currently-running status-effect ticks, e.g. before a token rebuild or once it leaves the snapshot. */
  private clearStatusEffectTicks(unitId: string): void {
    const ticks = this.activeStatusEffectTicks.get(unitId);
    if (!ticks) return;
    for (const tick of ticks) Ticker.shared.remove(tick);
    this.activeStatusEffectTicks.delete(unitId);
  }

  /** Re-renders one unit's token from the current truth (position, team, name, ...) but *displayed* hp/dead. */
  private refreshUnitDisplay(unitId: string): void {
    const unit = this.currentUnits.find((u) => u.id === unitId);
    if (!unit) return;
    void this.upsertUnit({
      ...unit,
      currentHp: this.displayedState.hpFor(unitId, unit.currentHp),
      dead: this.displayedState.isDead(unitId),
    });
  }

  /**
   * Resolves a unit's current on-screen position: its live sprite where there
   * is one (so a following animation/indicator tracks a unit mid-move-tween),
   * falling back to the given snapshot's hex otherwise (a unit that just died
   * has a sprite parked off-grid in the graveyard, but is still at its old
   * q,r in the pre-update snapshot). Resolved live, at call time - which may
   * be well after the vfx batch that triggered it arrived, since this is
   * called from staggered/scheduled callbacks - rather than frozen early.
   */
  private resolveLivePosition(
    unitId: string | null,
    snapshot: GameStateSnapshot | null | undefined,
  ): { x: number; y: number } | null {
    if (!unitId) return null;
    const sprite = this.unitSprites.get(unitId);
    if (sprite) return { x: sprite.position.x, y: sprite.position.y };
    const unit = snapshot?.units.find((u) => u.id === unitId);
    return unit ? axialToPixel({ q: unit.q, r: unit.r }, HEX_SIZE) : null;
  }

  /**
   * Plays one attack's travel animation between two already-resolved
   * points, then calls onComplete once every stroke has finished - only
   * then should the caller show the attack's damage/MISS indicator.
   *
   * Takes `from`/`to` directly rather than resolving them itself: the
   * caller (ScheduleVfxBatch) resolves both eagerly, synchronously, at the
   * moment the vfx batch arrives - before this action's "state" message can
   * possibly be processed - so a lethal hit's animation still travels to
   * the tile the defender was actually standing on, not the graveyard slot
   * it gets relocated to the instant "state" lands.
   */
  playAttackAnimation(
    from: { x: number; y: number },
    to: { x: number; y: number },
    spec: AttackAnimationSpec,
    onComplete: () => void,
  ): void {
    let ticks: (() => void)[] = [];
    ticks = spawnAttackAnimation(this.vfxLayer, Ticker.shared, from, to, spec, () => {
      for (const tick of ticks) this.activeAttackAnimTicks.delete(tick);
      onComplete();
    });
    for (const tick of ticks) this.activeAttackAnimTicks.add(tick);
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
  /**
   * Composes and caches the tokens for a roster before the board is asked to
   * draw it - see UnitIconFactory.preload. Safe to call repeatedly: everything
   * after the first call is a cache hit.
   */
  preloadArt(units: { definitionId: string; name: string; unitType: UnitType }[]): void {
    // Warming art must never be able to break a match - swallow anything that
    // escapes the factory's own per-file error handling.
    void this.iconFactory.preload(units).catch(() => {});
  }

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

  private ensureMap(radius: number, rowLimit: number): void {
    // Both halves of the shape are in the cache key - a board that changed only its row
    // trim would otherwise keep the stale tiles.
    if (radius === this.mapRadius && rowLimit === this.mapRowLimit) return;
    this.mapRadius = radius;
    this.mapRowLimit = rowLimit;
    this.graveyard.clear();
    this.graveyardLayer.removeChildren();
    this.boardLayer.removeChildren();
    this.tileGraphics.clear();

    for (const coord of mapTiles(radius, rowLimit)) {
      const tile = this.drawTile(coord);
      this.tileGraphics.set(`${coord.q},${coord.r}`, tile);
      this.boardLayer.addChild(tile);
    }

    // The board just changed shape, so the pan clamp's content bounds did too.
    this.cameraController.reclamp();
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
      if (this.cameraController.shouldSuppressTap()) return;
      this.closeStackPicker();
      this.callbacks.onTileClick(coord);
    });
    return g;
  }

  private async applySnapshot(
    snapshot: {
      mapRadius: number;
      mapRowLimit?: number;
      units: UnitSnapshot[];
      tileEffects?: TileEffectSnapshot[];
    },
  ): Promise<void> {
    this.ensureMap(snapshot.mapRadius, snapshot.mapRowLimit ?? snapshot.mapRadius);
    this.currentUnits = snapshot.units;
    this.renderTileEffects(snapshot.tileEffects ?? []);
    this.renderCloakTiles(snapshot.units);

    const seen = new Set<string>();
    for (const unit of snapshot.units) {
      seen.add(unit.id);
      // Holds displayed hp/dead at their last-shown value while something is
      // still pending for this unit, rather than jumping straight to a
      // lethal hit's post-battle result before its own animation/indicator
      // has played - see DisplayedUnitState and ScheduleVfxBatch.
      this.displayedState.syncToTruth(unit.id, unit.currentHp, unit.dead);
      await this.upsertUnit({
        ...unit,
        currentHp: this.displayedState.hpFor(unit.id, unit.currentHp),
        dead: this.displayedState.isDead(unit.id),
      });
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
        this.displayedState.forget(id);
        this.clearStatusEffectTicks(id);
      }
    }
    // The stack composition (or its existence at all) may have just changed
    // (a stacked unit died, moved away, etc.) - re-render so a stale picker
    // never lingers, and closes itself automatically once fewer than 2
    // occupants remain.
    this.renderStackPicker();
    await this.renderPairEffects(snapshot.units);
  }

  /**
   * Cloak and Dagger isn't a real TileEffectSnapshot on the wire (CloakEffect
   * is a unit-attached Effect on its caster, never added to
   * GameStateSnapshotMapper's tileEffects) - so it's synthesized here from
   * whichever units currently carry that effect, drawn at their own q,r.
   * Shares tileEffectLayer's per-snapshot wipe (renderTileEffects clears it
   * just before this runs), so no separate teardown bookkeeping is needed -
   * a caster who loses the effect simply stops being drawn next snapshot.
   */
  private renderCloakTiles(units: UnitSnapshot[]): void {
    for (const unit of units) {
      const hasCloak = unit.effects.some((e) => statusVisualFor(e)?.mode === "tile");
      if (!hasCloak) continue;
      drawCloakTile(this.tileEffectLayer, axialToPixel({ q: unit.q, r: unit.r }, HEX_SIZE), HEX_SIZE);
    }
  }

  /**
   * Duel banners / Static Link lightning - visuals spanning two specific
   * units, resolved via the new partnerUnitId field rather than a same-name
   * pairing guess (see EffectSnapshot). Redrawn from scratch every snapshot,
   * same as renderTileEffects.
   *
   * Duel is symmetric - each duelist holds its own DuelEffect pointing at
   * the other, so both sides carry a "Duel" entry and this dedupes by only
   * drawing when the current unit's id sorts before its partner's. Static
   * Link is not: only the caster's Effect is ever added
   * (GameStateSnapshotMapper resolves its partnerUnitId from
   * StaticLinkEffect.getTarget(), the target never gets a "Static Link"
   * entry of its own) - the same id-order check would wrongly skip it
   * whenever the caster's id happens to sort after its target's, so
   * lightning always draws instead.
   */
  private async renderPairEffects(units: UnitSnapshot[]): Promise<void> {
    for (const tick of this.activePairEffectTicks) Ticker.shared.remove(tick);
    this.activePairEffectTicks.clear();
    this.pairEffectLayer.removeChildren();

    for (const unit of units) {
      for (const effect of unit.effects) {
        const spec = statusVisualFor(effect);
        if (!spec || spec.mode !== "pair" || !effect.partnerUnitId) continue;
        if (spec.kind === "banner" && unit.id >= effect.partnerUnitId) continue;
        const from = this.resolveUnitPosition(unit.id);
        const to = this.resolveUnitPosition(effect.partnerUnitId);
        if (!from || !to) continue;
        if (spec.kind === "banner") {
          await spawnDuelBanners(this.pairEffectLayer, from, to);
        } else {
          this.activePairEffectTicks.add(spawnStaticLink(this.pairEffectLayer, Ticker.shared, from, to, spec.color));
        }
      }
    }
  }

  /**
   * Redraws every tile effect from scratch rather than diffing - there are only ever a
   * handful, and this mirrors refreshHighlights' own clear-and-redraw approach. Redrawing
   * from the snapshot every push is also what makes a missile marker follow its target
   * around for free: the server recomputes the impact tile from wherever the victim now is.
   */
  private renderTileEffects(tileEffects: TileEffectSnapshot[]): void {
    this.tileEffectLayer.removeChildren();
    this.tileMarkerLayer.removeChildren();
    for (const effect of tileEffects) {
      const { x, y } = axialToPixel({ q: effect.q, r: effect.r }, HEX_SIZE);
      const style = TILE_EFFECT_STYLES[effect.kind] ?? TILE_EFFECT_STYLES.unknown;
      const points = hexPolygonPoints({ x: 0, y: 0 }, HEX_SIZE - style.inset);
      const glow = new Graphics();
      glow.poly(points).fill({ color: style.fill, alpha: style.fillAlpha });
      glow.poly(points).stroke({ width: 2, color: style.stroke, alpha: style.strokeAlpha });
      if (style.reticle) {
        // A crosshair rather than more shading: this marks where something is about to
        // land, and needs to read as a target rather than as ground the tile has become.
        const arm = HEX_SIZE * 0.34;
        glow.moveTo(-arm, 0).lineTo(arm, 0).moveTo(0, -arm).lineTo(0, arm)
          .stroke({ width: 2, color: style.stroke, alpha: style.strokeAlpha });
        glow.circle(0, 0, arm * 0.55).stroke({ width: 2, color: style.stroke, alpha: style.strokeAlpha });
      }
      glow.position.set(x, y);
      glow.eventMode = "none";
      (style.aboveUnits ? this.tileMarkerLayer : this.tileEffectLayer).addChild(glow);
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
    void this.applySnapshot({
      mapRadius: PLACEMENT_MAP_RADIUS,
      mapRowLimit: PLACEMENT_MAP_ROW_LIMIT,
      units: synthetic,
    });
  }

  /**
   * Parks a fallen unit in its team's column at the board's edge, the way captured
   * chess pieces sit on the border - off the hex grid entirely, so it can't be
   * clicked instead of the tile beneath it or counted as an occupant of it.
   *
   * The engine keeps dead units in the roster (their position field is never
   * cleared, a documented gotcha), so they keep arriving in every snapshot with
   * their last q,r - which is exactly why the client has to move them rather than
   * simply trusting the coordinates. Note only roster units ever reach here: a
   * dead summon leaves GameState's registry entirely and just disappears.
   */
  private placeInGraveyard(unit: UnitSnapshot, container: Container): void {
    if (container.parent !== this.graveyardLayer) {
      this.graveyardLayer.addChild(container);
    }
    const column = this.graveyard.get(unit.team) ?? [];
    if (!column.includes(unit.id)) {
      column.push(unit.id);
      this.graveyard.set(unit.team, column);
    }

    // One column just outside each end of the hex grid. The root is centred on
    // screen and hexes live in local coordinates around the origin, so a constant
    // derived from the map width lands cleanly on the border.
    const edge = (this.mapRadius + 1.6) * HEX_SIZE * 1.5;
    const slot = column.indexOf(unit.id);
    const columnTop = -(this.mapRowLimit + 0.5) * HEX_SIZE * 1.732;
    container.position.set(
      unit.team === "PLAYER_ONE" ? -edge : edge,
      columnTop + slot * HEX_SIZE * 0.8,
    );

    // No slide into the tray: a corpse crossing the whole board would read as a
    // move. Cancel any tween still in flight from its last living step.
    const tick = this.activeMoveTicks.get(unit.id);
    if (tick) {
      Ticker.shared.remove(tick);
      this.activeMoveTicks.delete(unit.id);
    }
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
        // The tap that ends a pan is not a board action - see
        // CameraController.shouldSuppressTap.
        if (this.cameraController.shouldSuppressTap()) return;
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
    this.clearStatusEffectTicks(unit.id);

    const texture = await this.iconFactory.getTexture(
      unit.definitionId,
      unit.team,
      unit.unitType,
      unit.name.charAt(0),
      unit.name,
    );
    const sprite = new Sprite(texture);
    sprite.anchor.set(0.5);
    // Shrink Ray's applied effect ("Shrunk") shrinks the token itself rather
    // than drawing an overlay - see StatusEffects.ts's "shrink" kind.
    const shrinkScale = unit.effects.some((e) => statusVisualFor(e)?.kind === "shrink") ? 0.8 : 1;
    sprite.width = HEX_SIZE * UNIT_SPRITE_SCALE * shrinkScale;
    sprite.height = HEX_SIZE * UNIT_SPRITE_SCALE * shrinkScale;
    container.addChild(sprite);

    if (unit.dead) {
      // A corpse gets no HP bar - it would read as a live unit at 0 health - and is
      // shrunk so a long column still fits beside the board. It stays interactive:
      // the pointertap handler above still routes it to the sidebar for inspection.
      sprite.width = HEX_SIZE * 0.7;
      sprite.height = HEX_SIZE * 0.7;
      container.alpha = 0.45;
      this.placeInGraveyard(unit, container);
      return;
    }

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

    container.alpha = 1;
    this.renderUnitStatusEffects(container, unit);

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

      // A two-part ability highlights one stage at a time: the units that can be moved,
      // then - once one is chosen - only that unit's own destinations. Showing every
      // pair at once would light up most of the board and mean nothing.
      if (legal.multi) {
        // The key the server grouped the second halves under: a unit id, or a "q,r" tile key.
        // Built here rather than parsed - see the contract, it is meant to be opaque.
        const primaryKey =
          state.multiPrimaryUnitId ??
          (state.multiPrimaryTile ? `${state.multiPrimaryTile.q},${state.multiPrimaryTile.r}` : null);

        if (primaryKey) {
          for (const tile of legal.multi.destinationsByPrimary[primaryKey] ?? []) {
            this.highlightTile(tile.q, tile.r);
          }
          if (state.multiPrimaryUnitId) {
            this.ringUnit(state.multiPrimaryUnitId, SELECTED_RING_COLOR);
          } else if (state.multiPrimaryTile) {
            this.highlightTile(state.multiPrimaryTile.q, state.multiPrimaryTile.r, SELECTED_RING_COLOR);
          }
        } else {
          for (const unitId of legal.multi.primaryUnitIds) {
            this.ringUnit(unitId, LEGAL_UNIT_RING_COLOR);
          }
          for (const tile of legal.multi.primaryTiles ?? []) {
            this.highlightTile(tile.q, tile.r);
          }
        }
        return;
      }

      for (const tile of legal.tiles) {
        this.highlightTile(tile.q, tile.r);
      }
      for (const unitId of legal.unitIds) {
        this.ringUnit(unitId, LEGAL_UNIT_RING_COLOR);
      }
    }
  }

  /** Ring around a unit's sprite; a no-op if that unit isn't currently rendered. */
  private ringUnit(unitId: string, color: number): void {
    const sprite = this.unitSprites.get(unitId);
    if (!sprite) return;
    const ring = new Graphics().circle(0, 0, HEX_SIZE * 0.75).stroke({ width: 3, color });
    ring.position.copyFrom(sprite.position);
    this.uiLayer.addChild(ring);
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
    for (const coord of mapTiles(this.mapRadius, this.mapRowLimit)) {
      const distance = hexDistance(origin, coord);
      if (distance > ability.range || distance < minRange) continue;
      // The caster's own tile is only worth shading when the ability can actually
      // be aimed there (minRange 0), and even then it already has a selection ring.
      if (distance === 0) continue;
      const center = axialToPixel(coord, HEX_SIZE);
      const band = new Graphics()
        .poly(hexPolygonPoints({ x: 0, y: 0 }, HEX_SIZE - 2))
        .fill({ color: CAST_RANGE_COLOR, alpha: 0.08 })
        .stroke({ width: 1, color: CAST_RANGE_COLOR, alpha: 0.45 });
      band.position.set(center.x, center.y);
      this.uiLayer.addChild(band);
    }
  }

  /** `color` marks a tile as something other than a plain legal target - the first half of a
   * two-tile cast is drawn in the selection colour so the player can see what they have picked. */
  private highlightTile(q: number, r: number, color: number = LEGAL_TILE_COLOR): void {
    const center = axialToPixel({ q, r }, HEX_SIZE);
    const points = hexPolygonPoints({ x: 0, y: 0 }, HEX_SIZE - 3);
    const highlight = new Graphics()
      .poly(points)
      .fill({ color, alpha: 0.25 })
      .stroke({ width: 2, color, alpha: 0.8 });
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
      const texture = await this.iconFactory.getTexture(
        unit.definitionId, unit.team, unit.unitType, unit.name.charAt(0), unit.name);

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
        if (this.cameraController.shouldSuppressTap()) return;
        e.stopPropagation();
        this.closeStackPicker();
        this.callbacks.onUnitClick(unit);
      });
      panel.addChild(sprite);
    }
  }
}
