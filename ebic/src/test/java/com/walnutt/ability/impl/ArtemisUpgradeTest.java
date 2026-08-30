package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.walnutt.ability.target.NoTarget;
import com.walnutt.effect.impl.SteadyFocusEffect;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.Team;
import com.walnutt.status.Stat;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Artemis's unlocked abilities. */
class ArtemisUpgradeTest {

    @Test
    void longshotUpgradedGrantsPermanentAttackRange() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit artemis = f.heroWith("Artemis", Team.PLAYER_ONE, new UnitStats(20, 60, 20, 500),
            0, 0, false, "longshot");
        assertEquals(1, (int) artemis.getEffective(Stat.ATTACK_RANGE), "the base gives no reach");

        artemis.getAbilities().stream().filter(a -> "longshot".equals(a.getDefinitionId()))
            .forEach(Ability::upgrade);

        assertEquals(3, (int) artemis.getEffective(Stat.ATTACK_RANGE), "1 + the 2 the upgrade grants");
    }

    /** The damage bonus is untouched by the upgrade - it simply has further to climb. */
    @Test
    void longshotStillScalesWithDistanceAfterUpgrading() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit artemis = f.heroWith("Artemis", Team.PLAYER_ONE, new UnitStats(20, 60, 20, 500),
            0, 0, true, "longshot");
        Unit target = f.basic("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 3);

        DamageEvent event = new DamageEvent(artemis, target, 100);
        artemis.getAbilities().stream().filter(a -> "longshot".equals(a.getDefinitionId()))
            .forEach(a -> a.onIncomingDamage(f.state(), event));

        // 3 tiles x 20% = +60%, the same rate as before.
        assertEquals(160, event.getDamage());
        assertTrue(artemis.getAbilities().stream()
            .filter(a -> "longshot".equals(a.getDefinitionId())).allMatch(Ability::isUpgraded));
    }

    private static SteadyFocus steadyFocusOn(Unit unit) {
        return (SteadyFocus) unit.getAbilities().stream()
            .filter(a -> "steady_focus".equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    @Test
    void upgradedSteadyFocusIsAToggleThatHoldsIndefinitely() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit artemis = f.heroWith("Artemis", Team.PLAYER_ONE, new UnitStats(20, 60, 20, 500),
            0, 0, true, "steady_focus");
        SteadyFocus focus = steadyFocusOn(artemis);

        focus.onUse(f.state(), new NoTarget());
        assertTrue(focus.isAiming());
        SteadyFocusEffect aim = artemis.getActiveEffect(SteadyFocusEffect.class).orElseThrow();
        assertTrue(aim.getRemainingTurns() > 100, "no clock on it - it ends when she says so");
        assertEquals(2, focus.getMaxCooldown(), "5 before the upgrade");

        focus.decreaseCooldown(99);
        focus.onUse(f.state(), new NoTarget());

        assertFalse(focus.isAiming(), "casting it again lowers the aim");
        assertTrue(artemis.getActiveEffect(SteadyFocusEffect.class).isEmpty());
    }

    @Test
    void theBaseSteadyFocusRunsOutOnItsOwn() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit artemis = f.heroWith("Artemis", Team.PLAYER_ONE, new UnitStats(20, 60, 20, 500),
            0, 0, false, "steady_focus");
        SteadyFocus focus = steadyFocusOn(artemis);

        focus.onUse(f.state(), new NoTarget());

        assertEquals(2, artemis.getActiveEffect(SteadyFocusEffect.class).orElseThrow().getRemainingTurns());
        assertEquals(5, focus.getMaxCooldown());
    }
}
