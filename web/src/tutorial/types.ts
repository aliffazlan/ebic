// Shared shapes for the scripted tutorial's step machine. See TutorialRunner.ts for
// how a step's gate decides what counts as "the expected action" vs. a wrong move,
// and TutorialScript.ts for the actual ~25-step sequence built from these types.

import type { GameStateStore } from "../state/GameStateStore";
import type { Team, VfxEvent } from "../types/contract";

export type Speaker = "valor" | "harbinger" | "auroth" | "evayne" | "thaddeus";

export interface DialogueLine {
  speaker: Speaker;
  text: string;
}

export type TutorialGate =
  // Pure narration - advances on its own once dialogue finishes.
  | { kind: "none" }
  // Draft: only this exact pick advances; anything else is a wrong move.
  | { kind: "exact-pick"; definitionId: string }
  // Placement: only moving this exact unit onto this exact front-row column advances.
  | { kind: "exact-placement"; unitId: string; targetQ: number }
  // Placement: any rearrangement is accepted; only Confirm advances.
  | { kind: "confirm-only" }
  // Battle: any N distinct BASIC units moving (any destination) advances.
  | { kind: "count-basics-moved"; atLeast: number }
  // Battle: exactly these units may act (move), each once, nothing else.
  | { kind: "exact-unit-actions"; unitIds: string[] }
  // Battle: only ending the turn advances.
  | { kind: "end-turn" }
  // Battle: only this exact caster/ability/target combo advances.
  | { kind: "exact-ability"; unitId: string; abilityId: string; targetId: string }
  // Battle: only this exact attacker attacking this exact target opens the attribute
  // prompt; the attribute the player picks is then ignored - the outcome is scripted.
  | { kind: "rigged-attribute"; outcome: "miss" | "kill"; attackerId: string; targetId: string };

export interface TutorialStepContext {
  host: TutorialHost;
}

export interface TutorialStep {
  id: string;
  /** A stable index dev-only `?tutorialStep=N` jumps to; omitted for steps with nothing worth jumping to directly. */
  devCheckpoint?: number;
  dialogue?: DialogueLine[];
  /** Short persistent reminder of the current task, shown in a banner even after dialogue is dismissed. Omit (or leave undefined) for pure-narration steps with nothing to do. */
  objective?: string;
  gate: TutorialGate;
  onEnter?: (ctx: TutorialStepContext) => void | Promise<void>;
  onAdvance?: (ctx: TutorialStepContext) => void | Promise<void>;
}

/** What TutorialRunner needs from its host screen - kept narrow so the runner has no direct UI/Pixi dependency. */
export interface TutorialHost {
  readonly store: GameStateStore;
  /** Shows the queued lines in the main dialogue box; resolves once the player has clicked through all of them. */
  showDialogue(lines: DialogueLine[]): Promise<void>;
  /** Updates (or, with null, hides) the persistent on-screen objective reminder. */
  setObjective(text: string | null): void;
  /** Fires the small, non-blocking, interrupt-and-replace rejection toast. */
  showWrongMove(speaker: Speaker, text: string): void;
  /** Runs a vfx batch through the same combat-log + animation pipeline a live match uses. */
  playVfxBatch(events: VfxEvent[], resultingTeam: Team): void;
  fadeOut(): Promise<void>;
  fadeIn(): Promise<void>;
  delay(ms: number): Promise<void>;
  exitToLobby(): void;
}
