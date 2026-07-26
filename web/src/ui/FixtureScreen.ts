// Dev-only harness: loads web/src/fixtures/sample-state.json directly into
// the real Board/Hud (no auth, no lobby, no live socket) so the renderer can
// be developed/eyeballed before a backend exists. Reached via `?fixture=1`.

import { Application } from "pixi.js";
import { Board } from "../board/Board";
import { GameStateStore } from "../state/GameStateStore";
import { Hud } from "./Hud";
import sampleState from "../fixtures/sample-state.json";
import type { GameStateSnapshot, Attribute } from "../types/contract";
import type { MatchActions } from "./MatchActions";
import type { Screen } from "./Screen";

export class FixtureScreen implements Screen, MatchActions {
  private container: HTMLDivElement | null = null;
  private app: Application | null = null;
  private board: Board | null = null;
  private hud: Hud | null = null;
  private store = new GameStateStore("PLAYER_ONE");
  private root: HTMLElement;

  constructor(root: HTMLElement) {
    this.root = root;
  }

  mount(): void {
    const container = document.createElement("div");
    container.className = "match-screen";
    const canvasHost = document.createElement("div");
    canvasHost.className = "canvas-host";
    const hudHost = document.createElement("div");
    hudHost.className = "hud-host";
    container.append(canvasHost, hudHost);
    this.root.appendChild(container);
    this.container = container;

    void this.initPixi(canvasHost);
    this.hud = new Hud(hudHost, "fixture-preview", this.store, this);
    this.store.setState({ snapshot: sampleState as GameStateSnapshot, connected: true });
    this.store.pushMessage("Loaded local fixture (web/src/fixtures/sample-state.json) — no live server.");
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
      onTileClick: () => {},
      onUnitClick: (unit) => this.selectUnit(unit.id),
    });
    app.stage.addChild(this.board.root);
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
  sendPick(_definitionId: string): void {
    this.store.pushMessage("(fixture preview - no server to send this to)");
  }
  sendPlacement(_q: number, _r: number): void {
    this.store.pushMessage("(fixture preview - no server to send this to)");
  }
  exitToLobby(): void {
    location.href = location.pathname;
  }
}
