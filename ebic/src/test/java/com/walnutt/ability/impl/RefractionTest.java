package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

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

class RefractionTest {

    @Test
    void redirectsDamageToAnAdjacentUnit_limitedUsesPerTurn() {
        Unit lanaya = new BasicUnit("Lanaya", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        Refraction refraction = new Refraction(new AbilityDefinition("Refraction", "passive", "desc",
            Map.of("count", 1.0)));
        lanaya.addAbility(refraction);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        Unit attacker = new BasicUnit("Attacker", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(lanaya);
        p1.addUnit(ally);
        p2.addUnit(attacker);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(lanaya, map.getTile(new Position(1, 1)));
        map.moveUnit(ally, map.getTile(new Position(0, 1))); // lanaya's only adjacent unit

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE)); // resets uses to 1

        lanaya.takeDamage(state, new DamageEvent(attacker, lanaya, 40));
        assertEquals(100, lanaya.getHealth(), "damage should have been redirected away from Lanaya");
        assertEquals(60, ally.getHealth(), "the adjacent ally should have taken it instead");

        // Second hit this turn: no uses left, so it applies normally.
        lanaya.takeDamage(state, new DamageEvent(attacker, lanaya, 25));
        assertEquals(75, lanaya.getHealth());
        assertEquals(60, ally.getHealth());
    }
}
