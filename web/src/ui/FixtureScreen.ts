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
import sampleState from "../fixtures/sample-state.json";
import samplePlacement from "../fixtures/sample-placement.json";
import type { Attribute, GameStateSnapshot, PlacementStateSnapshot } from "../types/contract";
import type { MatchActions } from "./MatchActions";
import type { Screen } from "./Screen";

export type FixtureMode = "match" | "placement";

export class FixtureScreen implements Screen, MatchActions {
  private container: HTMLDivElement | null = null;
  private app: Application | null = null;
  private board: Board | null = null;
  private hud: Hud | null = null;
  private store = new GameStateStore("PLAYER_ONE");
  private root: HTMLElement;
  private mode: FixtureMode;
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

    void this.initPixi(canvasHost);
    this.hud = new Hud(hudHost, logHost, "fixture-preview", this.store, this);

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
    this.hud?.destroy();
    this.board?.destroy();
    this.app?.destroy(true, { children: true });
    this.container?.remove();
    this.container = null;
  }

  private async initPixi(canvasHost: HTMLDivElement): Promise<void> {
    const app = new Application();
    await app.init({ background: "#0f172a", resizeTo: canvasHost, antialias: true });
    canvasHost.appendChild(app.canvas);
    this.app = app;
    this.board = new Board(app, this.store, {
      onTileClick: (coord) => this.handleTileClick(coord),
      onUnitClick: (unit) => this.handleUnitClick(unit.id),
    });
    app.stage.addChild(this.board.root);
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
}
