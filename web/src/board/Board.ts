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
import { spawnAttackAnimation, buildMissileIcon } from "../vfx/AttackAnimationPlayer";
import { spawnFloatingIcon, type FloatingIconKind } from "../vfx/FloatingIcon";
import { safeTick } from "../vfx/SafeTick";
import { DisplayedUnitState } from "../vfx/DisplayedUnitState";
import { statusVisualFor } from "../vfx/StatusEffects";
import { spawnUnitStatusOverlay, drawCloakTile, spawnDuelBanners, spawnStaticLink } from "../vfx/StatusEffectPlayer";
import type { IndicatorSpec } from "../vfx/VfxIndicators";
import { castAnimationFor, type AttackAnimationSpec } from "../vfx/AttackAnimations";
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
// Backtrack's afterimage trail: dark-purple translucent circles, same size as
// the unit's own token border, dropped every few frames along the slide.
const BACKTRACK_TRAIL_INTERVAL_FRAMES = 4;
const BACKTRACK_TRAIL_LIFE_FRAMES = 18;
const BACKTRACK_TRAIL_COLOR = 0x5b21b6;
const BACKTRACK_TRAIL_ALPHA = 0.35;
// Dislocation's teleport: shrink to nothing at the old tile, jump, grow back
// from nothing at the new one - 0.6s each way, per temp/abilities2.txt.
const TELEPORT_PHASE_FRAMES = 36;
// Eruption's cast burst: red particles bursting upward off a newly-ignited tile.
const ERUPTION_BURST_COLOR = 0xff5722;
// Overheat's proc burst, on the unit that just overheated.
const OVERHEAT_BURST_COLOR = 0xfb923c;
// Pylon's cast: a small dark-blue orb drops from above the map onto the target
// tile before the pylon itself is revealed. Offset is plain pixel math, not a
// real map position - see the sky-drop origin comment in ScheduleVfxBatch.ts.
const PYLON_SKY_OFFSET_PX = 600;
const PYLON_ORB_RADIUS_PX = 7;
const PYLON_ORB_COLOR = 0x1e3a8a;
const PYLON_DROP_FRAMES = 30; // ~0.5s
// Mimic's cast: particles travel from the copied unit to Joker.
const MIMIC_PARTICLE_COUNT = 16;
const MIMIC_PARTICLE_LIFE_FRAMES = 30;
const MIMIC_PARTICLE_COLORS = [0x166534, 0xd8b4fe];
// Soul Rip/Decay: same converging-particle shape as Mimic, brown/red, drained
// toward whichever unit is doing the draining - see spawnConvergingParticles.
// Brighter/bigger/slightly longer-lived than Mimic's own particles (rather than
// reusing its exact look) so this reads clearly against a dark board instead of
// blending into the similarly-coloured generic damage burst that plays alongside it.
const SOUL_RIP_PARTICLE_COLORS = [0xd97706, 0xf87171];
const SOUL_RIP_PARTICLE_COUNT = 16;
const DECAY_PARTICLE_COUNT = 8; // ~50% of Soul Rip's, per temp/abilities.txt
const SOUL_RIP_PARTICLE_LIFE_FRAMES = 40; // ~0.65s - a touch longer than Mimic's 30
const SOUL_RIP_PARTICLE_RADIUS_PX = 4;
// Overwhelming Odds' cast and Pylon Collapse's death burst are both a single
// hollow pulse growing from 0 to a fixed radius - see spawnOneShotPulse.
const OVERWHELMING_ODDS_PULSE_RADIUS_PX = HEX_SIZE * 3; // matches the ability's cast radius closely enough - hardcoded per temp/abilities2.txt
const OVERWHELMING_ODDS_PULSE_FRAMES = 24; // ~0.4s
const OVERWHELMING_ODDS_COLOR = 0xf97316;
const PYLON_COLLAPSE_PULSE_RADIUS_PX = HEX_SIZE; // 1 tile radius
const PYLON_COLLAPSE_PULSE_FRAMES = 24; // ~0.4s
const PYLON_COLLAPSE_COLOR = 0x1e3a8a;
// Sanity's Eclipse: a charging orb hooked to the caster, then a detonation
// flight + pulses at the (centroid of the) affected tile(s).
const SANITY_ECLIPSE_COLOR = 0x7dd3fc;
const SANITY_ECLIPSE_ORB_RADIUS_PX = 8;
const SANITY_ECLIPSE_ORB_Y_OFFSET_PX = HEX_SIZE * 0.65; // just below the top of the portrait circle
const SANITY_ECLIPSE_PARTICLE_INTERVAL_FRAMES = 6;
const SANITY_ECLIPSE_PARTICLE_SPAWN_RADIUS_PX = 40;
const SANITY_ECLIPSE_PARTICLE_LIFE_FRAMES = 24;
const SANITY_ECLIPSE_DETONATE_RADIUS_PX = HEX_SIZE * 1.5;
const SANITY_ECLIPSE_FLY_FRAMES = 24; // ~0.4s
const SANITY_ECLIPSE_PULSE_COUNT = 3;
const SANITY_ECLIPSE_PULSE_TOTAL_FRAMES = 42; // ~0.7s
// Sprout/Overgrowth's cast: a filled circle growing from nothing to a fixed
// radius/full opacity together (same progress value drives both, not
// independently eased) over ~1.5s, then the Branchling(s) appear - see
// spawnGrowingFilledCircle and applySnapshot's held-back-summon handling.
const BRANCH_SUMMON_CIRCLE_COLOR = 0x86efac;
const BRANCH_SUMMON_CIRCLE_FRAMES = 90; // ~1.5s per temp/abilities.txt
// 70% of a tile's area: area scales with radius^2, so sqrt(0.7) of HEX_SIZE.
const SPROUT_CIRCLE_RADIUS_PX = HEX_SIZE * Math.sqrt(0.7);
// "Much larger... centered around the center tile" - sized to roughly cover the
// centre tile plus the ring of 6 tiles Overgrowth actually plants on.
const OVERGROWTH_CIRCLE_RADIUS_PX = HEX_SIZE * 2.4;
// Eureka's bulb / Reload's gear: a floating icon at the caster's portrait, per
// temp/abilities.txt - see spawnFloatingIconAt/FloatingIcon.ts.
const EUREKA_BULB_COLOR = 0xfacc15;
const RELOAD_GEAR_COLOR = 0xf1f5f9;
// Nanobots' cast: ~10 small blue projectiles converging caster->target, same
// spawnConvergingParticles shape as Soul Rip, just the opposite direction.
const NANOBOTS_CAST_COLOR = 0x60a5fa;
const NANOBOTS_PARTICLE_COUNT = 10;
// Shrink Ray's cast: a plain one-shot light-blue beam, no damage event to key off.
const SHRINK_RAY_BEAM_COLOR = 0x38bdf8;
const SHRINK_RAY_BEAM_FRAMES = 60; // ~1s
// Homing Missile's cast-phase launch: flies straight up off-screen and despawns, before the
// (turns-later) impact-phase projectile in AttackAnimations.ts's ABILITY_DAMAGE_ANIMATION_BY_
// CAUSE_LABEL. Purely cosmetic - nothing downstream needs to sync with it.
const HOMING_MISSILE_COLOR = 0xe2e8f0;
const HOMING_MISSILE_LAUNCH_OFFSET_PX = 500;
const HOMING_MISSILE_LAUNCH_FRAMES = 36; // ~0.6s
// Translocation's cast: a growing light-blue circle at the destination tile before the
// relocated unit's sprite reveals there - see translocationsAwaitingMove/translocationsRevealing.
const TRANSLOCATION_CIRCLE_COLOR = 0x38bdf8;
const TRANSLOCATION_CIRCLE_RADIUS_PX = HEX_SIZE * 0.9;
const TRANSLOCATION_CIRCLE_FRAMES = BRANCH_SUMMON_CIRCLE_FRAMES; // ~1.5s, same cadence as Sprout/Overgrowth
// Acidic Brew's landing pulse - same sickly green as TILE_EFFECT_STYLES.acid's stroke, sized
// to comfortably cover the upgrade's 1-tile ring and noticeably slower than the "fast" pulses
// elsewhere (Overwhelming Odds/Pylon Collapse/Homing Missile), per temp/abilities.txt's
// "a single slow pulse".
const ACID_PULSE_COLOR = 0xa3e635;
const ACID_PULSE_RADIUS_PX = HEX_SIZE * 2.2;
const ACID_PULSE_FRAMES = 50; // ~0.83s
const WHITE = 0xffffff;
// Blizzard's cast: a flurry of white "snowball" particles, staggered rather than all at once
// and starting anywhere within the caster's portrait, per temp/abilities.txt.
const BLIZZARD_SNOWBALL_COLOR = WHITE;
const BLIZZARD_SNOWBALL_COUNT = 9;
const BLIZZARD_SNOWBALL_INTERVAL_MS = 90;
// Snow Golem's cast: a growing white circle at the destination tile before each golem reveals -
// same cadence as Sprout/Overgrowth, but per-golem rather than one shared circle (see
// applySnapshot), so the upgrade's pair can play "at the same time" per temp/abilities.txt.
const SNOW_GOLEM_CIRCLE_COLOR = WHITE;
const SNOW_GOLEM_CIRCLE_RADIUS_PX = HEX_SIZE * 0.9;
const SNOW_GOLEM_CIRCLE_FRAMES = BRANCH_SUMMON_CIRCLE_FRAMES;
// Snow Blast (the golem's own death effect): 5 white pulses covering its 1-tile splash
// (SnowBlast.java's default radius), per temp/abilities.txt.
const SNOW_BLAST_PULSE_RADIUS_PX = HEX_SIZE * 2;
const SNOW_BLAST_PULSE_COUNT = 5;
const SNOW_BLAST_PULSE_TOTAL_FRAMES = 45; // ~0.75s, ~5 pulses at Homing Missile's own ~80ms-per-pulse cadence

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
  // One-shot vfx tickers not otherwise owned by a unit id (Pylon's sky-drop,
  // Backtrack's trail ghosts, Mimic's particles, Overwhelming Odds'/Pylon
  // Collapse's pulses, ...) - every one is also wrapped in safeTick, but this
  // set is what lets destroy() actually cancel them at match end rather than
  // leaving them to fire against a torn-down vfxLayer.
  private activeVfxOneShotTicks = new Set<() => void>();
  // Sanity's Eclipse's charging orb, one per caster currently charging - keyed
  // so a recast (or a defensive re-check against the caster's own effects
  // list) can find and replace/stop the right one. See startSanityEclipseCharge.
  private sanityEclipseCharges = new Map<string, { orb: Graphics; tick: () => void }>();
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
  // Set by playVfx when an ability_used event needs its own move-animation
  // treatment (Backtrack's trail, Dislocation's shrink/grow) instead of the
  // plain slide. Consumed - and cleared, whether or not a move actually
  // follows - by the very next upsertUnit call for that unit, so a later
  // unrelated Move can never reuse a stale flag.
  private pendingMoveAnimationByUnit = new Map<string, "backtrack-trail" | "teleport">();
  // Set by playVfx on a sprout/overgrowth ability_used event, consumed by the very
  // next applySnapshot that sees new Branchling unit(s) appear - see the FIFO
  // matching in applySnapshot. Neither ability's cast event carries a tile
  // position (they target an empty tile), so the circle's position and the
  // decision of which new Branchling(s) belong to which cast can only be
  // resolved once the summon(s) actually land in the following snapshot.
  private pendingBranchSummonReveals: Array<"sprout" | "overgrowth"> = [];
  // Branchling ids currently held back from the normal instant-appear path pending their
  // growing-circle cast VFX (see applySnapshot) - persistent across calls, since further
  // "state" pushes commonly arrive mid-circle (Branch's own turn often isn't over yet).
  private branchlingsAwaitingReveal = new Set<string>();
  // Set by playVfx on a translocation ability_used event (targetUnitId is the unit actually
  // being relocated - see VfxCollector.java's MultiTarget handling), waiting for the snapshot
  // where that unit's q,r actually changes - the event carries no destination tile of its own
  // (MultiTarget's secondary half never reaches the wire), so where to grow the circle can
  // only be read off the unit's own new position once it lands.
  private translocationsAwaitingMove = new Set<string>();
  // Units whose growing-circle cast VFX is currently playing at their destination - held back
  // from the normal slide-to-new-position path in applySnapshot's per-unit loop until it
  // finishes, same role as branchlingsAwaitingReveal.
  private translocationsRevealing = new Set<string>();
  // Set by playVfx on an acidic_brew ability_used event, consumed the next time one or more
  // new "acid" tile effects appear (see applySnapshot) - the cast's TileTarget never reaches
  // the wire (VfxCollector.java has no case for it), so like Sprout/Overgrowth the destination
  // can only be resolved once the tile(s) actually land.
  private pendingAcidicBrewCasts: string[] = [];
  // Tile keys ("q,r") currently held back from renderTileEffects while their throw+pulse cast
  // VFX plays - same "hold back, reveal on completion" role as branchlingsAwaitingReveal, just
  // for tiles instead of units.
  private acidTilesAwaitingReveal = new Set<string>();
  // Set by playVfx on a snow_golem ability_used event (the caster's id - both the base cast
  // and the upgrade's two-tile cast target bare tiles, never a unit, so the event carries no
  // destination either), consumed the next time one or more new "Snow Golem"-named units
  // appear. Unlike Sprout/Overgrowth's single shared circle, each claimed golem gets its own
  // independent throw+circle+reveal - see applySnapshot.
  private pendingSnowGolemCasts: string[] = [];
  // Golem ids currently held back pending their own throw+circle cast VFX - same role as
  // branchlingsAwaitingReveal, just resolved per-unit instead of merged into one circle.
  private snowGolemsAwaitingReveal = new Set<string>();
  // Per-unit render counter, bumped at the start of every upsertUnit call for that unit id and
  // checked again after its await returns - guards against two overlapping applySnapshot calls
  // (the store subscription fires one per "state" push with no serialization) racing to render
  // the same unit: a slower, superseded call would otherwise resume after a newer one has
  // already rebuilt this unit's overlay, unconditionally overwrite activeStatusEffectTicks'
  // entry with its own (stale) ticks, and silently orphan the newer call's emitter tick with
  // nothing left able to find and stop it - the root cause of status particles (Nanobots,
  // Frostbite, ...) sometimes lingering past their effect's actual expiry.
  private unitRenderGeneration = new Map<string, number>();
  // The previous applySnapshot's tile effects, kept only to diff against the
  // new ones - a newly-appearing "burning" entry is what triggers Eruption's
  // cast burst, since neither its ability_used event nor any damage event
  // carries the tile it was cast on (see ScheduleVfxBatch.ts's contract notes).
  private previousTileEffects: TileEffectSnapshot[] = [];

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
    for (const tick of this.activeVfxOneShotTicks) Ticker.shared.remove(tick);
    this.activeVfxOneShotTicks.clear();
    for (const charge of this.sanityEclipseCharges.values()) Ticker.shared.remove(charge.tick);
    this.sanityEclipseCharges.clear();
    this.root.destroy({ children: true });
  }

  /** Plays a generic burst at the position of a unit from the *pre-update* snapshot (vfx arrives before state). */
  playVfx(events: VfxEvent[]): void {
    const snapshot = this.store.getState().snapshot;
    // Pylon Collapse fires one damage event per adjacent enemy hit, all sharing
    // the same dying pylon as sourceUnitId - the collapse pulse itself plays
    // once per pylon, not once per victim.
    const pylonCollapseBurstFor = new Set<string>();
    // Sanity's Eclipse's detonation likewise fans out to one damage event per
    // victim, all sharing the detonating caster as sourceUnitId - collected
    // here and resolved once, after the loop, into a single flight+pulse at
    // the centroid of every victim hit (see detonateSanityEclipse).
    const sanityEclipseTargetsBySource = new Map<string, { x: number; y: number }[]>();
    for (const event of events) {
      if (event.type === "ability_used" && event.sourceUnitId) {
        if (event.abilityId === "backtrack") {
          this.pendingMoveAnimationByUnit.set(event.sourceUnitId, "backtrack-trail");
        } else if (event.abilityId === "dislocation") {
          this.pendingMoveAnimationByUnit.set(event.sourceUnitId, "teleport");
        } else if (event.abilityId === "mimic" && event.targetUnitId) {
          this.spawnMimicParticles(event.targetUnitId, event.sourceUnitId);
        } else if (event.abilityId === "overwhelming_odds") {
          const pos = this.resolveUnitPosition(event.sourceUnitId);
          if (pos) this.spawnOneShotPulse(pos, OVERWHELMING_ODDS_PULSE_RADIUS_PX, OVERWHELMING_ODDS_COLOR, OVERWHELMING_ODDS_PULSE_FRAMES);
        } else if (event.abilityId === "sanity_s_eclipse") {
          // Identifiers.normalize("Sanity's Eclipse") drops the apostrophe as a
          // separator rather than treating it as a word boundary on its own -
          // "sanity_s_eclipse", not "sanity_eclipse".
          this.startSanityEclipseCharge(event.sourceUnitId);
        } else if (event.abilityId === "soul_rip" && event.targetUnitId) {
          this.spawnSoulRip(event.sourceUnitId, event.targetUnitId);
        } else if (event.abilityId === "sprout") {
          this.pendingBranchSummonReveals.push("sprout");
        } else if (event.abilityId === "overgrowth") {
          this.pendingBranchSummonReveals.push("overgrowth");
        } else if (event.abilityId === "eureka") {
          const pos = this.resolveUnitPosition(event.sourceUnitId);
          if (pos) this.spawnFloatingIconAt(pos, "bulb", EUREKA_BULB_COLOR);
        } else if (event.abilityId === "reload") {
          const pos = this.resolveUnitPosition(event.sourceUnitId);
          if (pos) this.spawnFloatingIconAt(pos, "gear", RELOAD_GEAR_COLOR);
        } else if (event.abilityId === "nanobots" && event.targetUnitId) {
          this.spawnConvergingParticles(event.sourceUnitId, event.targetUnitId, [NANOBOTS_CAST_COLOR], NANOBOTS_PARTICLE_COUNT);
        } else if (event.abilityId === "shrink_ray" && event.targetUnitId) {
          const from = this.resolveUnitPosition(event.sourceUnitId);
          const to = this.resolveUnitPosition(event.targetUnitId);
          if (from && to) this.spawnOneShotBeam(from, to, SHRINK_RAY_BEAM_COLOR, SHRINK_RAY_BEAM_FRAMES);
        } else if (event.abilityId === "homing_missile") {
          this.spawnHomingMissileLaunch(event.sourceUnitId);
        } else if (event.abilityId === "translocation" && event.targetUnitId) {
          this.translocationsAwaitingMove.add(event.targetUnitId);
        } else if (event.abilityId === "poison_bloom" && event.targetUnitId) {
          // No DamageEvent at cast time (the poison itself ticks later under causeLabel
          // "Poison") - dispatched directly here rather than through the damage-triggered
          // pipeline in ScheduleVfxBatch, via the dedicated cast-only animation table (see
          // CAST_ANIMATION_BY_ABILITY_ID's own doc comment for why it's kept separate).
          const from = this.resolveUnitPosition(event.sourceUnitId);
          const to = this.resolveUnitPosition(event.targetUnitId);
          const spec = castAnimationFor(event.abilityId);
          if (from && to && spec) this.playAttackAnimation(from, to, spec, () => {});
        } else if (event.abilityId === "hidden_potential" && event.targetUnitId) {
          // Same direct-dispatch shape as Poison Bloom - HiddenPotential.onUse fires no
          // DamageEvent either.
          const from = this.resolveUnitPosition(event.sourceUnitId);
          const to = this.resolveUnitPosition(event.targetUnitId);
          const spec = castAnimationFor(event.abilityId);
          if (from && to && spec) this.playAttackAnimation(from, to, spec, () => {});
        } else if (event.abilityId === "acidic_brew") {
          this.pendingAcidicBrewCasts.push(event.sourceUnitId);
        } else if (event.abilityId === "blizzard" && event.targetUnitId) {
          this.spawnStaggeredConvergingParticles(event.sourceUnitId, event.targetUnitId, [BLIZZARD_SNOWBALL_COLOR], BLIZZARD_SNOWBALL_COUNT, BLIZZARD_SNOWBALL_INTERVAL_MS);
        } else if (event.abilityId === "snow_golem") {
          this.pendingSnowGolemCasts.push(event.sourceUnitId);
        }
      }
      if (event.type === "damage" && event.causeLabel === "Decay" && event.sourceUnitId && event.targetUnitId) {
        // Decay is a passive AoE (no ability_used event of its own, see Decay.java) -
        // always drains victim toward the caster, never reversed (unlike Soul Rip).
        this.spawnConvergingParticles(
          event.targetUnitId,
          event.sourceUnitId,
          SOUL_RIP_PARTICLE_COLORS,
          DECAY_PARTICLE_COUNT,
          SOUL_RIP_PARTICLE_LIFE_FRAMES,
          SOUL_RIP_PARTICLE_RADIUS_PX,
        );
      }
      if (
        event.type === "damage" &&
        event.causeLabel === "Pylon Collapse" &&
        event.sourceUnitId &&
        !pylonCollapseBurstFor.has(event.sourceUnitId)
      ) {
        pylonCollapseBurstFor.add(event.sourceUnitId);
        const pos = this.resolveUnitPosition(event.sourceUnitId);
        if (pos) this.spawnOneShotPulse(pos, PYLON_COLLAPSE_PULSE_RADIUS_PX, PYLON_COLLAPSE_COLOR, PYLON_COLLAPSE_PULSE_FRAMES);
      }
      if (event.type === "damage" && event.causeLabel === "Sanity's Eclipse" && event.sourceUnitId && event.targetUnitId) {
        const pos = this.resolveUnitPosition(event.targetUnitId);
        if (pos) {
          const list = sanityEclipseTargetsBySource.get(event.sourceUnitId) ?? [];
          list.push(pos);
          sanityEclipseTargetsBySource.set(event.sourceUnitId, list);
        }
      }
      const unitId = event.targetUnitId ?? event.sourceUnitId;
      const unit = unitId ? snapshot?.units.find((u) => u.id === unitId) : null;
      const pos = unit ? axialToPixel({ q: unit.q, r: unit.r }, HEX_SIZE) : { x: 0, y: 0 };
      if (event.type === "death" && unit?.name === "Snow Golem") {
        // SnowBlast.onDeath (backend) fires no DamageEvent and no explicit application event
        // of its own (it just applies a damage-less BlizzardEffect) - the generic "death"
        // VfxEvent every unit's death already produces is the only signal available, per
        // temp/abilities.txt's "5 white pulses, covering the affected area".
        this.spawnMultiPulse(pos, SNOW_BLAST_PULSE_RADIUS_PX, WHITE, SNOW_BLAST_PULSE_COUNT, SNOW_BLAST_PULSE_TOTAL_FRAMES);
      }
      spawnParticleBurst(this.vfxLayer, Ticker.shared, {
        x: pos.x,
        y: pos.y,
        color: colorForVfxType(event.type),
      });
    }
    for (const [sourceUnitId, positions] of sanityEclipseTargetsBySource) {
      this.detonateSanityEclipse(sourceUnitId, positions);
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
    // One live array, passed by reference all the way down into spawnUnitStatusOverlay
    // (and, for ember/smoke/snowflake's periodic emitters, into every particle they spawn
    // afterward) - not built up by spreading each call's own fresh return array. A particle
    // spawned by an emitter well after this call returns still needs to land in the SAME
    // array clearStatusEffectTicks will later iterate, or it's invisible to that teardown
    // and only stops once its own independent lifetime naturally runs out - up to ~0.75s
    // after clearStatusEffectTicks already ran, i.e. after the status itself is gone.
    const ticks: (() => void)[] = [];
    const tokenRadiusPx = (HEX_SIZE * UNIT_SPRITE_SCALE) / 2;
    for (const effect of unit.effects) {
      const spec = statusVisualFor(effect);
      if (!spec || spec.mode !== "unit") continue;
      spawnUnitStatusOverlay(container, Ticker.shared, spec, tokenRadiusPx, ticks);
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
    // applyMoveAnimation: false - see upsertUnit's doc comment. This call is only ever
    // meant to refresh HP-bar/graveyard/status-overlay visuals, never position.
    void this.upsertUnit(
      {
        ...unit,
        currentHp: this.displayedState.hpFor(unitId, unit.currentHp),
        dead: this.displayedState.isDead(unitId),
      },
      undefined,
      false,
    );
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
    const tick = safeTick(() => {
      elapsed += 1;
      ring.visible = Math.floor(elapsed / STROBE_TOGGLE_FRAMES) % 2 === 0;
      if (elapsed >= STROBE_TOTAL_FRAMES) {
        Ticker.shared.remove(tick);
        this.activeStrobeTicks.delete(tick);
        ring.destroy();
      }
    });
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
    // Captured before this.currentUnits is overwritten below - Overheat's
    // proc has no vfx event of its own (see spawnOverheatBursts), so the only
    // way to detect it is diffing each unit's own "Overheating" effect
    // against its value from the snapshot before this one.
    const previousOverheatByUnit = this.overheatAccumulatedByUnit(this.currentUnits);
    this.currentUnits = snapshot.units;

    const previousUnitIds = new Set(this.unitSprites.keys());
    const previousBurningTiles = new Set(
      this.previousTileEffects.filter((e) => e.kind === "burning").map((e) => `${e.q},${e.r}`),
    );

    // Sprout/Overgrowth: neither cast's ability_used event carries a tile position (an
    // empty tile has no unit to name - see VfxEvent), so which new Branchling(s) belong
    // to a pending cast, and where their growing-circle VFX should center, can only be
    // resolved here, once the summon(s) actually land in this snapshot. Claimed units
    // are added to this.branchlingsAwaitingReveal (a persistent field, not a local -
    // Branch's own turn often has more than one action, so further "state" pushes can
    // easily arrive before the ~1.5s circle finishes, and a unit held back only for the
    // one applySnapshot call where it first appeared would fall through to the normal
    // instant-appear path on the very next call) and stay held back from that path below
    // on every call until their circle actually finishes - see spawnGrowingFilledCircle.
    // Matched in cast order (FIFO), which is correct as long as Branchling is never
    // spawned by anything else.
    if (this.pendingBranchSummonReveals.length > 0) {
      const availableBranchlings = snapshot.units.filter(
        (u) => u.name === "Branchling" && !previousUnitIds.has(u.id) && !this.branchlingsAwaitingReveal.has(u.id),
      );
      while (this.pendingBranchSummonReveals.length > 0 && availableBranchlings.length > 0) {
        const kind = this.pendingBranchSummonReveals.shift()!;
        const claimed = kind === "sprout" ? availableBranchlings.splice(0, 1) : availableBranchlings.splice(0);
        const positions = claimed.map((u) => axialToPixel({ q: u.q, r: u.r }, HEX_SIZE));
        const centroid = {
          x: positions.reduce((sum, p) => sum + p.x, 0) / positions.length,
          y: positions.reduce((sum, p) => sum + p.y, 0) / positions.length,
        };
        const claimedIds = claimed.map((u) => u.id);
        for (const id of claimedIds) this.branchlingsAwaitingReveal.add(id);
        const radius = kind === "sprout" ? SPROUT_CIRCLE_RADIUS_PX : OVERGROWTH_CIRCLE_RADIUS_PX;
        this.spawnGrowingFilledCircle(centroid, radius, BRANCH_SUMMON_CIRCLE_COLOR, BRANCH_SUMMON_CIRCLE_FRAMES, () => {
          for (const id of claimedIds) {
            this.branchlingsAwaitingReveal.delete(id);
            // Looked up fresh rather than reusing `claimed`'s snapshot - by the time the
            // circle finishes, possibly several snapshots later, this.currentUnits (kept
            // current every applySnapshot call regardless of hold-back) is the only copy
            // still guaranteed up to date.
            const current = this.currentUnits.find((u) => u.id === id);
            if (!current) continue; // died or was otherwise removed while awaiting reveal
            void this.upsertUnit({
              ...current,
              currentHp: this.displayedState.hpFor(id, current.currentHp),
              dead: this.displayedState.isDead(id),
            });
          }
        });
      }
    }

    // Snow Golem: like Sprout/Overgrowth, the cast (a bare tile, or two tiles upgraded) carries
    // no destination on the wire, so which new "Snow Golem" unit(s) belong to a pending cast can
    // only be resolved once they land. Unlike Overgrowth, each claimed golem gets its OWN
    // independent throw+circle+reveal rather than one shared circle, so the upgrade's pair can
    // genuinely play "at the same time" per temp/abilities.txt instead of being merged into a
    // single centroid animation.
    if (this.pendingSnowGolemCasts.length > 0) {
      const availableGolems = snapshot.units.filter(
        (u) => u.name === "Snow Golem" && !previousUnitIds.has(u.id) && !this.snowGolemsAwaitingReveal.has(u.id),
      );
      while (this.pendingSnowGolemCasts.length > 0 && availableGolems.length > 0) {
        const casterId = this.pendingSnowGolemCasts.shift()!;
        const claimed = availableGolems.splice(0); // both of the upgrade's golems, or the base's one
        const from = this.resolveUnitPosition(casterId);
        const spec = from ? castAnimationFor("snow_golem") : null;
        for (const golem of claimed) {
          const golemId = golem.id;
          const to = axialToPixel({ q: golem.q, r: golem.r }, HEX_SIZE);
          this.snowGolemsAwaitingReveal.add(golemId);
          const reveal = () => {
            this.spawnGrowingFilledCircle(to, SNOW_GOLEM_CIRCLE_RADIUS_PX, SNOW_GOLEM_CIRCLE_COLOR, SNOW_GOLEM_CIRCLE_FRAMES, () => {
              this.snowGolemsAwaitingReveal.delete(golemId);
              const current = this.currentUnits.find((u) => u.id === golemId);
              if (!current) return; // died or was otherwise removed while awaiting reveal
              void this.upsertUnit({
                ...current,
                currentHp: this.displayedState.hpFor(golemId, current.currentHp),
                dead: this.displayedState.isDead(golemId),
              });
            });
          };
          if (from && spec) {
            this.playAttackAnimation(from, to, spec, reveal);
          } else {
            reveal(); // defensive - caster gone, skip straight to the circle
          }
        }
      }
    }

    // Translocation: the unit named by the cast event (see VfxCollector.java's MultiTarget
    // handling) hasn't necessarily moved yet - the event fires before the relocation lands,
    // and carries no destination tile of its own. Once its q,r actually differs from its
    // sprite's current position, that new position is where the growing circle plays; the
    // unit itself is held back (translocationsRevealing, checked in the per-unit loop below)
    // until the circle finishes.
    if (this.translocationsAwaitingMove.size > 0) {
      for (const unit of snapshot.units) {
        if (!this.translocationsAwaitingMove.has(unit.id)) continue;
        const sprite = this.unitSprites.get(unit.id);
        if (!sprite) {
          this.translocationsAwaitingMove.delete(unit.id); // died or otherwise gone - nothing to reveal
          continue;
        }
        const newPos = axialToPixel({ q: unit.q, r: unit.r }, HEX_SIZE);
        if (sprite.position.x === newPos.x && sprite.position.y === newPos.y) continue; // hasn't landed yet
        this.translocationsAwaitingMove.delete(unit.id);
        this.translocationsRevealing.add(unit.id);
        const unitId = unit.id;
        this.spawnGrowingFilledCircle(newPos, TRANSLOCATION_CIRCLE_RADIUS_PX, TRANSLOCATION_CIRCLE_COLOR, TRANSLOCATION_CIRCLE_FRAMES, () => {
          this.translocationsRevealing.delete(unitId);
          const current = this.currentUnits.find((u) => u.id === unitId);
          if (!current) return; // died while awaiting reveal
          // Snaps the sprite straight to its destination (rather than letting upsertUnit's
          // own "did position change" check kick off a slide-tween there) - the relocation
          // itself is already told by the circle, a slide immediately after it would double up.
          const finalSprite = this.unitSprites.get(unitId);
          if (finalSprite) finalSprite.position.set(newPos.x, newPos.y);
          void this.upsertUnit({
            ...current,
            currentHp: this.displayedState.hpFor(unitId, current.currentHp),
            dead: this.displayedState.isDead(unitId),
          });
        });
      }
    }

    // Acidic Brew: like Sprout/Overgrowth, the cast (a bare TileTarget) carries no destination
    // on the wire (AcidicBrew.onUse's target-resolution has no TileTarget case in
    // VfxCollector.java), so the throw's destination is only knowable once AcidPoolEffect's
    // tile(s) actually land. A single cast can register several tiles at once
    // (getTilesInRadius(centre, radius) - radius 0 base, 1 upgraded), so every newly-appeared
    // "acid" tile this snapshot is treated as one group whose pixel centroid is the throw's
    // destination (equal to the real centre tile for a symmetric ring). This has to run
    // *before* renderTileEffects (unlike Eruption's equivalent diff, which runs after) so the
    // held-back tiles can be filtered out of that very call - per temp/abilities.txt, the
    // pulse must finish "before then doing the acid tile overlay".
    const previousAcidTiles = new Set(
      this.previousTileEffects.filter((e) => e.kind === "acid").map((e) => `${e.q},${e.r}`),
    );
    const newAcidTiles = (snapshot.tileEffects ?? []).filter(
      (e) => e.kind === "acid" && !previousAcidTiles.has(`${e.q},${e.r}`),
    );
    if (newAcidTiles.length > 0 && this.pendingAcidicBrewCasts.length > 0) {
      const casterId = this.pendingAcidicBrewCasts.shift()!;
      const tileKeys = newAcidTiles.map((e) => `${e.q},${e.r}`);
      for (const key of tileKeys) this.acidTilesAwaitingReveal.add(key);
      const from = this.resolveUnitPosition(casterId);
      const positions = newAcidTiles.map((e) => axialToPixel({ q: e.q, r: e.r }, HEX_SIZE));
      const to = {
        x: positions.reduce((sum, p) => sum + p.x, 0) / positions.length,
        y: positions.reduce((sum, p) => sum + p.y, 0) / positions.length,
      };
      const spec = from ? castAnimationFor("acidic_brew") : null;
      const releaseTiles = () => {
        for (const key of tileKeys) this.acidTilesAwaitingReveal.delete(key);
        this.renderTileEffects(this.previousTileEffects.filter((e) => !this.acidTilesAwaitingReveal.has(`${e.q},${e.r}`)));
      };
      if (from && spec) {
        this.playAttackAnimation(from, to, spec, () => {
          this.spawnOneShotPulse(to, ACID_PULSE_RADIUS_PX, ACID_PULSE_COLOR, ACID_PULSE_FRAMES, releaseTiles);
        });
      } else {
        // Defensive - caster gone or spec missing: just show the overlay normally, no flourish.
        for (const key of tileKeys) this.acidTilesAwaitingReveal.delete(key);
      }
    }

    this.renderTileEffects((snapshot.tileEffects ?? []).filter((e) => !this.acidTilesAwaitingReveal.has(`${e.q},${e.r}`)));
    this.renderCloakTiles(snapshot.units);

    for (const effect of snapshot.tileEffects ?? []) {
      if (effect.kind === "burning" && !previousBurningTiles.has(`${effect.q},${effect.r}`)) {
        this.spawnEruptionBurst(effect.q, effect.r);
      }
    }
    this.previousTileEffects = snapshot.tileEffects ?? [];
    this.spawnOverheatBursts(previousOverheatByUnit, snapshot.units);

    const seen = new Set<string>();
    for (const unit of snapshot.units) {
      seen.add(unit.id);
      // Not unit.definitionId: a Pylon's unitType is BASIC, and
      // GameStateSnapshotMapper.definitionId() returns the literal string
      // "basic" for every BASIC unit (not a normalized name) - "zenith_pylon"
      // is only the internal definitions-map key PylonAbility.java looks
      // itself up by, it never reaches the wire. The unit's own name field,
      // however, is reliably "Pylon" (new SummonedUnit(pylonDefinition.name(), ...)).
      const isNewPylon = unit.name === "Pylon" && !previousUnitIds.has(unit.id);
      // Holds displayed hp/dead at their last-shown value while something is
      // still pending for this unit, rather than jumping straight to a
      // lethal hit's post-battle result before its own animation/indicator
      // has played - see DisplayedUnitState and ScheduleVfxBatch.
      this.displayedState.syncToTruth(unit.id, unit.currentHp, unit.dead);
      if (this.branchlingsAwaitingReveal.has(unit.id)) {
        // Not materialized yet - its growing-circle cast VFX is still playing, and the
        // completion callback above will call upsertUnit for it once that finishes.
        continue;
      }
      if (this.translocationsRevealing.has(unit.id)) {
        // Held at its old position while its growing-circle cast VFX plays at the
        // destination - the completion callback above snaps it there once that finishes.
        continue;
      }
      if (this.snowGolemsAwaitingReveal.has(unit.id)) {
        // Not materialized yet - its own throw+circle cast VFX is still playing, and the
        // completion callback above will call upsertUnit for it once that finishes.
        continue;
      }
      // Consumed here, unconditionally, whether or not this unit's position
      // actually changed - and ONLY here. upsertUnit itself no longer reads
      // this map: refreshUnitDisplay also calls upsertUnit (whenever a
      // damage/heal indicator is shown for a unit), and if that incidental
      // call were allowed to consume the flag too, a staggered indicator
      // firing before this loop reaches the unit (Backtrack's own heal,
      // shown via IndicatorScheduler's setTimeout, is a real example) could
      // silently discard it on a premature, position-already-current no-op
      // call - see temp/abilities.txt's Backtrack/Dislocation bug reports.
      const pendingMoveKind = this.pendingMoveAnimationByUnit.get(unit.id);
      this.pendingMoveAnimationByUnit.delete(unit.id);
      await this.upsertUnit(
        {
          ...unit,
          currentHp: this.displayedState.hpFor(unit.id, unit.currentHp),
          dead: this.displayedState.isDead(unit.id),
        },
        pendingMoveKind,
      );
      if (isNewPylon) {
        const container = this.unitSprites.get(unit.id);
        if (container) {
          // Held back until the sky-drop orb lands - see spawnPylonSkyDrop.
          container.visible = false;
          this.spawnPylonSkyDrop(unit.q, unit.r, container);
        }
      }
      // Upgraded Sanity's Eclipse re-arms the same OrbEffect for a second
      // fall on the same tile after detonating once, with no vfx event of
      // its own announcing the recast - detonateSanityEclipse already stops
      // the charge on detonation, so if the pending effect is still here on
      // the very next snapshot, that's a recast and the charge should resume.
      // Harmless (a no-op via the .has() guard) for every unit not currently
      // charging at all.
      const stillPendingEclipse = unit.effects.some((e) => e.name === "Sanity's Eclipse (pending)");
      if (stillPendingEclipse && !this.sanityEclipseCharges.has(unit.id)) {
        this.startSanityEclipseCharge(unit.id);
      } else if (!stillPendingEclipse) {
        this.stopSanityEclipseCharge(unit.id);
      }
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
        this.unitRenderGeneration.delete(id);
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

  /**
   * `pendingMoveKind` should only ever be passed by applySnapshot's own loop,
   * which is the sole place allowed to consume pendingMoveAnimationByUnit -
   * see the comment at that call site. refreshUnitDisplay's incidental calls
   * (an HP/dead-only refresh, never a real position change) must always omit
   * it, so they can never apply - or accidentally discard - a pending kind.
   */
  /**
   * `applyMoveAnimation` (default true) gates the entire position-change/
   * animate branch below - false for refreshUnitDisplay's incidental calls
   * (an HP/dead-only refresh, never a real position change), so that call
   * site can never retrigger or override an in-flight move animation.
   *
   * It isn't enough to just stop refreshUnitDisplay from *consuming*
   * pendingMoveAnimationByUnit (a prior fix did only that): this.currentUnits
   * is updated to the new snapshot's positions before applySnapshot's own
   * per-unit loop even starts, so any *other* caller of upsertUnit for the
   * same unit - even with no pendingMoveKind of its own - sees a "position
   * changed" and would restart animateUnitMove as a plain "slide", cancelling
   * whatever bespoke animation is already mid-flight. Backtrack's own heal
   * indicator is a real, reproducible trigger for exactly this: it fires via
   * IndicatorScheduler on the next JS task tick (its cursor is stale between
   * turns), almost always before the just-started "backtrack-trail" tween
   * has spawned a single ghost.
   */
  private async upsertUnit(
    unit: UnitSnapshot,
    pendingMoveKind?: "backtrack-trail" | "teleport",
    applyMoveAnimation = true,
  ): Promise<void> {
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

    // See unitRenderGeneration's own doc comment: bumped here, before the only await in this
    // function, and checked again once it resolves - if a newer call for this same unit id has
    // started (and, since generations only move forward, necessarily already run its own
    // synchronous prefix above) while this one was suspended, this one is stale and must not
    // proceed to render on top of - or clobber the ticks tracked for - whatever that newer call
    // already did.
    const myGeneration = (this.unitRenderGeneration.get(unit.id) ?? 0) + 1;
    this.unitRenderGeneration.set(unit.id, myGeneration);

    const texture = await this.iconFactory.getTexture(
      unit.definitionId,
      unit.team,
      unit.unitType,
      unit.name.charAt(0),
      unit.name,
    );
    if (this.unitRenderGeneration.get(unit.id) !== myGeneration) return;
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
    } else if (applyMoveAnimation && (container.position.x !== pos.x || container.position.y !== pos.y)) {
      // Generic "did this unit's position change" check - covers the plain
      // Move ability, Backtrack, Dislocation, Cloak and Dagger's teleport,
      // anything, since they all just result in a different q,r in the next
      // snapshot. pendingMoveKind (set by playVfx from the preceding vfx
      // batch) is the only per-ability special-casing needed, for the two
      // moves with their own bespoke animation.
      this.animateUnitMove(unit.id, container, pos, pendingMoveKind ?? "slide");
    }
    // else: position is unchanged (or applyMoveAnimation is false, e.g. an
    // incidental HP/dead-only refresh) - leave it exactly where it is
    // (including mid-tween - nothing to do).
  }

  /**
   * Slides a unit's container from wherever it's currently visually sitting
   * to `target`, using the same frame-counted Ticker.shared pattern
   * strobeSprite already establishes in this file. If a tween for this unit
   * is already in flight (a second position update arrived before the first
   * finished), it's cancelled first and the new tween starts from the
   * sprite's current (mid-tween) position rather than the old target, so the
   * motion reads as continuous instead of snapping or fighting itself.
   *
   * `kind` swaps in a bespoke animation for the two casts that need one -
   * "teleport" (Dislocation) is different enough (no travel phase at all,
   * scale rather than position tweened) that it's a separate method entirely;
   * "backtrack-trail" is the same slide with afterimage circles dropped
   * alongside it.
   */
  private animateUnitMove(
    unitId: string,
    container: Container,
    target: { x: number; y: number },
    kind: "slide" | "backtrack-trail" | "teleport" = "slide",
  ): void {
    const existingTick = this.activeMoveTicks.get(unitId);
    if (existingTick) {
      Ticker.shared.remove(existingTick);
      this.activeMoveTicks.delete(unitId);
    }

    const start = { x: container.position.x, y: container.position.y };
    if (kind === "teleport") {
      this.animateTeleportMove(unitId, container, start, target);
      return;
    }

    const dx = target.x - start.x;
    const dy = target.y - start.y;
    let elapsed = 0;
    let trailCursor = 0;

    const tick = safeTick(() => {
      elapsed += 1;
      const t = Math.min(1, elapsed / MOVE_TWEEN_FRAMES);
      const eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
      container.position.set(start.x + dx * eased, start.y + dy * eased);
      if (kind === "backtrack-trail" && elapsed - trailCursor >= BACKTRACK_TRAIL_INTERVAL_FRAMES) {
        trailCursor = elapsed;
        this.spawnBacktrackTrailGhost(container.position.x, container.position.y);
      }
      if (t >= 1) {
        Ticker.shared.remove(tick);
        this.activeMoveTicks.delete(unitId);
      }
    });
    this.activeMoveTicks.set(unitId, tick);
    Ticker.shared.add(tick);
  }

  /** A single fading afterimage circle for Backtrack's trail - self-contained/self-cleaning, same pattern as ParticleBurst's own dots. */
  private spawnBacktrackTrailGhost(x: number, y: number): void {
    const radius = (HEX_SIZE * UNIT_SPRITE_SCALE) / 2;
    const ghost = new Graphics().circle(0, 0, radius).fill({ color: BACKTRACK_TRAIL_COLOR, alpha: BACKTRACK_TRAIL_ALPHA });
    ghost.position.set(x, y);
    this.vfxLayer.addChild(ghost);
    let elapsed = 0;
    const tick = safeTick(() => {
      elapsed += 1;
      ghost.alpha = BACKTRACK_TRAIL_ALPHA * Math.max(0, 1 - elapsed / BACKTRACK_TRAIL_LIFE_FRAMES);
      if (elapsed >= BACKTRACK_TRAIL_LIFE_FRAMES) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
        ghost.destroy();
      }
    });
    this.activeVfxOneShotTicks.add(tick);
    Ticker.shared.add(tick);
  }

  /** Every unit's current Overheat heat gauge, by id - see spawnOverheatBursts. */
  private overheatAccumulatedByUnit(units: UnitSnapshot[]): Map<string, number> {
    const map = new Map<string, number>();
    for (const unit of units) {
      const effect = unit.effects.find((e) => e.name === "Overheating");
      const accumulated = effect ? this.parseOverheatAccumulated(effect.extraInfo) : null;
      if (accumulated !== null) map.set(unit.id, accumulated);
    }
    return map;
  }

  /** OverheatTrackerEffect.java's extraInfo is "Heat: <accumulated> / <threshold>". */
  private parseOverheatAccumulated(extraInfo: string | null): number | null {
    if (!extraInfo) return null;
    const match = /^Heat:\s*(\d+)\s*\/\s*\d+$/.exec(extraInfo);
    return match ? Number(match[1]) : null;
  }

  /**
   * Overheat (Ember) has no vfx event of its own to hook - the only trace of
   * a proc is that the victim's own "Overheating" tracker effect resets
   * (OverheatTrackerEffect.consumeProc() is the only thing that ever lowers
   * `accumulated` - it otherwise only grows), so a drop between two
   * consecutive snapshots is the proc. The burst plays on the overheating
   * unit itself - the epicenter of the neighbour-ignite effect ("makes it
   * overheat and set its neighbours alight"), not on Ember.
   */
  private spawnOverheatBursts(previous: Map<string, number>, units: UnitSnapshot[]): void {
    for (const unit of units) {
      const effect = unit.effects.find((e) => e.name === "Overheating");
      if (!effect) continue;
      const now = this.parseOverheatAccumulated(effect.extraInfo);
      const before = previous.get(unit.id);
      if (now === null || before === undefined || now >= before) continue;
      const pos = axialToPixel({ q: unit.q, r: unit.r }, HEX_SIZE);
      spawnParticleBurst(this.vfxLayer, Ticker.shared, { x: pos.x, y: pos.y, color: OVERHEAT_BURST_COLOR, count: 16, speed: 2.2, life: 26, radius: 3 });
    }
  }

  /** Eruption's cast burst - red particles bursting upward off a newly-ignited tile. */
  private spawnEruptionBurst(q: number, r: number): void {
    const { x, y } = axialToPixel({ q, r }, HEX_SIZE);
    spawnParticleBurst(this.vfxLayer, Ticker.shared, {
      x,
      y,
      color: ERUPTION_BURST_COLOR,
      count: 22,
      speed: 2.4,
      life: 30,
      radius: 3,
      directionDeg: -90,
      spreadDeg: 70,
    });
  }

  /**
   * Pylon's cast: a small orb drops from a synthetic point above the map onto
   * the target tile, then reveals the pylon's already-placed (but hidden)
   * sprite. Self-contained/self-cleaning, same pattern as the attack
   * animation trail particles - not tracked in a teardown set.
   */
  private spawnPylonSkyDrop(q: number, r: number, container: Container): void {
    const target = axialToPixel({ q, r }, HEX_SIZE);
    const from = { x: target.x, y: target.y - PYLON_SKY_OFFSET_PX };
    const orb = new Graphics().circle(0, 0, PYLON_ORB_RADIUS_PX).fill({ color: PYLON_ORB_COLOR });
    orb.position.set(from.x, from.y);
    this.vfxLayer.addChild(orb);
    let elapsed = 0;
    const tick = safeTick(() => {
      elapsed += 1;
      const t = Math.min(1, elapsed / PYLON_DROP_FRAMES);
      const eased = 1 - Math.pow(1 - t, 3);
      orb.position.set(from.x + (target.x - from.x) * eased, from.y + (target.y - from.y) * eased);
      if (t >= 1) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
        orb.destroy();
        // The pylon may already have died (and had its container destroyed by
        // applySnapshot's cleanup) before this ~0.5s drop finished.
        if (!container.destroyed) container.visible = true;
      }
    });
    this.activeVfxOneShotTicks.add(tick);
    Ticker.shared.add(tick);
  }

  /**
   * Mimic's cast: particles spawn at randomised points within the copied
   * unit's own icon radius (not just its centre) and travel to Joker,
   * per temp/abilities2.txt. Positions are resolved once, at cast time - a
   * ~0.5s flight is short enough that either unit moving mid-flight is an
   * acceptable, rarely-visible edge case, the same tradeoff Duel's banners
   * and Static Link's endpoints already make.
   */
  private spawnMimicParticles(targetUnitId: string, casterUnitId: string): void {
    this.spawnConvergingParticles(targetUnitId, casterUnitId, MIMIC_PARTICLE_COLORS, MIMIC_PARTICLE_COUNT);
  }

  /**
   * Soul Rip can target an ally or an enemy (see SoulRip.java) - against an enemy,
   * particles are drained from them to the caster same as Mimic's shape; against an
   * ally, the direction reverses and particles start on the caster instead, per
   * temp/abilities.txt.
   */
  private spawnSoulRip(casterUnitId: string, targetUnitId: string): void {
    const snapshot = this.store.getState().snapshot;
    const caster = snapshot?.units.find((u) => u.id === casterUnitId);
    const target = snapshot?.units.find((u) => u.id === targetUnitId);
    const isAlly = !!caster && !!target && caster.team === target.team;
    const [from, to] = isAlly ? [casterUnitId, targetUnitId] : [targetUnitId, casterUnitId];
    this.spawnConvergingParticles(
      from,
      to,
      SOUL_RIP_PARTICLE_COLORS,
      SOUL_RIP_PARTICLE_COUNT,
      SOUL_RIP_PARTICLE_LIFE_FRAMES,
      SOUL_RIP_PARTICLE_RADIUS_PX,
    );
  }

  /**
   * Particles spawn at a uniform point inside `fromUnitId`'s own icon radius and
   * converge (ease-in) on `toUnitId` - Mimic's original shape, generalized so Soul
   * Rip/Decay (and anything else with this "drain" look) can reuse it with their
   * own colors/count/lifetime rather than duplicating the animation.
   */
  private spawnConvergingParticles(
    fromUnitId: string,
    toUnitId: string,
    colors: number[],
    count: number,
    lifeFrames: number = MIMIC_PARTICLE_LIFE_FRAMES,
    dotRadius: number = 2.5,
  ): void {
    const fromPos = this.resolveUnitPosition(fromUnitId);
    const toPos = this.resolveUnitPosition(toUnitId);
    if (!fromPos || !toPos) return;
    for (let i = 0; i < count; i++) {
      this.spawnConvergingParticle(fromPos, toPos, colors[i % colors.length], dotRadius, lifeFrames);
    }
  }

  /**
   * One converging particle, extracted from spawnConvergingParticles so Blizzard's staggered
   * flurry (spawnStaggeredConvergingParticles) can spawn them one at a time on an interval
   * instead of all at once, while every existing all-at-once caller (Mimic, Soul Rip, Decay,
   * Nanobots) keeps this exact same per-particle shape.
   */
  private spawnConvergingParticle(
    fromPos: { x: number; y: number },
    toPos: { x: number; y: number },
    color: number,
    dotRadius: number,
    lifeFrames: number,
  ): void {
    const radius = (HEX_SIZE * UNIT_SPRITE_SCALE) / 2;
    // sqrt(random) for the radius avoids the centre-bunching a plain uniform draw would cause.
    const angle = Math.random() * Math.PI * 2;
    const r = radius * Math.sqrt(Math.random());
    const startX = fromPos.x + Math.cos(angle) * r;
    const startY = fromPos.y + Math.sin(angle) * r;
    const dot = new Graphics().circle(0, 0, dotRadius).fill({ color });
    dot.position.set(startX, startY);
    this.vfxLayer.addChild(dot);
    let elapsed = 0;
    const tick = safeTick(() => {
      elapsed += 1;
      const t = Math.min(1, elapsed / lifeFrames);
      const eased = t * t; // ease-in - starts slow, accelerates toward the destination
      dot.position.set(startX + (toPos.x - startX) * eased, startY + (toPos.y - startY) * eased);
      dot.alpha = 1 - t;
      if (t >= 1) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
        dot.destroy();
      }
    });
    this.activeVfxOneShotTicks.add(tick);
    Ticker.shared.add(tick);
  }

  /**
   * Blizzard's cast: the same converging-particle look, but spawned one at a time on an
   * interval instead of all at once ("should not spawn at the same time"), and starting
   * anywhere within the caster's portrait rather than just the centre - spawnConvergingParticle
   * already draws its own random start point inside fromPos's icon radius, so this only needs
   * to call it repeatedly instead of in one synchronous loop. Positions are re-resolved live at
   * each spawn rather than frozen once at cast time, since the flurry spans a meaningfully
   * longer window than a single ~0.5s burst.
   */
  private spawnStaggeredConvergingParticles(
    fromUnitId: string,
    toUnitId: string,
    colors: number[],
    count: number,
    intervalMs: number,
    lifeFrames: number = MIMIC_PARTICLE_LIFE_FRAMES,
    dotRadius: number = 2.5,
  ): void {
    let spawned = 0;
    let nextSpawn = performance.now();
    const tick = safeTick(() => {
      const now = performance.now();
      if (now < nextSpawn) return;
      nextSpawn = now + intervalMs;
      const fromPos = this.resolveUnitPosition(fromUnitId);
      const toPos = this.resolveUnitPosition(toUnitId);
      if (fromPos && toPos) {
        this.spawnConvergingParticle(fromPos, toPos, colors[spawned % colors.length], dotRadius, lifeFrames);
      }
      spawned += 1;
      if (spawned >= count) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
      }
    });
    this.activeVfxOneShotTicks.add(tick);
    Ticker.shared.add(tick);
  }

  /**
   * A single hollow ring growing from nothing to `radiusPx`, fading slightly
   * as it grows - Overwhelming Odds' cast and Pylon Collapse's death burst
   * share this exact shape, differing only in colour/radius/duration. `onComplete`
   * (Acidic Brew only, so far) fires once the ring has destroyed itself.
   */
  private spawnOneShotPulse(pos: { x: number; y: number }, radiusPx: number, color: number, frames: number, onComplete?: () => void): void {
    const ring = new Graphics();
    ring.position.set(pos.x, pos.y);
    this.vfxLayer.addChild(ring);
    let elapsed = 0;
    const tick = safeTick(() => {
      elapsed += 1;
      const t = Math.min(1, elapsed / frames);
      const eased = 1 - Math.pow(1 - t, 3);
      ring.clear().circle(0, 0, radiusPx * eased).stroke({ width: 3, color, alpha: 1 - t * 0.3 });
      if (t >= 1) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
        ring.destroy();
        onComplete?.();
      }
    });
    this.activeVfxOneShotTicks.add(tick);
    Ticker.shared.add(tick);
  }

  /**
   * A filled circle growing from radius 0/alpha 0 to `radiusPx`/full opacity over
   * `frames`, both driven by the same progress value rather than independently
   * eased - Sprout/Overgrowth's cast per temp/abilities.txt ("opacity should also
   * start at 0%, and scale up to 100% the same rate as the circle growth").
   * Overgrowth reuses this at a larger radius rather than a separate shape.
   * `onComplete` fires once, after the circle has destroyed itself - the caller
   * uses it to reveal the Branchling(s) only once the circle has finished (see
   * applySnapshot's held-back-summon handling).
   */
  private spawnGrowingFilledCircle(
    pos: { x: number; y: number },
    radiusPx: number,
    color: number,
    frames: number,
    onComplete: () => void,
  ): void {
    const circle = new Graphics();
    circle.position.set(pos.x, pos.y);
    this.vfxLayer.addChild(circle);
    let elapsed = 0;
    const tick = safeTick(() => {
      elapsed += 1;
      const t = Math.min(1, elapsed / frames);
      circle.clear().circle(0, 0, radiusPx * t).fill({ color, alpha: t });
      if (t >= 1) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
        circle.destroy();
        onComplete();
      }
    });
    this.activeVfxOneShotTicks.add(tick);
    Ticker.shared.add(tick);
  }

  /** Eureka's bulb / Reload's gear - a floating icon at a unit's portrait, see FloatingIcon.ts. */
  private spawnFloatingIconAt(pos: { x: number; y: number }, kind: FloatingIconKind, color: number): void {
    const tick = spawnFloatingIcon(this.indicatorLayer, Ticker.shared, {
      x: pos.x,
      y: pos.y - HEX_SIZE * 0.5, // above the token, same offset spawnIndicatorAt uses
      kind,
      color,
      getBoardScale: () => this.root.scale.x,
      onComplete: () => this.activeIndicatorTicks.delete(tick),
    });
    this.activeIndicatorTicks.add(tick);
  }

  /**
   * A plain one-shot beam between two points, no travel phase - Shrink Ray's cast has no
   * DamageEvent to key off (it's a pure stat debuff), so it can't ride the damage-triggered
   * ABILITY_DAMAGE_ANIMATION_BY_CAUSE_LABEL pipeline the way Orbital Beam does. Same
   * pulse-width/fade-in-then-out shape as AttackAnimationPlayer's "beam" stroke, adapted as a
   * self-contained Board helper since this one isn't gated by any VfxEvent's damage reveal.
   */
  private spawnOneShotBeam(from: { x: number; y: number }, to: { x: number; y: number }, color: number, frames: number): void {
    const beam = new Graphics();
    this.vfxLayer.addChild(beam);
    let elapsed = 0;
    const tick = safeTick(() => {
      elapsed += 1;
      const t = Math.min(1, elapsed / frames);
      const width = 4 + 1.5 * Math.sin((elapsed / 10) * Math.PI * 2);
      beam.clear().moveTo(from.x, from.y).lineTo(to.x, to.y).stroke({ width: Math.max(1, width), color, cap: "round" });
      beam.alpha = t < 0.1 ? t / 0.1 : t > 0.85 ? 1 - (t - 0.85) / 0.15 : 1;
      if (t >= 1) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
        beam.destroy();
      }
    });
    this.activeVfxOneShotTicks.add(tick);
    Ticker.shared.add(tick);
  }

  /**
   * Homing Missile's cast-phase launch: a missile icon flies straight up off the caster and
   * out of view, then despawns - purely cosmetic, no state to sync with (the actual impact,
   * turns later, is its own damage-triggered stroke - see ABILITY_DAMAGE_ANIMATION_BY_CAUSE_
   * LABEL["Homing Missile"] and ScheduleVfxBatch's sky-drop origin override). Reuses
   * AttackAnimationPlayer's missile icon cache via buildMissileIcon rather than duplicating it.
   */
  private spawnHomingMissileLaunch(casterUnitId: string | null): void {
    const pos = this.resolveUnitPosition(casterUnitId);
    if (!pos) return;
    const icon = buildMissileIcon(HOMING_MISSILE_COLOR);
    icon.position.set(pos.x, pos.y);
    this.vfxLayer.addChild(icon);
    let elapsed = 0;
    const tick = safeTick(() => {
      elapsed += 1;
      const t = Math.min(1, elapsed / HOMING_MISSILE_LAUNCH_FRAMES);
      const eased = t * t; // ease-in - accelerates away, reads as a launch
      icon.position.y = pos.y - HOMING_MISSILE_LAUNCH_OFFSET_PX * eased;
      if (t > 0.7) icon.alpha = 1 - (t - 0.7) / 0.3;
      if (t >= 1) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
        icon.destroy();
      }
    });
    this.activeVfxOneShotTicks.add(tick);
    Ticker.shared.add(tick);
  }

  /**
   * Sanity's Eclipse's charge phase: a small orb hooked to the caster's own
   * *live* position (re-resolved every frame, so it correctly follows him if
   * he moves while it charges), with particles continuously spawning around
   * it and converging in. Replaces any existing charge for this caster first,
   * so a fresh cast (or the upgrade's recast, see applySnapshot) never leaks
   * a duplicate.
   */
  private startSanityEclipseCharge(casterUnitId: string): void {
    this.stopSanityEclipseCharge(casterUnitId);
    const orb = new Graphics().circle(0, 0, SANITY_ECLIPSE_ORB_RADIUS_PX).fill({ color: SANITY_ECLIPSE_COLOR, alpha: 0.85 });
    this.vfxLayer.addChild(orb);
    let particleCursor = 0;
    let elapsed = 0;
    const tick = safeTick(() => {
      elapsed += 1;
      const pos = this.resolveUnitPosition(casterUnitId);
      if (pos) orb.position.set(pos.x, pos.y - SANITY_ECLIPSE_ORB_Y_OFFSET_PX);
      if (elapsed - particleCursor >= SANITY_ECLIPSE_PARTICLE_INTERVAL_FRAMES) {
        particleCursor = elapsed;
        this.spawnSanityEclipseChargeParticle(orb);
      }
    });
    Ticker.shared.add(tick);
    this.sanityEclipseCharges.set(casterUnitId, { orb, tick });
  }

  private stopSanityEclipseCharge(casterUnitId: string): void {
    const charge = this.sanityEclipseCharges.get(casterUnitId);
    if (!charge) return;
    Ticker.shared.remove(charge.tick);
    charge.orb.destroy();
    this.sanityEclipseCharges.delete(casterUnitId);
  }

  /** One converging particle - reads `orb`'s position live every frame, so it tracks a moving/growing orb for free. */
  private spawnSanityEclipseChargeParticle(orb: Graphics): void {
    const angle = Math.random() * Math.PI * 2;
    const startX = orb.position.x + Math.cos(angle) * SANITY_ECLIPSE_PARTICLE_SPAWN_RADIUS_PX;
    const startY = orb.position.y + Math.sin(angle) * SANITY_ECLIPSE_PARTICLE_SPAWN_RADIUS_PX;
    const dot = new Graphics().circle(0, 0, 2).fill({ color: SANITY_ECLIPSE_COLOR });
    dot.position.set(startX, startY);
    this.vfxLayer.addChild(dot);
    let elapsed = 0;
    const tick = safeTick(() => {
      // The orb can be destroyed (stopSanityEclipseCharge, e.g. on detonation or a fresh
      // recast) while this particle is still mid-flight, chasing it - reading `.position` on
      // a destroyed Graphics throws (Pixi nulls it out), which safeTick would catch, but only
      // after removing the ticker, never destroying `dot` itself, leaving it stuck on screen
      // forever. Checked and handled explicitly here instead of relying on that throw.
      if (orb.destroyed) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
        dot.destroy();
        return;
      }
      elapsed += 1;
      const t = Math.min(1, elapsed / SANITY_ECLIPSE_PARTICLE_LIFE_FRAMES);
      dot.position.set(
        dot.position.x + (orb.position.x - dot.position.x) * 0.15,
        dot.position.y + (orb.position.y - dot.position.y) * 0.15,
      );
      dot.alpha = 1 - t;
      if (t >= 1) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
        dot.destroy();
      }
    });
    this.activeVfxOneShotTicks.add(tick);
    Ticker.shared.add(tick);
  }

  /**
   * Sanity's Eclipse's detonation: grows the charging orb to 1.5 tiles wide
   * and flies it to the centroid of every victim actually hit (there's no
   * tile position anywhere on the wire - see API_CONTRACT.md/EffectSnapshot -
   * so the victims' own resolved positions are the only usable signal, and
   * conveniently work identically for the local player's own casts and the
   * opponent's), then hands off to spawnSanityEclipsePulses.
   */
  private detonateSanityEclipse(casterUnitId: string, targetPositions: { x: number; y: number }[]): void {
    if (targetPositions.length === 0) return;
    const centroid = {
      x: targetPositions.reduce((sum, p) => sum + p.x, 0) / targetPositions.length,
      y: targetPositions.reduce((sum, p) => sum + p.y, 0) / targetPositions.length,
    };
    const charge = this.sanityEclipseCharges.get(casterUnitId);
    const from = charge ? { x: charge.orb.position.x, y: charge.orb.position.y } : centroid;
    this.stopSanityEclipseCharge(casterUnitId);

    const orb = new Graphics();
    orb.position.set(from.x, from.y);
    this.vfxLayer.addChild(orb);
    let elapsed = 0;
    const tick = safeTick(() => {
      elapsed += 1;
      const t = Math.min(1, elapsed / SANITY_ECLIPSE_FLY_FRAMES);
      const eased = 1 - Math.pow(1 - t, 3);
      const radius = SANITY_ECLIPSE_ORB_RADIUS_PX + (SANITY_ECLIPSE_DETONATE_RADIUS_PX - SANITY_ECLIPSE_ORB_RADIUS_PX) * eased;
      orb.position.set(from.x + (centroid.x - from.x) * eased, from.y + (centroid.y - from.y) * eased);
      orb.clear().circle(0, 0, radius).fill({ color: SANITY_ECLIPSE_COLOR, alpha: 0.85 });
      if (t >= 1) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
        orb.destroy();
        this.spawnSanityEclipsePulses(centroid);
      }
    });
    this.activeVfxOneShotTicks.add(tick);
    Ticker.shared.add(tick);
  }

  /** ~3 fast pulses over ~0.7s at the detonation point - thin wrapper over the generalized spawnMultiPulse. */
  private spawnSanityEclipsePulses(pos: { x: number; y: number }): void {
    this.spawnMultiPulse(pos, SANITY_ECLIPSE_DETONATE_RADIUS_PX, SANITY_ECLIPSE_COLOR, SANITY_ECLIPSE_PULSE_COUNT, SANITY_ECLIPSE_PULSE_TOTAL_FRAMES);
  }

  /**
   * N quick hollow pulses cycling in place at a fixed point over `totalFrames` - one ticker
   * cycling through them, rather than N staggered calls. Generalized from Sanity's Eclipse's
   * own 3-pulse detonation so Snow Blast's 5-pulse death effect can share it.
   */
  private spawnMultiPulse(pos: { x: number; y: number }, radiusPx: number, color: number, count: number, totalFrames: number): void {
    const framesPerPulse = Math.round(totalFrames / count);
    const ring = new Graphics();
    ring.position.set(pos.x, pos.y);
    this.vfxLayer.addChild(ring);
    let elapsed = 0;
    const tick = safeTick(() => {
      elapsed += 1;
      const pulseElapsed = elapsed % framesPerPulse;
      const t = pulseElapsed / framesPerPulse;
      const eased = 1 - Math.pow(1 - t, 3);
      ring.clear().circle(0, 0, radiusPx * eased).stroke({ width: 3, color, alpha: 1 - t * 0.5 });
      if (elapsed >= totalFrames) {
        Ticker.shared.remove(tick);
        this.activeVfxOneShotTicks.delete(tick);
        ring.destroy();
      }
    });
    this.activeVfxOneShotTicks.add(tick);
    Ticker.shared.add(tick);
  }

  /**
   * Dislocation's teleport: shrinks to nothing at the old tile, jumps
   * straight to the new one (no travel phase - it's gone, then it's there),
   * then grows back from nothing. Tweens `container.scale` rather than
   * position for the two phases, unlike the plain slide.
   */
  private animateTeleportMove(
    unitId: string,
    container: Container,
    start: { x: number; y: number },
    target: { x: number; y: number },
  ): void {
    container.position.set(start.x, start.y);
    let phase: "shrink" | "grow" = "shrink";
    let elapsed = 0;

    const tick = safeTick(() => {
      elapsed += 1;
      const t = Math.min(1, elapsed / TELEPORT_PHASE_FRAMES);
      const eased = 1 - Math.pow(1 - t, 3); // ease-out cubic
      if (phase === "shrink") {
        container.scale.set(1 - eased);
        if (t >= 1) {
          phase = "grow";
          elapsed = 0;
          container.position.set(target.x, target.y);
        }
        return;
      }
      container.scale.set(eased);
      if (t >= 1) {
        container.scale.set(1);
        Ticker.shared.remove(tick);
        this.activeMoveTicks.delete(unitId);
      }
    });
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
        .fill({ color: 0x0e0b0b, alpha: 0.92 })
        .stroke({ width: 1, color: 0x3a1414 }),
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
