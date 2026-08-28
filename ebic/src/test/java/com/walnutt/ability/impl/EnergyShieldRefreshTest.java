package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BarrierEffect;
import com.walnutt.effect.impl.EnergyShieldEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class EnergyShieldRefreshTest {

    private record Fixture(GameState state, EnergyShield shield, Unit maxwell, Unit ally, Unit enemy) {
    }

    private static Fixture fixture() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        EnergyShield shield = new EnergyShield(new AbilityDefinition("Energy Shield", "active", "desc", Map.of(
            "cooldown", 4.0, "cast_range", 3.0, "barrier", 80.0, "duration", 5.0)));
        maxwell.addAbility(shield);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(maxwell);
        p1.addUnit(ally);
        p2.addUnit(enemy);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(0, 1)));
        map.moveUnit(enemy, map.getTile(new Position(0, 2)));
        return new Fixture(state, shield, maxwell, ally, enemy);
    }

    private static void recast(Fixture f) {
        f.state().setRemainingMoves(3);
        f.shield().decreaseCooldown(f.shield().getCurrentCooldown());
        f.shield().onUse(f.state(), new UnitTarget(f.ally()));
    }

    @Test
    void recastingRefreshesTheSameShieldRatherThanAddingASecond() {
        Fixture f = fixture();
        f.shield().onUse(f.state(), new UnitTarget(f.ally()));

        // Chip it down, let some duration run off, then recast.
        f.ally().takeDamage(f.state(), new DamageEvent(f.enemy(), f.ally(), 30));
        f.ally().endTurn(f.state());
        EnergyShieldEffect existing = f.ally().getActiveEffect(EnergyShieldEffect.class).orElseThrow();
        assertEquals(50, existing.getRemainingBarrierHp());
        assertEquals(4, existing.getRemainingTurns());

        recast(f);

        assertEquals(1, f.ally().getEffects().stream()
            .filter(e -> e instanceof EnergyShieldEffect && !e.isExpired()).count(),
            "one Energy Shield, not two");
        EnergyShieldEffect refreshed = f.ally().getActiveEffect(EnergyShieldEffect.class).orElseThrow();
        assertEquals(80, refreshed.getRemainingBarrierHp(), "topped back up to a full pool");
        assertEquals(5, refreshed.getRemainingTurns(), "and a full duration");
    }

    @Test
    void refreshingDoesNotDoubleTheAbsorption() {
        Fixture f = fixture();
        f.shield().onUse(f.state(), new UnitTarget(f.ally()));
        recast(f);

        f.ally().takeDamage(f.state(), new DamageEvent(f.enemy(), f.ally(), 100));

        // One 80 HP shield, so 20 gets through - not 160 of absorption from two shields.
        assertEquals(480, f.ally().getHealth());
    }

    /**
     * A different barrier is a separate effect and must still stack alongside this one,
     * absorbing in turn - only the ability's own shield is deduplicated.
     */
    @Test
    void stillStacksWithADifferentBarrier() {
        Fixture f = fixture();
        f.shield().onUse(f.state(), new UnitTarget(f.ally()));
        BarrierEffect holyShield = new BarrierEffect("Holy Shield", "desc", 3, 50);
        f.ally().addEffect(holyShield);

        f.ally().takeDamage(f.state(), new DamageEvent(f.enemy(), f.ally(), 100));

        assertEquals(500, f.ally().getHealth(), "80 + 50 of barrier absorbs all 100");
        // The Energy Shield spends all 80 and breaks; the other barrier takes the last 20.
        assertTrue(f.ally().getActiveEffect(EnergyShieldEffect.class).isEmpty(),
            "the 80 HP shield is exhausted by a 100 damage hit");
        assertEquals(30, holyShield.getRemainingBarrierHp(), "the second barrier absorbed only the overflow");
    }
}
