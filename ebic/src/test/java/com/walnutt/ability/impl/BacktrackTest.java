package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class BacktrackTest {

    @Test
    void healsForDamageTakenDuringThePreviousTurnOnly() {
        Unit chronos = new BasicUnit("Chronos", Team.PLAYER_ONE, new UnitStats(70, 110, 60, 1200));
        Backtrack backtrack = new Backtrack(new AbilityDefinition("Backtrack", "active", "desc",
            Map.of("cooldown", 3.0, "range", 3.0, "backtrack_period", 1.0)));
        chronos.addAbility(backtrack);
        Unit attacker = new BasicUnit("Attacker", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(chronos);
        p2.addUnit(attacker);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(chronos, map.getTile(new Position(0, 0)));

        chronos.getHealthPool().setCurrent(600);
        chronos.takeDamage(state, new DamageEvent(attacker, chronos, 100));
        // Roll over into "last turn" at Chronos's own next turn start.
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        // Damage taken AFTER the rollover shouldn't count toward "last turn" yet.
        chronos.takeDamage(state, new DamageEvent(attacker, chronos, 50));
        int healthBeforeBacktrack = chronos.getHealth();

        backtrack.onUse(state, new TileTarget(map.getTile(new Position(1, 0))));

        assertEquals(new Position(1, 0), chronos.getPosition());
        assertEquals(Math.min(healthBeforeBacktrack + 100, chronos.getMaxHealth()), chronos.getHealth());
    }
}
