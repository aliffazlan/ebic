import type { Attribute, SandboxTool, Team } from "../types/contract";

/** Callbacks Hud/Board use to express user intent; MatchScreen implements and wires them to the socket. */
export interface MatchActions {
  selectUnit(unitId: string | null): void;
  selectAbility(abilityId: string | null): void;
  castAbility(targetKind: "unit" | "tile" | "none", target?: { unitId?: string; q?: number; r?: number }): void;
  endTurn(): void;
  /** `team` says which side is answering - only needed in a sandbox, where this client is both. */
  sendAttribute(value: Attribute, team?: Team): void;
  /** Answers a `choice` prompt (Eureka's gadget dialogue) with the chosen option's id. */
  sendChoice(optionId: string): void;
  /** Closes a `choice` prompt without picking. The cast is abandoned and costs nothing. */
  cancelChoice(): void;
  sendPick(definitionId: string): void;
  sendPlacementSwap(unitId: string, targetUnitId: string): void;
  sendPlacementMove(unitId: string, q: number, r: number): void;
  confirmPlacement(): void;
  exitToLobby(): void;
  // Sandbox tools - only ever reachable from the SANDBOX tab, which only a sandbox match shows.
  /** Opens the unit picker for a side, or closes it with null. */
  openSandboxPicker(team: Team | null): void;
  /** A unit was picked: wait for the tile to spawn it on. */
  chooseSandboxSpawn(team: Team, definitionId: string, name: string): void;
  /** Wait for the unit to remove or heal. */
  startSandboxTool(kind: "remove" | "heal"): void;
  cancelSandboxTool(): void;
  /** Sends a tool use that needs no board click (clear, refill, reset, switch). */
  sendSandbox(tool: SandboxTool): void;
}
