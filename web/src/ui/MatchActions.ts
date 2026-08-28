import type { Attribute } from "../types/contract";

/** Callbacks Hud/Board use to express user intent; MatchScreen implements and wires them to the socket. */
export interface MatchActions {
  selectUnit(unitId: string | null): void;
  selectAbility(abilityId: string | null): void;
  castAbility(targetKind: "unit" | "tile" | "none", target?: { unitId?: string; q?: number; r?: number }): void;
  endTurn(): void;
  sendAttribute(value: Attribute): void;
  /** Answers a `choice` prompt (Eureka's gadget dialogue) with the chosen option's id. */
  sendChoice(optionId: string): void;
  sendPick(definitionId: string): void;
  sendPlacementSwap(unitId: string, targetUnitId: string): void;
  sendPlacementMove(unitId: string, q: number, r: number): void;
  confirmPlacement(): void;
  exitToLobby(): void;
}
