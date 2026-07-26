package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class SnowGolemTest {

    @Test
    void onlyOneGolemAtATime_recastingInstantlyKillsThePrevious() {
        Unit yuki = new BasicUnit("Yuki", Team.PLAYER_ONE, new UnitStats(15, 20, 40, 450));
        SnowGolem summon = new SnowGolem(new AbilityDefinition("Snow Golem", "active", "desc",
            Map.of("cooldown", 16.0, "cast_range", 1.0)));
        yuki.addAbility(summon);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(yuki);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        UnitDefinition golemDef = new UnitDefinition("Snow Golem", "elite", 1000, 60, 40, 15, List.of());
        state.setUnitDefinitions(Map.of("yuki_golem", golemDef));
        state.setAbilityDefinitions(Map.of());
        state.setRemainingMoves(3);
        map.moveUnit(yuki, map.getTile(new Position(0, 0)));

        summon.onUse(state, new TileTarget(map.getTile(new Position(1, 0))));
        Unit firstGolem = map.getTile(new Position(1, 0)).getFirstOccupant();
        assertTrue(p1.getUnits().contains(firstGolem));
        assertFalse(firstGolem.isDead());
        assertEquals(1000, firstGolem.getMaxHealth());

        summon.onUse(state, new TileTarget(map.getTile(new Position(0, 1))));
        Unit secondGolem = map.getTile(new Position(0, 1)).getFirstOccupant();

        assertNotSame(firstGolem, secondGolem);
        assertTrue(firstGolem.isDead(), "recasting should instantly kill the previous golem");
        assertFalse(secondGolem.isDead());
        assertTrue(p1.getUnits().contains(secondGolem));
    }
}
