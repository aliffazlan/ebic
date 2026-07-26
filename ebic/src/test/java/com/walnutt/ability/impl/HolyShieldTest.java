package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class HolyShieldTest {

    @Test
    void barrierAbsorbsDamageBeforeHealth_thenOnBreakClearsDebuffsAndBlastsNearbyEnemies() {
        Unit thaddeus = new BasicUnit("Thaddeus", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        HolyShield shield = new HolyShield(new AbilityDefinition("Holy Shield", "active", "desc", Map.of(
            "cooldown", 4.0, "cast_range", 2.0, "duration", 3.0, "barrier_hp", 50.0, "damage", 30.0, "radius", 1.0)));
        thaddeus.addAbility(shield);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 200));
        ally.addEffect(new StatusEffect("Poisoned", 5, com.walnutt.status.EffectCategory.DEBUFF, StatusFlag.SILENCED));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 200));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(thaddeus);
        p1.addUnit(ally);
        p2.addUnit(enemy);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(thaddeus, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(0, 1)));
        map.moveUnit(enemy, map.getTile(new Position(1, 1)));

        shield.onUse(state, new UnitTarget(ally));

        // First hit: 30 damage, fully absorbed by the 50 HP barrier - no real health lost, debuff still up.
        ally.takeDamage(state, new DamageEvent(enemy, ally, 30));
        assertEquals(200, ally.getHealth());
        assertTrue(ally.hasStatus(StatusFlag.SILENCED));

        // Second hit: 30 more damage - only 20 HP of barrier left, so 10 spills over to real health,
        // and the barrier breaking clears the debuff + blasts the nearby enemy.
        ally.takeDamage(state, new DamageEvent(enemy, ally, 30));
        assertEquals(190, ally.getHealth());
        assertFalse(ally.hasStatus(StatusFlag.SILENCED), "barrier breaking should have dispelled the debuff");
        assertEquals(200 - 30, enemy.getHealth(), "nearby enemy should take the break damage");
    }
}
