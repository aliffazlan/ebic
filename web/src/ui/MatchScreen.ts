import { Application } from "pixi.js";
import { Board } from "../board/Board";
import { GameStateStore } from "../state/GameStateStore";
import { GameSocket } from "../net/GameSocket";
import { Hud } from "./Hud";
import type { AxialCoord } from "../hex/HexMath";
import type { Attribute, ServerMessage, Team, UnitSnapshot } from "../types/contract";
import type { MatchActions } from "./MatchActions";
import type { Screen } from "./Screen";

// How long the pre-encounter strobe plays on the board before the attribute
// modal actually appears - see API_CONTRACT.md's explanation of the
// encounter-trigger/strobe design. Kept in the ~1.5-2s range the brief asked
// for; matches (loosely, doesn't need to be exact) Board's own frame-counted
// STROBE_TOTAL_FRAMES so the ring visually finishes right around when the
// modal shows up.
const ATTRIBUTE_STROBE_MS = 1800;

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
  // Tracks the delayed "show the attribute modal after the strobe"
  // setTimeout so a newer prompt (or unmount) can cancel a stale one -
  // otherwise a fast second attribute prompt could have its immediate strobe
  // clobbered a moment later by an earlier prompt's delayed setState.
  private pendingAttributePromptTimer: number | null = null;
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
        this.store.pushMessage("Disconnected from match - reconnecting...");
      },
      onError: () => this.store.pushMessage("Connection error."),
      onReconnecting: (attempt) => {
        // Only the first attempt gets its own message - a burst of "attempt
        // N" lines during a longer outage would just be noise in the log;
        // the top-bar "Reconnecting..." badge (driven by `connected`) is the
        // ongoing indicator, this is just the initial heads-up.
        if (attempt === 1) {
          this.store.pushMessage("Connection lost, attempting to reconnect...");
        }
      },
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
    if (this.pendingAttributePromptTimer !== null) {
      window.clearTimeout(this.pendingAttributePromptTimer);
      this.pendingAttributePromptTimer = null;
    }
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
        const priorState = this.store.getState();
        const wasGameOver = priorState.gameOver;
        this.store.setState({
          snapshot: msg.payload,
          gameOver: msg.payload.gameOver ? wasGameOver : null,
          // A real "state" message means the match has begun for both
          // players - placement is over, drop any lingering placement UI.
          placementState: null,
          // A "state" push is one of the signals that an in-flight attribute
          // encounter has resolved (see the "waiting for other player" state
          // below) - defensive alongside the "vfx" case, which normally gets
          // there first since vfx always precedes the state it reflects.
          ...(priorState.prompt?.kind === "attribute" ? { prompt: null, attributeSubmitted: false } : {}),
        });
        break;
      }
      case "vfx":
        this.board?.playVfx(msg.payload);
        this.store.appendCombatLog(msg.payload);
        {
          // The "attribute" prompt's own encounter resolving is signaled by
          // the next vfx/state push, not by a fresh prompt necessarily aimed
          // at this client (see API_CONTRACT.md's "waiting for other player"
          // paragraph) - clear it here so the waiting-state modal closes.
          const priorPrompt = this.store.getState().prompt;
          if (priorPrompt?.kind === "attribute") {
            this.store.setState({ prompt: null, attributeSubmitted: false });
          }
        }
        break;
      case "draft_round":
        this.store.setState({ draftRound: msg.payload });
        break;
      case "placement_state":
        this.store.setState({ placementState: msg.payload, selectedUnitId: null });
        break;
      case "prompt": {
        // Any freshly arriving prompt supersedes an in-flight delayed one
        // (see the field comment on pendingAttributePromptTimer).
        if (this.pendingAttributePromptTimer !== null) {
          window.clearTimeout(this.pendingAttributePromptTimer);
          this.pendingAttributePromptTimer = null;
        }

        if (msg.payload.kind === "attribute") {
          // The `attribute` prompt doubles as the encounter trigger (see
          // API_CONTRACT.md) - both units are already in the last "state"
          // snapshot, no fog of war once combat has started. Strobe them on
          // the board first, THEN show the attribute modal - don't set
          // `prompt` yet, since that's what Hud uses to decide to render it.
          const payload = msg.payload;
          this.board?.strobeUnits([payload.unitId, payload.opponentUnitId]);
          this.store.setState({ selectedAbilityId: null, attributeSubmitted: false });
          this.pendingAttributePromptTimer = window.setTimeout(() => {
            this.pendingAttributePromptTimer = null;
            this.store.setState({ prompt: payload });
          }, ATTRIBUTE_STROBE_MS);
        } else {
          this.store.setState({ prompt: msg.payload, selectedAbilityId: null, attributeSubmitted: false });
        }
        break;
      }
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

    if (state.placementState) {
      // No edits are accepted once this player has confirmed - just wait.
      if (state.placementState.confirmed) return;
      // Empty tile clicked with a unit selected: relocate it there. No
      // artificial "zone" restriction client-side - let the click through
      // and let the server validate/reject via a "message" push.
      if (state.selectedUnitId) {
        this.sendPlacementMove(state.selectedUnitId, coord.q, coord.r);
      }
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

    if (state.placementState) {
      if (state.placementState.confirmed) return;
      if (state.selectedUnitId === unit.id) {
        // Clicking the already-selected unit again cancels the selection.
        this.selectUnit(null);
        return;
      }
      if (state.selectedUnitId) {
        this.sendPlacementSwap(state.selectedUnitId, unit.id);
        return;
      }
      this.selectUnit(unit.id);
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
    // Optimistic local clear rather than waiting for a fresh prompt to
    // arrive and overwrite it - see the comment on sendAttribute below, the
    // same "a fresh prompt only reaches whoever's next to act" gap applies
    // here too (a cast that ends this player's turn leaves the stale
    // "action" prompt's legalTargets sitting in state with nothing to
    // refresh it until this player's next turn).
    this.store.setState({ selectedAbilityId: null, prompt: null });
  }

  endTurn(): void {
    this.socket.send({ type: "action", kind: "end_turn" });
    this.store.setState({ prompt: null });
  }

  sendAttribute(value: Attribute): void {
    this.socket.send({ type: "attribute", value });
    // Real bug fix (see API_CONTRACT.md): a fresh `prompt` only gets pushed
    // to whichever team is next to act - after the *defender* answers, it's
    // not their turn, so nothing ever arrives to replace this stale
    // `attribute` prompt. Keep the prompt (and its encounter card) up, but
    // flip to the "waiting for other player" state rather than closing the
    // modal outright - handleMessage clears both `prompt` and
    // `attributeSubmitted` the moment a vfx/state/new-prompt message signals
    // the encounter actually resolved.
    this.store.setState({ attributeSubmitted: true });
  }

  sendPick(definitionId: string): void {
    this.socket.send({ type: "pick", definitionId });
    this.store.setState({ draftRound: null });
  }

  sendPlacementSwap(unitId: string, targetUnitId: string): void {
    this.socket.send({ type: "placement_edit", kind: "swap", unitId, targetUnitId });
    this.store.setState({ selectedUnitId: null });
  }

  sendPlacementMove(unitId: string, q: number, r: number): void {
    this.socket.send({ type: "placement_edit", kind: "move", unitId, q, r });
    this.store.setState({ selectedUnitId: null });
  }

  confirmPlacement(): void {
    this.socket.send({ type: "placement_edit", kind: "confirm" });
  }

  exitToLobby(): void {
    this.onExit();
  }
}
