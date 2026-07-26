import type { Attribute } from "../types/contract";

/** Callbacks Hud/Board use to express user intent; MatchScreen implements and wires them to the socket. */
export interface MatchActions {
  selectUnit(unitId: string | null): void;
  selectAbility(abilityId: string | null): void;
  castAbility(targetKind: "unit" | "tile" | "none", target?: { unitId?: string; q?: number; r?: number }): void;
  endTurn(): void;
  sendAttribute(value: Attribute): void;
  sendPick(definitionId: string): void;
  sendPlacement(q: number, r: number): void;
  exitToLobby(): void;
}
