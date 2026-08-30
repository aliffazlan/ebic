package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.effect.impl.HolyShieldBarrierEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.Team;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Thaddeus's unlocked abilities. */
class ThaddeusUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    @Test
    void upgradedSelflessTakesMoreOfTheBlowButBearsLessOfIt() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit thaddeus = f.heroWith("Thaddeus", Team.PLAYER_ONE, new UnitStats(30, 20, 30, 1000),
            0, 0, true, "selfless");
        Unit ally = f.basic("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 1000), 0, 1);
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 2);

        ally.takeDamage(f.state(), new DamageEvent(enemy, ally, 100));

        assertEquals(940, ally.getHealth(), "40% of the blow is taken off them");
        assertEquals(980, thaddeus.getHealth(), "and only half of that 40 actually lands on him");
    }

    @Test
    void theBaseSelflessPassesTheWholeRedirectedShareOn() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit thaddeus = f.heroWith("Thaddeus", Team.PLAYER_ONE, new UnitStats(30, 20, 30, 1000),
            0, 0, false, "selfless");
        Unit ally = f.basic("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 1000), 0, 1);
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 2);

        ally.takeDamage(f.state(), new DamageEvent(enemy, ally, 100));

        assertEquals(925, ally.getHealth(), "25% is lifted off them");
        assertEquals(975, thaddeus.getHealth(), "and all of that 25 lands on him");
    }

    @Test
    void upgradedHolyShieldIsBiggerAndMendsItselfEachTurn() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit thaddeus = f.heroWith("Thaddeus", Team.PLAYER_ONE, new UnitStats(30, 20, 30, 1000),
            0, 0, true, "holy_shield");
        Unit ally = f.basic("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 1000), 0, 1);
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 2);

        on(thaddeus, "holy_shield").onUse(f.state(), new UnitTarget(ally));
        HolyShieldBarrierEffect barrier = ally.getActiveEffect(HolyShieldBarrierEffect.class).orElseThrow();
        assertEquals(100, barrier.getRemainingBarrierHp(), "50 before the upgrade");

        ally.takeDamage(f.state(), new DamageEvent(enemy, ally, 60));
        assertEquals(40, barrier.getRemainingBarrierHp());
        assertEquals(1000, ally.getHealth(), "the barrier ate all of it");

        barrier.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        assertEquals(60, barrier.getRemainingBarrierHp(), "mends 20 a turn");
    }

    @Test
    void aMendingBarrierNeverClimbsPastWhatItStartedWith() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit thaddeus = f.heroWith("Thaddeus", Team.PLAYER_ONE, new UnitStats(30, 20, 30, 1000),
            0, 0, true, "holy_shield");
        Unit ally = f.basic("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 1000), 0, 1);

        on(thaddeus, "holy_shield").onUse(f.state(), new UnitTarget(ally));
        HolyShieldBarrierEffect barrier = ally.getActiveEffect(HolyShieldBarrierEffect.class).orElseThrow();
        for (int i = 0; i < 5; i++) {
            barrier.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        }

        assertEquals(100, barrier.getRemainingBarrierHp());
    }

    /** Base: only a shattered barrier erupts. Upgraded: one that merely faded does too. */
    @Test
    void upgradedHolyShieldEruptsWhenItFadesRatherThanOnlyWhenItBreaks() {
        for (boolean upgraded : new boolean[] { false, true }) {
            UpgradeFixture f = UpgradeFixture.create();
            Unit thaddeus = f.heroWith("Thaddeus", Team.PLAYER_ONE, new UnitStats(30, 20, 30, 1000),
                0, 0, upgraded, "holy_shield");
            Unit ally = f.basic("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 1000), 0, 1);
            Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 1, 1);

            on(thaddeus, "holy_shield").onUse(f.state(), new UnitTarget(ally));
            HolyShieldBarrierEffect barrier = ally.getActiveEffect(HolyShieldBarrierEffect.class).orElseThrow();
            barrier.setRemainingTurns(0);
            barrier.onExpire(f.state());

            if (upgraded) {
                assertEquals(950, enemy.getHealth(), "a faded barrier still blasts them");
            } else {
                assertEquals(1000, enemy.getHealth(), "the base only erupts on a break");
            }
            assertTrue(barrier.getRemainingBarrierHp() > 0, "it faded rather than shattered");
        }
    }
}
