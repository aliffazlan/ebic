import { describe, expect, it } from "vitest";
import { GameStateStore } from "./GameStateStore";
import type { UnitSnapshot, VfxEvent } from "../types/contract";

function unit(id: string, name: string): UnitSnapshot {
  return {
    id, name, team: "PLAYER_ONE", definitionId: name.toLowerCase(), unitType: "CHAMPION",
    q: 0, r: 0, currentHp: 100, maxHp: 100, currentBarrierHp: 0, maxBarrierHp: 0,
    strength: 10, agility: 10, intelligence: 10, attackRange: 1, minAttackRange: 0,
    dead: false, hasMovedThisTurn: false, hasAttackedThisTurn: false,
    statusFlags: [], abilities: [], effects: [],
  };
}

function damage(amount: number): VfxEvent {
  return { type: "damage", abilityId: null, sourceUnitId: "a", targetUnitId: "b", amount, causeLabel: "Attack" };
}

describe("GameStateStore combat log replay (spectator backfill)", () => {
  it("replaying the same tick sequence on a fresh store reconstructs identical combatLogPages", () => {
    // The exact per-tick sequence the server would send: a batch of vfx events paired with
    // that tick's currentTeam - see WebRenderer.render()/ChannelHub.recordCombatLogBatch on
    // the Java side. Turn 3 rolls a round over (PLAYER_TWO -> PLAYER_ONE); turn 4 has no vfx
    // at all (a plain move), which must still be recorded so its round boundary isn't lost.
    const ticks: { events: VfxEvent[]; currentTeam: "PLAYER_ONE" | "PLAYER_TWO" }[] = [
      { events: [damage(10)], currentTeam: "PLAYER_ONE" },
      { events: [damage(20)], currentTeam: "PLAYER_TWO" },
      { events: [damage(30)], currentTeam: "PLAYER_ONE" },
      { events: [], currentTeam: "PLAYER_TWO" },
    ];

    const live = new GameStateStore("PLAYER_ONE", "hostplayer", "guestplayer");
    live.setState({ snapshot: { currentTeam: "PLAYER_ONE", remainingMoves: 3, gameOver: false, mapRadius: 5, mapRowLimit: 5, units: [unit("a", "Valor"), unit("b", "Dirge")], tileEffects: [] } });
    for (const tick of ticks) {
      live.appendCombatLog(tick.events);
      live.commitCombatLog(tick.currentTeam);
    }

    const replayed = new GameStateStore(null, "hostplayer", "guestplayer");
    replayed.setState({ snapshot: { currentTeam: "PLAYER_ONE", remainingMoves: 3, gameOver: false, mapRadius: 5, mapRowLimit: 5, units: [unit("a", "Valor"), unit("b", "Dirge")], tileEffects: [] } });
    for (const tick of ticks) {
      replayed.appendCombatLog(tick.events);
      replayed.commitCombatLog(tick.currentTeam);
    }

    expect(replayed.getState().combatLogPages).toEqual(live.getState().combatLogPages);
    expect(live.getState().combatLogPages).toHaveLength(2);
  });
});

describe("GameStateStore spectator support", () => {
  it("a null yourTeam derives isSpectator: true and keeps both player names", () => {
    const store = new GameStateStore(null, "hostplayer", "guestplayer");
    const state = store.getState();

    expect(state.yourTeam).toBeNull();
    expect(state.isSpectator).toBe(true);
    expect(state.playerOneName).toBe("hostplayer");
    expect(state.playerTwoName).toBe("guestplayer");
  });

  it("a real yourTeam derives isSpectator: false", () => {
    const store = new GameStateStore("PLAYER_ONE", "hostplayer", "guestplayer");
    const state = store.getState();

    expect(state.yourTeam).toBe("PLAYER_ONE");
    expect(state.isSpectator).toBe(false);
  });
});
