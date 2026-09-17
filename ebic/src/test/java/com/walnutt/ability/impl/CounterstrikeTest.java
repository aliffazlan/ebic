package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.CounterstrikeBarrierEffect;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class CounterstrikeTest {

    private static GameState fixture(Unit attacker, Unit counterer) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(attacker);
        p2.addUnit(counterer);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(attacker, map.getTile(new Position(0, 0)));
        map.moveUnit(counterer, map.getTile(new Position(1, 0)));
        return state;
    }

    @Test
    void freeCounterAttack_reusesOriginalAttributeChoices_reducedDamageWithBarrier() {
        Unit attacker = new BasicUnit("Attacker", Team.PLAYER_ONE, new UnitStats(30, 0, 0, 100));
        Unit counterer = new BasicUnit("Valor", Team.PLAYER_TWO, new UnitStats(50, 0, 0, 200));
        counterer.addAbility(new Counterstrike(new AbilityDefinition(
            "Counterstrike", "passive", "desc",
            Map.of("damage_multiplier", 0.5, "lifesteal", 1.0, "barrier_duration", 3.0))));
        counterer.getHealthPool().setCurrent(100);
        GameState state = fixture(attacker, counterer);

        // Same-attribute encounter: original damage = max(0, 30 - 50) = 0, so the only
        // damage change we observe comes from the automatic counter-attack.
        CombatEngine.performAttack(state, attacker, counterer, Attribute.STRENGTH, Attribute.STRENGTH);

        // Counter: max(0, 50 - 30) = 20, at 0.5x multiplier -> 10 dealt, 100% of that -> a
        // 10 HP barrier instead of a heal.
        assertEquals(100 - 10, attacker.getHealth());
        assertEquals(100, counterer.getHealth(), "no longer heals raw HP");
        CounterstrikeBarrierEffect barrier = counterer.getActiveEffect(CounterstrikeBarrierEffect.class)
            .orElseThrow(() -> new AssertionError("expected a barrier to be granted"));
        assertEquals(10, barrier.getRemainingBarrierHp());
        assertEquals(3, barrier.getRemainingTurns());
    }

    @Test
    void repeatProcs_addOntoTheSameBarrierAndRefreshItsDuration() {
        Unit attacker = new BasicUnit("Attacker", Team.PLAYER_ONE, new UnitStats(30, 0, 0, 100));
        Unit counterer = new BasicUnit("Valor", Team.PLAYER_TWO, new UnitStats(50, 0, 0, 200));
        counterer.addAbility(new Counterstrike(new AbilityDefinition(
            "Counterstrike", "passive", "desc",
            Map.of("damage_multiplier", 0.5, "lifesteal", 1.0, "barrier_duration", 3.0))));
        GameState state = fixture(attacker, counterer);

        CombatEngine.performAttack(state, attacker, counterer, Attribute.STRENGTH, Attribute.STRENGTH);
        CounterstrikeBarrierEffect first = counterer.getActiveEffect(CounterstrikeBarrierEffect.class)
            .orElseThrow(() -> new AssertionError("expected a barrier to be granted"));
        first.setRemainingTurns(1);

        CombatEngine.performAttack(state, attacker, counterer, Attribute.STRENGTH, Attribute.STRENGTH);

        assertTrue(counterer.getEffects().stream()
            .filter(CounterstrikeBarrierEffect.class::isInstance).count() == 1,
            "a repeat proc grows the same barrier rather than stacking a second one");
        CounterstrikeBarrierEffect after = counterer.getActiveEffect(CounterstrikeBarrierEffect.class)
            .orElseThrow(() -> new AssertionError("barrier should still be present"));
        assertEquals(20, after.getRemainingBarrierHp(), "10 from each of two procs");
        assertEquals(20, after.getMaxBarrierHp(), "max grows with each proc too");
        assertEquals(3, after.getRemainingTurns(), "duration refreshed back to full, not left at 1");
    }
}
