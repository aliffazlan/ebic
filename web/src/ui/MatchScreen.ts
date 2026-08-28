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
        // Detect a PLAYER_TWO -> PLAYER_ONE transition (one full round just
        // completed) before applying the new snapshot - see the store
        // method's own doc comment for why the very first "state" message
        // doesn't count.
        this.store.startNewCombatLogPageIfRoundJustCompleted(msg.payload.currentTeam);
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
          this.store.setState({ selectedAbilityId: null, attributeSubmitted: false, multiPrimaryUnitId: null });
          this.pendingAttributePromptTimer = window.setTimeout(() => {
            this.pendingAttributePromptTimer = null;
            this.store.setState({ prompt: payload });
          }, ATTRIBUTE_STROBE_MS);
        } else {
          this.store.setState({
            prompt: msg.payload,
            selectedAbilityId: null,
            attributeSubmitted: false,
            multiPrimaryUnitId: null,
          });
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

  /**
   * A two-part cast needs two clicks: first the unit to move, then where to put it. The
   * prompt's `multi` block drives both stages, so there is no per-ability knowledge here -
   * any future ability sending a `multi` block gets the same flow for free.
   *
   * Returns true if the click was consumed by this flow.
   */
  private handleMultiStageClick(coord: AxialCoord, clickedUnitId?: string): boolean {
    const state = this.store.getState();
    if (!state.selectedUnitId || !state.selectedAbilityId) return false;
    const prompt = state.prompt;
    if (!prompt || prompt.kind !== "action") return false;
    const multi = prompt.legalTargets?.[state.selectedUnitId]?.[state.selectedAbilityId]?.multi;
    if (!multi) return false;

    if (!state.multiPrimaryUnitId) {
      // Stage one. Accept a click on the unit itself or on the ground it stands on, the
      // same "resolve what the player meant" courtesy castAtCoord extends elsewhere.
      const candidate =
        clickedUnitId ??
        this.store.getState().snapshot?.units.find((u) => u.q === coord.q && u.r === coord.r && !u.dead)?.id;
      if (candidate && multi.primaryUnitIds.includes(candidate)) {
        this.store.setState({ multiPrimaryUnitId: candidate });
      }
      return true;
    }

    // Stage two. Clicking the chosen unit again backs out rather than trapping the player.
    if (clickedUnitId && clickedUnitId === state.multiPrimaryUnitId) {
      this.store.setState({ multiPrimaryUnitId: null });
      return true;
    }
    this.socket.send({
      type: "action",
      kind: "ability",
      unitId: state.selectedUnitId,
      abilityId: state.selectedAbilityId,
      targetKind: "multi",
      targetUnitId: state.multiPrimaryUnitId,
      q: coord.q,
      r: coord.r,
    });
    this.store.setState({ selectedAbilityId: null, prompt: null, multiPrimaryUnitId: null });
    return true;
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
      if (this.handleMultiStageClick(coord)) return;
      this.castAtCoord(coord);
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
      if (this.handleMultiStageClick({ q: unit.q, r: unit.r }, unit.id)) return;
      this.castAtCoord({ q: unit.q, r: unit.r }, unit.id);
      return;
    }

    this.selectUnit(unit.id);
  }

  // ---- MatchActions ----

  selectUnit(unitId: string | null): void {
    this.store.setState({ selectedUnitId: unitId, selectedAbilityId: null, multiPrimaryUnitId: null });
  }

  selectAbility(abilityId: string | null): void {
    // Changing or cancelling the ability abandons any half-finished two-stage pick.
    this.store.setState({ selectedAbilityId: abilityId, multiPrimaryUnitId: null });
    if (abilityId) this.castImmediatelyIfSelfTargeted(abilityId);
  }

  /**
   * A self-cast has exactly one possible outcome, so making the player select it and
   * then confirm with a "no target" button is pure ceremony - fire it straight away.
   *
   * Gated on the ability accepting a no-target cast and offering no unit or tile
   * choices at all; anything that could also be aimed somewhere still waits for a click.
   */
  private castImmediatelyIfSelfTargeted(abilityId: string): void {
    const state = this.store.getState();
    const prompt = state.prompt;
    if (!prompt || prompt.kind !== "action" || !state.selectedUnitId) return;
    const legal = prompt.legalTargets?.[state.selectedUnitId]?.[abilityId];
    if (!legal || !legal.noTarget) return;
    if (legal.unitIds.length > 0 || legal.tiles.length > 0) return;
    this.castAbility("none");
  }

  /**
   * Resolves what the player *meant* by a click, rather than making them hit the exact
   * shape the ability wants.
   *
   * The problem this solves: Eruption targets a tile, but a tile with a unit standing on
   * it swallows the click as a unit click, so casting on an occupied tile used to mean
   * hunting for a stray pixel of hex not covered by the sprite. The reverse is just as
   * annoying for unit-targeted spells.
   *
   * The ability's own legalTargets tells us which shape it accepts, so this stays
   * data-driven - there's no per-ability knowledge here, and an ability accepting both
   * shapes still gets whatever the player literally clicked.
   */
  private castAtCoord(coord: AxialCoord, clickedUnitId?: string): void {
    const state = this.store.getState();
    if (!state.selectedUnitId || !state.selectedAbilityId) return;
    const prompt = state.prompt;
    const legal =
      prompt && prompt.kind === "action"
        ? prompt.legalTargets?.[state.selectedUnitId]?.[state.selectedAbilityId]
        : undefined;

    const takesUnits = !legal || legal.unitIds.length > 0;
    const takesTiles = !legal || legal.tiles.length > 0;

    if (clickedUnitId) {
      // Clicked a unit. If this spell only lands on tiles, aim at the tile it occupies.
      if (!takesUnits && takesTiles) {
        this.castAbility("tile", { q: coord.q, r: coord.r });
      } else {
        this.castAbility("unit", { unitId: clickedUnitId });
      }
      return;
    }

    // Clicked bare ground. If this spell only lands on units, redirect to whoever is
    // standing there - but only if someone actually is.
    if (!takesTiles && takesUnits) {
      const occupant = this.store
        .getState()
        .snapshot?.units.find((u) => u.q === coord.q && u.r === coord.r && !u.dead);
      if (occupant) {
        this.castAbility("unit", { unitId: occupant.id });
        return;
      }
    }
    this.castAbility("tile", { q: coord.q, r: coord.r });
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
    this.store.setState({ selectedAbilityId: null, prompt: null, multiPrimaryUnitId: null });
  }

  endTurn(): void {
    this.socket.send({ type: "action", kind: "end_turn" });
    this.store.setState({ prompt: null, multiPrimaryUnitId: null });
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

  sendChoice(optionId: string): void {
    this.socket.send({ type: "choice", optionId });
    // Same optimistic clear as sendAttribute: the dialogue was raised mid-cast, so the
    // next thing this client hears may be a state/vfx push rather than a fresh prompt.
    this.store.setState({ prompt: null });
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
