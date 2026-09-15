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
import com.walnutt.effect.impl.BarrierEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * Base-kit Objurgation is a conditional barrier below hp_threshold health, not a
 * guarantee - see HarbingerUpgradeTest for the upgrade's guaranteed fatal-blow save,
 * which this base kit deliberately does not provide.
 */
class ObjurgationTest {

    @Test
    void barrierAbsorbsAHitBelowThreshold_thenDoesNotReprocWhileOnCooldown() {
        Unit unit = new BasicUnit("Harbinger", Team.PLAYER_ONE, new UnitStats(10, 10, 50, 100));
        Objurgation objurgation = new Objurgation(new AbilityDefinition("Objurgation", "passive", "desc",
            Map.of("cooldown", 4.0, "int_to_hp", 1.0, "int_consumed", 0.5)));
        unit.addAbility(objurgation);
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(unit);
        p2.addUnit(enemy);
        GameState state = new GameState(new GameMap(3), List.of(p1, p2), new Random(1));

        unit.getHealthPool().setCurrent(10);
        // 50% of 50 intelligence, at 1 hp per point, is a 25 hp barrier - enough to fully
        // absorb this 15-damage hit, with 10 hp of it left standing afterwards.
        unit.takeDamage(state, new DamageEvent(enemy, unit, 15));

        assertFalse(unit.isDead());
        assertEquals(10, unit.getHealth(), "the barrier ate the whole hit");
        assertEquals(25, unit.getAttributeValue(Attribute.INTELLIGENCE), "half burned regardless of how much of the barrier was spent");
        assertEquals(10, unit.getActiveEffect(BarrierEffect.class).orElseThrow().getRemainingBarrierHp());
        assertFalse(objurgation.isReady());

        // Still on cooldown - Objurgation itself does not reproc a fresh barrier (no further
        // intelligence burned), but the 10 hp left over from the first proc is its own
        // BarrierEffect and keeps absorbing independently until it runs out.
        unit.takeDamage(state, new DamageEvent(enemy, unit, 15));
        assertEquals(5, unit.getHealth(), "the leftover 10 hp barrier absorbed 10 of this 15-damage hit");
        assertEquals(25, unit.getAttributeValue(Attribute.INTELLIGENCE), "no further intelligence burned while on cooldown");
    }

    /** A hit bigger than the barrier's pool is only ever partially mitigated - no guarantee. */
    @Test
    void doesNotGuaranteeSurvivingAHitBiggerThanTheBarrier() {
        Unit unit = new BasicUnit("Harbinger", Team.PLAYER_ONE, new UnitStats(10, 10, 80, 100));
        unit.addAbility(new Objurgation(new AbilityDefinition("Objurgation", "passive", "desc",
            Map.of("cooldown", 4.0, "int_to_hp", 1.0, "int_consumed", 0.5))));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(unit);
        p2.addUnit(enemy);
        GameState state = new GameState(new GameMap(3), List.of(p1, p2), new Random(1));

        unit.getHealthPool().setCurrent(5);
        // 40 hp barrier (half of 80 intelligence) against 500 incoming damage: mitigates,
        // does not save.
        unit.takeDamage(state, new DamageEvent(enemy, unit, 500));

        assertTrue(unit.isDead());
    }
}
