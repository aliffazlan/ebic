package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
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

class DecayTest {

    @Test
    void permanentlyStealsMaxHealthAndStrengthFromAdjacentUnits_bothAllyAndEnemy() {
        Unit dirge = new BasicUnit("Dirge", Team.PLAYER_ONE, new UnitStats(10, 0, 0, 100));
        Decay decay = new Decay(new AbilityDefinition("Decay", "passive", "desc",
            Map.of("radius", 1.0, "health_steal", 5.0, "bonus_increase", 2.0)));
        dirge.addAbility(decay);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(20, 0, 0, 100));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(20, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(dirge);
        p1.addUnit(ally);
        p2.addUnit(enemy);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(dirge, map.getTile(new Position(1, 1)));
        map.moveUnit(ally, map.getTile(new Position(0, 1)));
        map.moveUnit(enemy, map.getTile(new Position(2, 1)));

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));

        // Dirge steals from BOTH adjacent units (one loop iteration each): +5 max HP and +2
        // strength per victim = +10/+4 total for Dirge.
        assertEquals(110, dirge.getMaxHealth());
        assertEquals(14, dirge.getAttributeValue(Attribute.STRENGTH));
        assertEquals(95, ally.getMaxHealth());
        assertEquals(18, ally.getAttributeValue(Attribute.STRENGTH));
        assertEquals(95, enemy.getMaxHealth());
        assertEquals(18, enemy.getAttributeValue(Attribute.STRENGTH));
        // Direct health damage on top of the max-health clamp: 100 -> clamped to 95 -> -5 damage = 90.
        assertEquals(90, ally.getHealth());
        assertEquals(90, enemy.getHealth());
        // HealthPool ceiling actually moved, not just the reported effective stat.
        ally.heal(state, 1000);
        assertEquals(95, ally.getHealth());
        dirge.heal(state, 1000);
        assertEquals(110, dirge.getHealth());
    }

    /** Same trap as Static Link: a corpse keeps its position and keeps getting turn hooks. */
    @Test
    void aDeadDirgeStopsDraining() {
        Unit dirge = new BasicUnit("Dirge", Team.PLAYER_ONE, new UnitStats(30, 0, 0, 200));
        dirge.addAbility(new Decay(new AbilityDefinition("Decay", "passive", "desc",
            Map.of("radius", 1.0, "health_steal", 5.0, "str_steal", 2.0))));
        Unit neighbour = new BasicUnit("Neighbour", Team.PLAYER_TWO, new UnitStats(40, 0, 0, 1000));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(dirge);
        p2.addUnit(neighbour);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(dirge, map.getTile(new Position(0, 0)));
        map.moveUnit(neighbour, map.getTile(new Position(1, 0)));

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        assertTrue(neighbour.getAttributeValue(Attribute.STRENGTH) < 40, "drains while alive");

        dirge.takeDamage(state, new DamageEvent(neighbour, dirge, 9999));
        assertTrue(dirge.isDead());

        int strengthAtDeath = neighbour.getAttributeValue(Attribute.STRENGTH);
        int healthAtDeath = neighbour.getHealth();
        for (int turn = 0; turn < 3; turn++) {
            state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        }

        assertEquals(strengthAtDeath, neighbour.getAttributeValue(Attribute.STRENGTH),
            "a corpse must not keep stealing stats");
        assertEquals(healthAtDeath, neighbour.getHealth(), "nor keep dealing damage");
    }
}
