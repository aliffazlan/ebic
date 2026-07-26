import { Application } from "pixi.js";
import { Board } from "../board/Board";
import { GameStateStore } from "../state/GameStateStore";
import { GameSocket } from "../net/GameSocket";
import { Hud } from "./Hud";
import type { AxialCoord } from "../hex/HexMath";
import type { Attribute, ServerMessage, Team, UnitSnapshot } from "../types/contract";
import type { MatchActions } from "./MatchActions";
import type { Screen } from "./Screen";

export class MatchScreen implements Screen, MatchActions {
  private container: HTMLDivElement | null = null;
  private app: Application | null = null;
  private board: Board | null = null;
  private hud: Hud | null = null;
  private socket: GameSocket;
  private store: GameStateStore;
  private resizeListener = () => this.handleResize();
  private root: HTMLElement;
  private matchId: string;
  private onExit: () => void;

  constructor(root: HTMLElement, matchId: string, yourTeam: Team, onExit: () => void) {
    this.root = root;
    this.matchId = matchId;
    this.onExit = onExit;
    this.store = new GameStateStore(yourTeam);
    this.socket = new GameSocket(matchId, {
      onMessage: (msg) => this.handleMessage(msg),
      onOpen: () => {
        this.store.setState({ connected: true });
        this.store.pushMessage("Connected to match.");
      },
      onClose: () => {
        this.store.setState({ connected: false });
        this.store.pushMessage("Disconnected from match.");
      },
      onError: () => this.store.pushMessage("Connection error."),
    });
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

    this.hud = new Hud(hudHost, this.matchId, this.store, this);
    this.socket.connect();

    window.addEventListener("resize", this.resizeListener);
  }

  unmount(): void {
    window.removeEventListener("resize", this.resizeListener);
    this.socket.close();
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
      onUnitClick: (unit) => this.handleUnitClick(unit),
    });
    app.stage.addChild(this.board.root);
  }

  private handleResize(): void {
    this.board?.recenter();
  }

  private handleMessage(msg: ServerMessage): void {
    switch (msg.type) {
      case "state": {
        const wasGameOver = this.store.getState().gameOver;
        this.store.setState({
          snapshot: msg.payload,
          gameOver: msg.payload.gameOver ? wasGameOver : null,
        });
        break;
      }
      case "vfx":
        this.board?.playVfx(msg.payload);
        break;
      case "draft_round":
        this.store.setState({ draftRound: msg.payload });
        break;
      case "prompt":
        this.store.setState({ prompt: msg.payload, selectedAbilityId: null });
        break;
      case "message":
        this.store.pushMessage(msg.text);
        break;
      case "game_over":
        this.store.setState({ gameOver: msg.payload });
        break;
    }
  }

  private handleTileClick(coord: AxialCoord): void {
    const state = this.store.getState();

    if (state.prompt?.kind === "placement") {
      this.sendPlacement(coord.q, coord.r);
      return;
    }

    if (state.selectedAbilityId && state.selectedUnitId) {
      this.castAbility("tile", { q: coord.q, r: coord.r });
      return;
    }

    // Empty-tile click with nothing pending: deselect for convenience.
    this.selectUnit(null);
  }

  private handleUnitClick(unit: UnitSnapshot): void {
    const state = this.store.getState();

    if (state.prompt?.kind === "placement") {
      // Placement only targets tiles; ignore unit clicks during placement.
      return;
    }

    if (state.selectedAbilityId && state.selectedUnitId) {
      this.castAbility("unit", { unitId: unit.id });
      return;
    }

    this.selectUnit(unit.id);
  }

  // ---- MatchActions ----

  selectUnit(unitId: string | null): void {
    this.store.setState({ selectedUnitId: unitId, selectedAbilityId: null });
  }

  selectAbility(abilityId: string | null): void {
    this.store.setState({ selectedAbilityId: abilityId });
  }

  castAbility(targetKind: "unit" | "tile" | "none", target?: { unitId?: string; q?: number; r?: number }): void {
    const state = this.store.getState();
    if (!state.selectedUnitId || !state.selectedAbilityId) return;

    this.socket.send({
      type: "action",
      kind: "ability",
      unitId: state.selectedUnitId,
      abilityId: state.selectedAbilityId,
      targetKind,
      ...(target?.unitId ? { targetUnitId: target.unitId } : {}),
      ...(target?.q !== undefined ? { q: target.q } : {}),
      ...(target?.r !== undefined ? { r: target.r } : {}),
    });
    this.store.setState({ selectedAbilityId: null });
  }

  endTurn(): void {
    this.socket.send({ type: "action", kind: "end_turn" });
  }

  sendAttribute(value: Attribute): void {
    this.socket.send({ type: "attribute", value });
  }

  sendPick(definitionId: string): void {
    this.socket.send({ type: "pick", definitionId });
    this.store.setState({ draftRound: null });
  }

  sendPlacement(q: number, r: number): void {
    this.socket.send({ type: "placement", q, r });
  }

  exitToLobby(): void {
    this.onExit();
  }
}
