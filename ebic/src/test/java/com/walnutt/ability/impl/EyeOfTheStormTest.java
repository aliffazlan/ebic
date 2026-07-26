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

class EyeOfTheStormTest {

    @Test
    void strikesTheSoleAdjacentEnemyEachTurn_permanentlyStackingVulnerability() {
        Unit discharge = new BasicUnit("Discharge", Team.PLAYER_ONE, new UnitStats(30, 40, 10, 900));
        discharge.addAbility(new EyeOfTheStorm(new AbilityDefinition("Eye of the Storm", "passive", "desc",
            Map.of("count", 1.0, "range", 1.0, "damage", 2.0, "bonus_damage", 2.0))));
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(discharge);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(discharge, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(1, 0)));

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        assertEquals(1000 - 2, target.getHealth());

        // A separate, unrelated 10-damage hit should now deal 10 + 2 (one stack of vulnerability) = 12.
        target.takeDamage(state, new DamageEvent(discharge, target, 10));
        assertEquals(1000 - 2 - 12, target.getHealth());

        // Second Eye of the Storm strike: the existing +2 vulnerability also applies to
        // THIS strike itself (base 2 + 2 = 4), then stacks again afterward (now +4 total).
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        assertEquals(1000 - 2 - 12 - 4, target.getHealth());

        target.takeDamage(state, new DamageEvent(discharge, target, 10));
        assertEquals(1000 - 2 - 12 - 4 - 14, target.getHealth());
    }
}
