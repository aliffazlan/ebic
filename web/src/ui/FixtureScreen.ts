// Dev-only harness: loads a local fixture directly into the real Board/Hud
// (no auth, no lobby, no live socket) so the renderer can be developed/
// eyeballed before a backend exists. Reached via `?fixture=1` (match view,
// web/src/fixtures/sample-state.json) or `?fixture=placement` (placement
// editor, web/src/fixtures/sample-placement.json). In placement mode, edits
// are applied optimistically to a local in-memory copy of the fixture
// (simulating the server's echo-back) so the swap/move/confirm flow can
// actually be clicked through without a backend.

import { Application } from "pixi.js";
import { Board } from "../board/Board";
import { GameStateStore } from "../state/GameStateStore";
import { Hud } from "./Hud";
import { TurnBanner } from "./TurnBanner";
import { IndicatorScheduler } from "../vfx/IndicatorScheduler";
import { scheduleVfxBatch } from "../vfx/ScheduleVfxBatch";
import sampleState from "../fixtures/sample-state.json";
import samplePlacement from "../fixtures/sample-placement.json";
import type { Attribute, GameStateSnapshot, PlacementStateSnapshot, VfxEvent } from "../types/contract";
import type { MatchActions } from "./MatchActions";
import type { Screen } from "./Screen";
import { CHROME_VOID_HEX } from "./Colors";

export type FixtureMode = "match" | "placement";

/**
 * A synthetic vfx batch covering every indicator kind, fired by pressing "v" in
 * the match fixture. Damage numbers otherwise need a live two-player match to
 * see at all, which makes tweaking the animation or the colours painful.
 *
 * Deliberately spans all three stagger groups (poison, burn, everything else)
 * and includes a unit taking two hits at once, so the stacking offset shows up.
 *
 * Also covers every attack-animation kind (slash/arrow/projectile/lightning/
 * beam), Wei's multi-stroke, a lightning/beam MISS, and confirms Cloak and
 * Dagger keeps the old unanimated treatment (attacker and defender share a
 * tile there, so a travel animation has nowhere to travel).
 */
const DEMO_VFX: VfxEvent[] = [
  { type: "damage", abilityId: null, sourceUnitId: "u-harbinger", targetUnitId: "u-valor", amount: 12, causeLabel: "Poison" },
  { type: "damage", abilityId: null, sourceUnitId: "u-harbinger", targetUnitId: "u-dirge", amount: 8, causeLabel: "Poison" },
  { type: "damage", abilityId: null, sourceUnitId: "u-evayne", targetUnitId: "u-valor", amount: 15, causeLabel: "Burn" },
  { type: "damage", abilityId: null, sourceUnitId: "u-basic-3", targetUnitId: "u-basic-1", amount: 34, causeLabel: "Attack" }, // default white slash
  { type: "damage", abilityId: null, sourceUnitId: "u-basic-3", targetUnitId: "u-basic-2", amount: 0, causeLabel: "Attack" }, // default white slash, MISS
  { type: "damage", abilityId: null, sourceUnitId: "u-valor", targetUnitId: "u-basic-3", amount: 55, causeLabel: "Attack" }, // silver slash
  { type: "damage", abilityId: null, sourceUnitId: "u-discharge", targetUnitId: "u-basic-3", amount: 50, causeLabel: "Attack" }, // yellow lightning
  { type: "damage", abilityId: null, sourceUnitId: "u-zenith", targetUnitId: "u-basic-1", amount: 0, causeLabel: "Attack" }, // dark-blue beam, MISS
  { type: "damage", abilityId: null, sourceUnitId: "u-artemis", targetUnitId: "u-harbinger", amount: 40, causeLabel: "Counterstrike" }, // white arrow
  { type: "damage", abilityId: null, sourceUnitId: "u-wei", targetUnitId: "u-valor", amount: 48, causeLabel: "Duel" }, // magenta+dark-blue multi-stroke
  { type: "damage", abilityId: null, sourceUnitId: "u-ember", targetUnitId: "u-dirge", amount: 30, causeLabel: "Attack" }, // orange projectile + particles
  { type: "damage", abilityId: null, sourceUnitId: "u-evayne", targetUnitId: "u-dirge", amount: 30, causeLabel: "Cloak and Dagger" }, // must NOT get the new animation
  { type: "damage", abilityId: null, sourceUnitId: "u-evayne", targetUnitId: "u-dirge", amount: 60, causeLabel: "Sanity's Eclipse" },
  { type: "damage", abilityId: null, sourceUnitId: "u-evayne", targetUnitId: "u-dirge", amount: 25, causeLabel: "Acidic Brew" },
  { type: "heal", abilityId: null, sourceUnitId: null, targetUnitId: "u-basic-1", amount: 40, causeLabel: null },
  // Gets no number at all - only damage and heals do.
  { type: "ability_used", abilityId: "fireblast", sourceUnitId: "u-evayne", targetUnitId: "u-valor", amount: null, causeLabel: null },
  // Cast VFX added for temp/abilities2.txt (2026-09-15): Fireblast's growing
  // fireball, Perplexing Shot's widening chain, Orbital Beam's sky-beam
  // stagger, Mimic's converging particles, Overwhelming Odds' cast pulse, and
  // a stand-in Pylon Collapse burst (no real pylon unit in this fixture, so
  // u-zenith plays the dying pylon's role for the collapse pulse only).
  { type: "damage", abilityId: null, sourceUnitId: "u-ember", targetUnitId: "u-dirge", amount: 24, causeLabel: "Fireblast" },
  { type: "damage", abilityId: null, sourceUnitId: "u-harbinger", targetUnitId: "u-valor", amount: 30, causeLabel: "Perplexing Shot" },
  { type: "damage", abilityId: null, sourceUnitId: "u-harbinger", targetUnitId: "u-dirge", amount: 50, causeLabel: "Perplexing Shot" },
  { type: "damage", abilityId: null, sourceUnitId: "u-harbinger", targetUnitId: "u-wei", amount: 70, causeLabel: "Perplexing Shot" },
  { type: "damage", abilityId: null, sourceUnitId: "u-zenith", targetUnitId: "u-basic-1", amount: 60, causeLabel: "Orbital Beam" },
  { type: "damage", abilityId: null, sourceUnitId: "u-zenith", targetUnitId: "u-basic-2", amount: 60, causeLabel: "Orbital Beam" },
  { type: "ability_used", abilityId: "mimic", sourceUnitId: "u-harbinger", targetUnitId: "u-valor", amount: null, causeLabel: null },
  { type: "ability_used", abilityId: "overwhelming_odds", sourceUnitId: "u-valor", targetUnitId: null, amount: null, causeLabel: null },
  { type: "damage", abilityId: null, sourceUnitId: "u-zenith", targetUnitId: "u-basic-1", amount: 15, causeLabel: "Pylon Collapse" },
  { type: "damage", abilityId: null, sourceUnitId: "u-zenith", targetUnitId: "u-basic-2", amount: 15, causeLabel: "Pylon Collapse" },
];

export class FixtureScreen implements Screen, MatchActions {
  private container: HTMLDivElement | null = null;
  private app: Application | null = null;
  private board: Board | null = null;
  private hud: Hud | null = null;
  private store = new GameStateStore("PLAYER_ONE", "Player One", "Player Two");
  private root: HTMLElement;
  private mode: FixtureMode;
  private indicators = new IndicatorScheduler();
  private turnBanner: TurnBanner | null = null;
  // Dev-only: damage numbers and the turn banner need a live match to trigger
  // otherwise, so the fixture harness fakes them on a keypress.
  private demoKeyListener = (e: KeyboardEvent) => this.handleDemoKey(e);
  private placementFixture: PlacementStateSnapshot = structuredClone(
    samplePlacement as PlacementStateSnapshot,
  );

  constructor(root: HTMLElement, mode: FixtureMode = "match") {
    this.root = root;
    this.mode = mode;
  }

  mount(): void {
    const container = document.createElement("div");
    container.className = "match-screen";
    const canvasHost = document.createElement("div");
    canvasHost.className = "canvas-host";
    const hudHost = document.createElement("div");
    hudHost.className = "hud-host";

    // Combat and status logs get their own column on the left, opposite the
    // sidebar, so the board sits between what happened and what you can do.
    const logHost = document.createElement("div");
    logHost.className = "log-host";
    container.append(logHost, canvasHost, hudHost);
    this.root.appendChild(container);
    this.container = container;

    this.turnBanner = new TurnBanner(canvasHost);
    void this.initPixi(canvasHost);
    this.hud = new Hud(hudHost, logHost, "fixture-preview", this.store, this);
    if (this.mode === "match") {
      window.addEventListener("keydown", this.demoKeyListener);
      this.store.pushMessage("Fixture dev keys: v = damage indicators, t = YOUR TURN banner.");
    }

    if (this.mode === "placement") {
      this.store.setState({ placementState: this.placementFixture, connected: true });
      this.store.pushMessage(
        "Loaded local fixture (web/src/fixtures/sample-placement.json) — no live server, edits are simulated locally.",
      );
    } else {
      this.store.setState({ snapshot: sampleState as GameStateSnapshot, connected: true });
      this.store.pushMessage("Loaded local fixture (web/src/fixtures/sample-state.json) — no live server.");
    }
  }

  unmount(): void {
    window.removeEventListener("keydown", this.demoKeyListener);
    this.indicators.clear();
    this.turnBanner?.destroy();
    this.turnBanner = null;
    this.hud?.destroy();
    this.board?.destroy();
    this.app?.destroy(true, { children: true });
    this.container?.remove();
    this.container = null;
  }

  private async initPixi(canvasHost: HTMLDivElement): Promise<void> {
    const app = new Application();
    await app.init({ background: CHROME_VOID_HEX, resizeTo: canvasHost, antialias: true });
    canvasHost.appendChild(app.canvas);
    this.app = app;
    this.board = new Board(app, this.store, {
      onTileClick: (coord) => this.handleTileClick(coord),
      onUnitClick: (unit) => this.handleUnitClick(unit.id),
    });
    app.stage.addChild(this.board.root);
  }

  /**
   * Dev-only board-feedback triggers. Deliberately plain letters and no
   * modifier: the fixture harness has no text inputs to conflict with, and the
   * board's own pan/zoom keys (arrows, WASD, Home) are all elsewhere.
   */
  private handleDemoKey(e: KeyboardEvent): void {
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    if (e.key === "v") {
      // Drive the combat log too - the real flow commits on the "state" message
      // that follows a vfx batch, which the fixture has no server to send.
      this.store.appendCombatLog(DEMO_VFX);
      this.store.commitCombatLog(this.store.getState().snapshot?.currentTeam ?? "PLAYER_ONE");
      // Same orchestration MatchScreen uses off a real "vfx" message, so the
      // fixture exercises the exact sequencing code rather than a hand-rolled
      // copy that can drift from it.
      scheduleVfxBatch(DEMO_VFX, {
        indicators: this.indicators,
        playVfx: (events) => this.board?.playVfx(events),
        playAttackAnimation: (from, to, spec, onComplete) => this.board?.playAttackAnimation(from, to, spec, onComplete),
        playMarkConsumedBurst: (from, to) => this.board?.playMarkConsumedBurst(from, to),
        showIndicators: (specs) => this.board?.showIndicators(specs),
        showIndicatorAt: (pos, spec) => this.board?.showIndicatorAt(pos, spec),
        resolveUnitPosition: (unitId) => this.board?.resolveUnitPosition(unitId) ?? null,
        beginPendingHpChange: (unitId) => this.board?.beginPendingHpChange(unitId),
        sourceDefinitionId: (event) =>
          this.store.getState().snapshot?.units.find((u) => u.id === event.sourceUnitId)?.definitionId ?? null,
      });
      // Queued last, so it lands after the staggered groups - the same
      // arrangement the real turn-start sequence produces.
      this.indicators.enqueue(() => this.turnBanner?.show(), 0);
    } else if (e.key === "t") {
      this.turnBanner?.show();
    }
  }

  private handleTileClick(coord: { q: number; r: number }): void {
    if (this.mode !== "placement") return;
    const state = this.store.getState();
    if (state.placementState?.confirmed) return;
    if (state.selectedUnitId) {
      this.sendPlacementMove(state.selectedUnitId, coord.q, coord.r);
    } else {
      this.selectUnit(null);
    }
  }

  private handleUnitClick(unitId: string): void {
    if (this.mode !== "placement") {
      this.selectUnit(unitId);
      return;
    }
    const state = this.store.getState();
    if (state.placementState?.confirmed) return;
    if (state.selectedUnitId === unitId) {
      this.selectUnit(null);
    } else if (state.selectedUnitId) {
      this.sendPlacementSwap(state.selectedUnitId, unitId);
    } else {
      this.selectUnit(unitId);
    }
  }

  selectUnit(unitId: string | null): void {
    this.store.setState({ selectedUnitId: unitId, selectedAbilityId: null });
  }
  selectAbility(abilityId: string | null): void {
    this.store.setState({ selectedAbilityId: abilityId });
  }
  castAbility(): void {
    this.store.pushMessage("(fixture preview - no server to send this to)");
    this.store.setState({ selectedAbilityId: null });
  }
  endTurn(): void {
    this.store.pushMessage("(fixture preview - no server to send this to)");
  }
  sendAttribute(_value: Attribute): void {
    this.store.pushMessage("(fixture preview - no server to send this to)");
  }
  cancelChoice(): void {
    // Fixtures render a static snapshot; nothing is listening for an answer.
  }

  sendChoice(_optionId: string): void {
    this.store.pushMessage("(fixture preview - no server to send this to)");
  }
  sendPick(_definitionId: string): void {
    this.store.pushMessage("(fixture preview - no server to send this to)");
  }
  sendPlacementSwap(unitId: string, targetUnitId: string): void {
    const a = this.placementFixture.units.find((u) => u.unitId === unitId);
    const b = this.placementFixture.units.find((u) => u.unitId === targetUnitId);
    if (a && b) {
      [a.q, b.q] = [b.q, a.q];
      [a.r, b.r] = [b.r, a.r];
    }
    this.store.setState({ placementState: { ...this.placementFixture }, selectedUnitId: null });
  }
  sendPlacementMove(unitId: string, q: number, r: number): void {
    const unit = this.placementFixture.units.find((u) => u.unitId === unitId);
    if (unit) {
      unit.q = q;
      unit.r = r;
    }
    this.store.setState({ placementState: { ...this.placementFixture }, selectedUnitId: null });
  }
  confirmPlacement(): void {
    this.placementFixture = { ...this.placementFixture, confirmed: true };
    this.store.setState({ placementState: this.placementFixture });
    this.store.pushMessage("(fixture preview - confirmed locally, no opponent to wait on)");
  }
  exitToLobby(): void {
    location.href = location.pathname;
  }

  // Sandbox tools - the SANDBOX tab never renders here, so nothing can call these.
  openSandboxPicker(): void {}
  chooseSandboxSpawn(): void {}
  startSandboxTool(): void {}
  cancelSandboxTool(): void {}
  sendSandbox(): void {}
}
