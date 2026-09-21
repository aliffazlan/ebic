// The scripted, backend-free tutorial match. Structurally identical to FixtureScreen
// (owns Board/Hud/TurnBanner/IndicatorScheduler/GameStateStore, no socket) but driven
// by a TutorialRunner walking TUTORIAL_SCRIPT instead of a static fixture. Every
// MatchActions call is forwarded to the runner unchanged - Board and Hud have no idea
// this isn't a live match, which is exactly what lets them run unmodified.

import { Application } from "pixi.js";
import { Board } from "../board/Board";
import type { AxialCoord } from "../hex/HexMath";
import { GameStateStore } from "../state/GameStateStore";
import { TutorialRunner } from "../tutorial/TutorialRunner";
import { TUTORIAL_SCRIPT } from "../tutorial/TutorialScript";
import type { ArrowTarget, DialogueLine, Speaker, TutorialHost } from "../tutorial/types";
import type { Attribute, Team, UnitSnapshot, VfxEvent } from "../types/contract";
import { IndicatorScheduler } from "../vfx/IndicatorScheduler";
import { scheduleVfxBatch } from "../vfx/ScheduleVfxBatch";
import { CHROME_VOID_HEX } from "./Colors";
import { DialogueBox } from "./DialogueBox";
import { ObjectiveBanner } from "./ObjectiveBanner";
import { TutorialArrowOverlay } from "./TutorialArrowOverlay";
import { createFadeOverlay, delay, FADE_MS, HOLD_MS, nextFrames, runOverlayFade } from "./ScreenTransition";
import { Hud } from "./Hud";
import type { MatchActions } from "./MatchActions";
import type { Screen } from "./Screen";
import { TurnBanner } from "./TurnBanner";
import { WrongMoveToast } from "./WrongMoveToast";

export class TutorialScreen implements Screen, MatchActions, TutorialHost {
  readonly store: GameStateStore;
  private root: HTMLElement;
  private onExit: () => void;
  private runner: TutorialRunner;

  private container: HTMLDivElement | null = null;
  private app: Application | null = null;
  private board: Board | null = null;
  private hud: Hud | null = null;
  private turnBanner: TurnBanner | null = null;
  private objectiveBanner: ObjectiveBanner | null = null;
  private arrowOverlay: TutorialArrowOverlay | null = null;
  // setArrows can fire (via a ?tutorialStep= dev-jump) before initPixi's async
  // app.init() resolves and this.board exists - cached so initPixi can replay the
  // board-side targets the instant Board is actually constructed.
  private lastArrowTargets: ArrowTarget[] = [];
  private dialogueBox: DialogueBox | null = null;
  private wrongMoveToast: WrongMoveToast | null = null;
  private indicators = new IndicatorScheduler();
  private activeFadeOverlay: HTMLDivElement | null = null;

  constructor(root: HTMLElement, playerName: string, onExit: () => void) {
    this.root = root;
    this.onExit = onExit;
    this.store = new GameStateStore("PLAYER_ONE", playerName, "Harbinger's Legion");
    this.runner = new TutorialRunner(this, TUTORIAL_SCRIPT);
  }

  // ---- Screen ----

  mount(): void {
    const container = document.createElement("div");
    container.className = "match-screen";
    const canvasHost = document.createElement("div");
    canvasHost.className = "canvas-host";
    const hudHost = document.createElement("div");
    hudHost.className = "hud-host";
    const logHost = document.createElement("div");
    logHost.className = "log-host";
    container.append(logHost, canvasHost, hudHost);
    this.root.appendChild(container);
    this.container = container;

    this.turnBanner = new TurnBanner(canvasHost);
    this.objectiveBanner = new ObjectiveBanner(canvasHost);
    this.arrowOverlay = new TutorialArrowOverlay(canvasHost);
    void this.initPixi(canvasHost);
    this.hud = new Hud(hudHost, logHost, "tutorial", this.store, this);
    this.dialogueBox = new DialogueBox(container);
    this.wrongMoveToast = new WrongMoveToast(container);
    this.store.setState({ connected: true });

    const stepParam = new URLSearchParams(location.search).get("tutorialStep");
    const stepN = stepParam === null ? NaN : Number(stepParam);
    if (!Number.isNaN(stepN)) {
      void this.runner.jumpToDevCheckpoint(stepN);
    } else {
      void this.runner.start();
    }
  }

  unmount(): void {
    this.dialogueBox?.destroy();
    this.wrongMoveToast?.destroy();
    this.indicators.clear();
    this.turnBanner?.destroy();
    this.turnBanner = null;
    this.objectiveBanner?.destroy();
    this.objectiveBanner = null;
    this.arrowOverlay?.destroy();
    this.arrowOverlay = null;
    this.hud?.destroy();
    this.board?.destroy();
    this.app?.destroy(true, { children: true });
    this.activeFadeOverlay?.remove();
    this.activeFadeOverlay = null;
    this.container?.remove();
    this.container = null;
  }

  getElement(): HTMLElement | null {
    return this.container;
  }

  private async initPixi(canvasHost: HTMLDivElement): Promise<void> {
    const app = new Application();
    await app.init({ background: CHROME_VOID_HEX, resizeTo: canvasHost, antialias: true });
    canvasHost.appendChild(app.canvas);
    this.app = app;
    this.board = new Board(app, this.store, {
      onTileClick: (coord) => this.handleTileClick(coord),
      onUnitClick: (unit) => this.handleUnitClick(unit),
    });
    app.stage.addChild(this.board.root);
    this.board.setObjectiveArrows(
      this.lastArrowTargets.filter((t): t is Extract<ArrowTarget, { kind: "unit" | "tile" }> => t.kind !== "dom"),
    );
  }

  // ---- Board click routing (mirrors MatchScreen.ts, minus multi-stage casts - unused here) ----

  private handleTileClick(coord: AxialCoord): void {
    const state = this.store.getState();
    if (state.placementState) {
      if (state.placementState.confirmed) return;
      if (state.selectedUnitId) this.sendPlacementMove(state.selectedUnitId, coord.q, coord.r);
      return;
    }
    if (state.selectedAbilityId && state.selectedUnitId) {
      this.castAtCoord(coord);
      return;
    }
    this.selectUnit(null);
  }

  private handleUnitClick(unit: UnitSnapshot): void {
    const state = this.store.getState();
    if (state.placementState) {
      if (state.placementState.confirmed) return;
      if (state.selectedUnitId === unit.id) {
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
      this.castAtCoord({ q: unit.q, r: unit.r }, unit.id);
      return;
    }
    this.selectUnit(unit.id);
  }

  private castAtCoord(coord: AxialCoord, clickedUnitId?: string): void {
    const state = this.store.getState();
    if (!state.selectedUnitId || !state.selectedAbilityId) return;
    const prompt = state.prompt;
    const legal = prompt && prompt.kind === "action" ? prompt.legalTargets?.[state.selectedUnitId]?.[state.selectedAbilityId] : undefined;
    const takesUnits = !legal || legal.unitIds.length > 0;
    const takesTiles = !legal || legal.tiles.length > 0;

    if (clickedUnitId) {
      if (!takesUnits && takesTiles) {
        this.castAbility("tile", { q: coord.q, r: coord.r });
      } else {
        this.castAbility("unit", { unitId: clickedUnitId });
      }
      return;
    }
    if (!takesTiles && takesUnits) {
      const occupant = this.store.getState().snapshot?.units.find((u) => u.q === coord.q && u.r === coord.r && !u.dead);
      if (occupant) {
        this.castAbility("unit", { unitId: occupant.id });
        return;
      }
    }
    this.castAbility("tile", { q: coord.q, r: coord.r });
  }

  // ---- MatchActions: thin forwards into the runner ----

  selectUnit(unitId: string | null): void {
    this.runner.selectUnit(unitId);
  }
  selectAbility(abilityId: string | null): void {
    this.runner.selectAbility(abilityId);
  }
  castAbility(targetKind: "unit" | "tile" | "none", target?: { unitId?: string; q?: number; r?: number }): void {
    this.runner.castAbility(targetKind, target);
  }
  endTurn(): void {
    this.runner.endTurn();
  }
  sendAttribute(value: Attribute): void {
    this.runner.sendAttribute(value);
  }
  sendChoice(optionId: string): void {
    this.runner.sendChoice(optionId);
  }
  cancelChoice(): void {
    this.runner.cancelChoice();
  }
  sendPick(definitionId: string): void {
    this.runner.sendPick(definitionId);
  }
  sendPlacementSwap(unitId: string, targetUnitId: string): void {
    this.runner.sendPlacementSwap(unitId, targetUnitId);
  }
  sendPlacementMove(unitId: string, q: number, r: number): void {
    this.runner.sendPlacementMove(unitId, q, r);
  }
  confirmPlacement(): void {
    this.runner.confirmPlacement();
  }
  exitToLobby(): void {
    this.onExit();
  }

  // ---- TutorialHost ----

  showDialogue(lines: DialogueLine[]): Promise<void> {
    return this.dialogueBox!.showLines(lines);
  }

  setObjective(text: string | null): void {
    this.objectiveBanner?.setText(text);
  }

  setArrows(targets: ArrowTarget[]): void {
    this.lastArrowTargets = targets;
    const boardTargets = targets.filter(
      (t): t is Extract<ArrowTarget, { kind: "unit" | "tile" }> => t.kind !== "dom",
    );
    const domTargets = targets
      .filter((t): t is Extract<ArrowTarget, { kind: "dom" }> => t.kind === "dom")
      .map((t) => ({ selector: t.selector, direction: t.direction }));
    this.board?.setObjectiveArrows(boardTargets);
    this.arrowOverlay?.setTargets(domTargets);
  }

  showWrongMove(speaker: Speaker, text: string): void {
    this.wrongMoveToast?.show(speaker, text);
  }

  playVfxBatch(events: VfxEvent[], resultingTeam: Team): void {
    this.store.appendCombatLog(events);
    scheduleVfxBatch(events, {
      indicators: this.indicators,
      playVfx: (evts) => this.board?.playVfx(evts),
      playAttackAnimation: (from, to, spec, onComplete) => this.board?.playAttackAnimation(from, to, spec, onComplete),
      showIndicators: (specs) => this.board?.showIndicators(specs),
      showIndicatorAt: (pos, spec) => this.board?.showIndicatorAt(pos, spec),
      resolveUnitPosition: (unitId) => this.board?.resolveUnitPosition(unitId) ?? null,
      beginPendingHpChange: (unitId) => this.board?.beginPendingHpChange(unitId),
      sourceDefinitionId: (event) => this.store.getState().snapshot?.units.find((u) => u.id === event.sourceUnitId)?.definitionId ?? null,
    });
    this.store.commitCombatLog(resultingTeam);
  }

  async fadeOut(): Promise<void> {
    const overlay = createFadeOverlay();
    await runOverlayFade(overlay, "fade-overlay-in", FADE_MS);
    overlay.style.opacity = "1";
    overlay.classList.remove("fade-overlay-in");
    await delay(HOLD_MS);
    this.activeFadeOverlay = overlay;
  }

  async fadeIn(): Promise<void> {
    const overlay = this.activeFadeOverlay;
    this.activeFadeOverlay = null;
    if (!overlay) return;
    await nextFrames(2);
    await runOverlayFade(overlay, "fade-overlay-out", FADE_MS);
    overlay.remove();
  }

  delay(ms: number): Promise<void> {
    return delay(ms);
  }
}
