package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.walnutt.ability.target.UnitTarget;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.game.Team;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Wei's unlocked abilities. */
class WeiUpgradeTest {

    private record Setup(UpgradeFixture fixture, Unit wei, Unit victim) {
    }

    /** The victim carries a real ability parked on cooldown, which is what Energy Break feeds on. */
    private static Setup setup(boolean upgraded, int startingCooldown) {
        UpgradeFixture f = UpgradeFixture.create();
        Unit wei = f.heroWith("Wei", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 2000),
            0, 0, upgraded, "energy_break");
        Unit victim = f.elite("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);
        Ability parked = UpgradeFixture.ability("plasma_cannon");
        victim.addAbility(parked);
        parked.increaseCooldown(startingCooldown);
        return new Setup(f, wei, victim);
    }

    @Test
    void upgradedEnergyBreakFeedsOnHowExhaustedTheTargetAlreadyIs() {
        Setup base = setup(false, 5);
        Setup upgraded = setup(true, 5);

        // STRENGTH beats INTELLIGENCE, so the swing lands for 40 in both cases.
        CombatEngine.performAttack(base.fixture.state(), base.wei, base.victim,
            Attribute.STRENGTH, Attribute.INTELLIGENCE);
        CombatEngine.performAttack(upgraded.fixture.state(), upgraded.wei, upgraded.victim,
            Attribute.STRENGTH, Attribute.INTELLIGENCE);

        int baseDealt = 2000 - base.victim.getHealth();
        int upgradedDealt = 2000 - upgraded.victim.getHealth();
        // 5 turns of cooldown at 2 damage each, priced BEFORE this attack's own drain lands.
        assertEquals(baseDealt + 10, upgradedDealt);
    }

    @Test
    void upgradedEnergyBreakHealsWeiForEveryTurnOfCooldownItFindsOnALandedHit() {
        Setup s = setup(true, 5);
        s.wei.getHealthPool().setCurrent(1000);

        CombatEngine.performAttack(s.fixture.state(), s.wei, s.victim,
            Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(1005, s.wei.getHealth(), "1 per turn of cooldown");
    }

    @Test
    void anAttackThatFailsFeedsOnNothing() {
        Setup s = setup(true, 5);
        s.wei.getHealthPool().setCurrent(1000);

        // INTELLIGENCE loses to STRENGTH: Wei's swing is defended, so only the plain drain applies.
        CombatEngine.performAttack(s.fixture.state(), s.wei, s.victim,
            Attribute.INTELLIGENCE, Attribute.STRENGTH);

        assertEquals(1000, s.wei.getHealth(), "no heal without a landed hit");
        assertTrue(s.victim.getHealth() >= 2000 - 1, "and no bonus damage either");
    }

    @Test
    void upgradedImplosionSwingsTwiceBeforePricingTheDamage() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit wei = f.heroWith("Wei", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 2000),
            0, 0, true, "implosion", "energy_break");
        Unit victim = f.elite("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 5000), 0, 2);
        Ability parked = UpgradeFixture.ability("plasma_cannon");
        victim.addAbility(parked);

        wei.getAbilities().stream().filter(a -> "implosion".equals(a.getDefinitionId()))
            .findFirst().orElseThrow().onUse(f.state(), new UnitTarget(victim));

        // The free attacks drive Energy Break's drain into the target first, so the implosion
        // that follows is priced on cooldowns the swings themselves created.
        assertTrue(parked.getCurrentCooldown() > 0, "the free attacks drained it");
        assertTrue(5000 - victim.getHealth() > 0);
    }

    @Test
    void theBaseImplosionFindsNothingOnAnUntouchedTarget() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit wei = f.heroWith("Wei", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 2000),
            0, 0, false, "implosion", "energy_break");
        Unit victim = f.elite("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 5000), 0, 2);
        victim.addAbility(UpgradeFixture.ability("plasma_cannon"));

        wei.getAbilities().stream().filter(a -> "implosion".equals(a.getDefinitionId()))
            .findFirst().orElseThrow().onUse(f.state(), new UnitTarget(victim));

        assertEquals(5000, victim.getHealth(), "no cooldowns to implode, and no swings to make any");
    }
}
