package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class ObjurgationTest {

    @Test
    void preventsDeathOnce_thenAllowsDeathWhileOnCooldown() {
        Unit unit = new BasicUnit("Harbinger", Team.PLAYER_ONE, new UnitStats(10, 10, 50, 100));
        Objurgation objurgation = new Objurgation(new AbilityDefinition(
            "Objurgation", "passive", "desc", Map.of("cooldown", 3.0, "int_to_hp", 0.8)));
        unit.addAbility(objurgation);
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(unit);
        p2.addUnit(enemy);
        GameState state = new GameState(new GameMap(3), List.of(p1, p2), new Random(1));

        unit.getHealthPool().setCurrent(10);
        unit.takeDamage(state, new DamageEvent(enemy, unit, 50)); // would be fatal

        assertFalse(unit.isDead());
        assertEquals(40, unit.getHealth()); // round(0.8 * 50 intelligence) = 40
        assertEquals(0, unit.getAttributeValue(Attribute.INTELLIGENCE)); // consumed
        assertFalse(objurgation.isReady());

        // Still on cooldown - a second fatal hit is not prevented.
        unit.takeDamage(state, new DamageEvent(enemy, unit, 100));
        assertTrue(unit.isDead());
    }
}
