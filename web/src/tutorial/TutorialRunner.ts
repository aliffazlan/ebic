// The tutorial's step machine. Implements MatchActions itself (TutorialScreen forwards
// every call here 1:1) so Board/Hud need no tutorial-specific knowledge at all - from
// their point of view this looks exactly like a live MatchScreen. Each handler reads
// the CURRENT step's gate to decide: apply the mutation and advance, or reject with a
// wrong-move line and change nothing (mirroring what a real match looks like when the
// server refuses an illegal action).

import { computePlacementLegalTiles } from "./data/placement";
import { buildActionPrompt } from "./legalTargets";
import { computeStepArrows } from "./TutorialArrows";
import type { TutorialGate, TutorialStep, TutorialHost } from "./types";
import type { Attribute } from "../types/contract";
import type { EffectSnapshot, GameStateSnapshot, PlacementStateSnapshot, PromptPayload, VfxEvent } from "../types/contract";
import type { MatchActions } from "../ui/MatchActions";

export class TutorialRunner implements MatchActions {
  private stepIndex = 0;
  private movedBasics = new Set<string>();
  private actedUnits = new Set<string>();
  private placementMovedUnits = new Set<string>();
  private awaitingAttribute = false;
  private busy = false;

  private readonly host: TutorialHost;
  private readonly steps: TutorialStep[];

  constructor(host: TutorialHost, steps: TutorialStep[]) {
    this.host = host;
    this.steps = steps;
  }

  async start(): Promise<void> {
    this.stepIndex = 0;
    await this.enterStep();
  }

  /** Dev-only: jump straight to the step carrying this devCheckpoint number. No-op if not found. */
  async jumpToDevCheckpoint(n: number): Promise<void> {
    const idx = this.steps.findIndex((s) => s.devCheckpoint === n);
    if (idx < 0) return;
    this.stepIndex = idx;
    await this.enterStep();
  }

  private get current(): TutorialStep {
    return this.steps[this.stepIndex];
  }

  private async enterStep(): Promise<void> {
    this.busy = true;
    this.movedBasics.clear();
    this.actedUnits.clear();
    this.placementMovedUnits.clear();
    this.awaitingAttribute = false;
    const step = this.current;
    if (step.onEnter) await step.onEnter({ host: this.host });
    if (step.dialogue && step.dialogue.length > 0) await this.host.showDialogue(step.dialogue);
    this.host.setObjective(step.objective ?? null);
    this.busy = false;
    this.updateArrows();

    if (step.gate.kind === "none") {
      await this.completeGate();
    }
  }

  private updateArrows(): void {
    const state = this.host.store.getState();
    this.host.setArrows(
      computeStepArrows(this.current.id, {
        selectedUnitId: state.selectedUnitId,
        selectedAbilityId: state.selectedAbilityId,
        awaitingAttribute: this.awaitingAttribute,
        actedUnits: this.actedUnits,
        placementMovedUnits: this.placementMovedUnits,
      }),
    );
  }

  private async completeGate(): Promise<void> {
    this.host.setObjective(null);
    this.host.setArrows([]);
    const step = this.current;
    if (step.onAdvance) await step.onAdvance({ host: this.host });
    if (this.stepIndex >= this.steps.length - 1) return;
    this.stepIndex++;
    await this.enterStep();
  }

  private reject(text: string): void {
    this.host.showWrongMove("valor", text);
  }

  private pushSnapshot(snapshot: GameStateSnapshot): void {
    this.host.store.setState({ snapshot, prompt: buildActionPrompt(snapshot), selectedUnitId: null, selectedAbilityId: null });
  }

  // ---- MatchActions ----

  selectUnit(unitId: string | null): void {
    if (this.busy) return;
    this.host.store.setState({ selectedUnitId: unitId, selectedAbilityId: null });
    this.updateArrows();
  }

  selectAbility(abilityId: string | null): void {
    if (this.busy) return;
    this.host.store.setState({ selectedAbilityId: abilityId });
    this.updateArrows();
  }

  castAbility(targetKind: "unit" | "tile" | "none", target?: { unitId?: string; q?: number; r?: number }): void {
    if (this.busy) return;
    const state = this.host.store.getState();
    const unitId = state.selectedUnitId;
    const abilityId = state.selectedAbilityId;
    this.host.store.setState({ selectedAbilityId: null });
    if (!unitId || !abilityId) return;
    const gate = this.current.gate;

    if (abilityId === "move") {
      if (targetKind !== "tile" || target?.q === undefined || target.r === undefined) return;
      this.handleMove(unitId, target.q, target.r, gate);
      return;
    }
    if (abilityId === "attack") {
      if (targetKind !== "unit" || !target?.unitId) return;
      this.handleAttack(unitId, target.unitId, gate);
      return;
    }
    if (
      targetKind === "unit" &&
      target?.unitId &&
      gate.kind === "exact-ability" &&
      unitId === gate.unitId &&
      abilityId === gate.abilityId &&
      target.unitId === gate.targetId
    ) {
      this.applyColdEmbrace(unitId, abilityId, target.unitId);
      void this.completeGate();
      return;
    }
    this.reject("Wrong move, warrior! That's not what we need right now.");
  }

  private handleMove(unitId: string, q: number, r: number, gate: TutorialGate): void {
    const snapshot = this.host.store.getState().snapshot;
    if (!snapshot) return;
    const unit = snapshot.units.find((u) => u.id === unitId);
    if (!unit) return;

    if (gate.kind === "count-basics-moved") {
      if (unit.unitType !== "BASIC") {
        this.reject("Wrong move, warrior! Only our basic units need to advance right now.");
        return;
      }
      this.applyMove(snapshot, unitId, q, r);
      this.movedBasics.add(unitId);
      this.updateArrows();
      if (this.movedBasics.size >= gate.atLeast) void this.completeGate();
      return;
    }
    if (gate.kind === "exact-unit-actions") {
      // Basics always move freely and cost no actions (see the count-basics-moved gate
      // in battle-basics) - letting them through here too is an escape hatch in case the
      // player boxed one in during battle-basics and needs to free it before ending turn.
      if (unit.unitType === "BASIC") {
        this.applyMove(snapshot, unitId, q, r);
        return;
      }
      if (!gate.unitIds.includes(unitId) || this.actedUnits.has(unitId)) {
        this.reject("Wrong move, warrior! Only Valor, Thaddeus, and Evayne act this turn.");
        return;
      }
      this.applyMove(snapshot, unitId, q, r);
      this.actedUnits.add(unitId);
      this.updateArrows();
      if (this.actedUnits.size >= gate.unitIds.length) void this.completeGate();
      return;
    }
    this.reject("Wrong move, warrior! That's not what we need right now.");
  }

  private applyMove(snapshot: GameStateSnapshot, unitId: string, q: number, r: number): void {
    const next = structuredClone(snapshot);
    const unit = next.units.find((u) => u.id === unitId)!;
    unit.q = q;
    unit.r = r;
    unit.hasMovedThisTurn = true;
    const moveCost = unit.abilities.find((a) => a.id === "move")?.moveCost ?? 0;
    next.remainingMoves = Math.max(0, next.remainingMoves - moveCost);
    this.pushSnapshot(next);
  }

  private handleAttack(unitId: string, targetId: string, gate: TutorialGate): void {
    if (gate.kind !== "rigged-attribute" || unitId !== gate.attackerId || targetId !== gate.targetId || this.awaitingAttribute) {
      this.reject("Wrong move, warrior! That's not the attack we planned.");
      return;
    }
    this.awaitingAttribute = true;
    const snapshot = this.host.store.getState().snapshot!;
    const prompt: PromptPayload = {
      kind: "attribute",
      team: snapshot.currentTeam,
      unitId,
      opponentUnitId: targetId,
      selectableAttributes: ["STRENGTH", "AGILITY", "INTELLIGENCE"],
    };
    this.host.store.setState({ prompt, attributeSubmitted: false });
    this.updateArrows();
  }

  endTurn(): void {
    if (this.busy) return;
    if (this.current.gate.kind !== "end-turn") {
      this.reject("Wrong move, warrior! We still have work to do this turn.");
      return;
    }
    void this.completeGate();
  }

  sendAttribute(_value: Attribute): void {
    if (this.busy || !this.awaitingAttribute) return;
    const gate = this.current.gate;
    if (gate.kind !== "rigged-attribute") return;
    this.host.store.setState({ attributeSubmitted: true });
    void this.resolveAttribute(gate.outcome, gate.attackerId, gate.targetId);
  }

  private async resolveAttribute(outcome: "miss" | "kill", attackerId: string, targetId: string): Promise<void> {
    await this.host.delay(700);
    const snapshot = this.host.store.getState().snapshot!;
    const next = structuredClone(snapshot);
    const attacker = next.units.find((u) => u.id === attackerId)!;
    attacker.hasAttackedThisTurn = true;
    const atkCost = attacker.abilities.find((a) => a.id === "attack")?.moveCost ?? 0;
    next.remainingMoves = Math.max(0, next.remainingMoves - atkCost);
    const defender = next.units.find((u) => u.id === targetId)!;

    let events: VfxEvent[];
    if (outcome === "miss") {
      events = [{ type: "damage", abilityId: null, sourceUnitId: attackerId, targetUnitId: targetId, amount: 0, causeLabel: "Attack" }];
    } else {
      const lethal = defender.currentHp;
      defender.currentHp = 0;
      defender.dead = true;
      events = [
        { type: "damage", abilityId: null, sourceUnitId: attackerId, targetUnitId: targetId, amount: lethal, causeLabel: "Attack" },
        { type: "death", abilityId: null, sourceUnitId: null, targetUnitId: targetId, amount: null, causeLabel: null },
      ];
    }

    this.host.playVfxBatch(events, next.currentTeam);
    this.awaitingAttribute = false;
    this.host.store.setState({ attributeSubmitted: false });
    this.pushSnapshot(next);
    void this.completeGate();
  }

  private applyColdEmbrace(casterId: string, abilityId: string, targetId: string): void {
    const snapshot = this.host.store.getState().snapshot!;
    const next = structuredClone(snapshot);
    const caster = next.units.find((u) => u.id === casterId)!;
    const target = next.units.find((u) => u.id === targetId)!;
    const effect: EffectSnapshot = {
      name: "Cold Embrace",
      description: "Sealed in ice - healed for 40 at the start of each of the next 2 turns.",
      category: "BUFF",
      permanent: false,
      remainingTurns: 2,
      statusFlags: [],
      extraInfo: null,
      partnerUnitId: null,
    };
    target.effects.push(effect);
    const ability = caster.abilities.find((a) => a.id === abilityId);
    next.remainingMoves = Math.max(0, next.remainingMoves - (ability?.moveCost ?? 1));
    if (ability) ability.currentCooldown = ability.maxCooldown;
    this.host.playVfxBatch(
      [{ type: "status_applied", abilityId, sourceUnitId: casterId, targetUnitId: targetId, amount: null, causeLabel: null }],
      next.currentTeam,
    );
    this.pushSnapshot(next);
  }

  cancelChoice(): void {
    // No "choice" prompts are ever raised in this tutorial.
  }

  sendChoice(_optionId: string): void {
    // No "choice" prompts are ever raised in this tutorial.
  }

  sendPick(definitionId: string): void {
    if (this.busy) return;
    const gate = this.current.gate;
    if (gate.kind !== "exact-pick") return;
    if (definitionId !== gate.definitionId) {
      this.reject("Wrong move, warrior! That's not who I need you to choose.");
      return;
    }
    this.host.store.setState({ draftRound: null });
    void this.completeGate();
  }

  sendPlacementSwap(unitId: string, targetUnitId: string): void {
    if (this.busy) return;
    const placement = this.host.store.getState().placementState;
    if (!placement || placement.confirmed) return;
    const gate = this.current.gate;
    if (gate.kind === "exact-placement") {
      this.reject("Wrong move, warrior! Thaddeus belongs at the front.");
      return;
    }
    if (gate.kind !== "confirm-only") return;
    const next: PlacementStateSnapshot = structuredClone(placement);
    const a = next.units.find((u) => u.unitId === unitId);
    const b = next.units.find((u) => u.unitId === targetUnitId);
    if (a && b) {
      [a.q, b.q] = [b.q, a.q];
      [a.r, b.r] = [b.r, a.r];
    }
    next.legalTiles = computePlacementLegalTiles(next.units);
    this.host.store.setState({ placementState: next, selectedUnitId: null });
    this.placementMovedUnits.add(unitId).add(targetUnitId);
    this.updateArrows();
  }

  sendPlacementMove(unitId: string, q: number, r: number): void {
    if (this.busy) return;
    const placement = this.host.store.getState().placementState;
    if (!placement || placement.confirmed) return;
    const gate = this.current.gate;
    const legal = computePlacementLegalTiles(placement.units, unitId);
    const isLegal = legal.some((t) => t.q === q && t.r === r);

    if (gate.kind === "exact-placement") {
      if (unitId !== gate.unitId || q !== gate.targetQ || !isLegal) {
        this.reject("Wrong move, warrior! Thaddeus belongs at the front.");
        return;
      }
      const next: PlacementStateSnapshot = structuredClone(placement);
      const unit = next.units.find((u) => u.unitId === unitId)!;
      unit.q = q;
      unit.r = r;
      next.legalTiles = computePlacementLegalTiles(next.units);
      this.host.store.setState({ placementState: next, selectedUnitId: null });
      void this.completeGate();
      return;
    }
    if (gate.kind !== "confirm-only") return;
    if (!isLegal) {
      this.reject("Wrong move, warrior! That ground isn't ours to hold.");
      return;
    }
    const next: PlacementStateSnapshot = structuredClone(placement);
    const unit = next.units.find((u) => u.unitId === unitId);
    if (unit) {
      unit.q = q;
      unit.r = r;
    }
    next.legalTiles = computePlacementLegalTiles(next.units);
    this.host.store.setState({ placementState: next, selectedUnitId: null });
    this.placementMovedUnits.add(unitId);
    this.updateArrows();
  }

  confirmPlacement(): void {
    if (this.busy) return;
    const gate = this.current.gate;
    if (gate.kind !== "confirm-only") {
      this.reject("Wrong move, warrior! Not just yet - arrange our units first.");
      return;
    }
    const placement = this.host.store.getState().placementState;
    if (!placement) return;
    this.host.store.setState({ placementState: { ...placement, confirmed: true } });
    void this.completeGate();
  }

  exitToLobby(): void {
    this.host.exitToLobby();
  }

  // Sandbox tools - the SANDBOX tab never renders here, so nothing can call these.
  openSandboxPicker(): void {}
  chooseSandboxSpawn(): void {}
  startSandboxTool(): void {}
  cancelSandboxTool(): void {}
  sendSandbox(): void {}
}
