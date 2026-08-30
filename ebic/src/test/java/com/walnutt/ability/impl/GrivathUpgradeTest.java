package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.effect.impl.FeastEffect;
import com.walnutt.status.StatusFlag;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.game.Team;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Grivath's unlocked abilities. */
class GrivathUpgradeTest {

    private record Outcome(int agilityLost, int strengthLost, int healthLost) {
    }

    /** Grivath swings and LOSES the matchup - INTELLIGENCE into STRENGTH. */
    private static Outcome defendedSwing(boolean upgraded) {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 1000),
            0, 0, upgraded, "cripple");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(50, 50, 50, 1000), 0, 1);

        CombatEngine.performAttack(f.state(), grivath, victim, Attribute.INTELLIGENCE, Attribute.STRENGTH);

        return new Outcome(
            50 - victim.getAttributeValue(Attribute.AGILITY),
            50 - victim.getAttributeValue(Attribute.STRENGTH),
            1000 - victim.getHealth());
    }

    @Test
    void theBaseOnlyShavesTheDefendingAttributeWhenAnAttackFails() {
        Outcome base = defendedSwing(false);

        assertEquals(0, base.agilityLost, "untouched attributes stay untouched");
        assertEquals(1, base.strengthLost, "one point off whatever blocked it");
        assertEquals(0, base.healthLost, "and no health at all");
    }

    @Test
    void upgradedAFailedAttackDrainsExactlyAsMuchAsALandedOne() {
        Outcome upgraded = defendedSwing(true);

        // A basic unit, so nothing is doubled: 1 from every attribute, 3 more from the one that
        // defended, and the health on top.
        assertEquals(1, upgraded.agilityLost);
        assertEquals(4, upgraded.strengthLost, "1 as an attribute, plus the 3 defended bonus");
        // Cripple deliberately damages BEFORE dropping the ceiling, so the drop does not
        // clamp current health a second time and charge for it twice.
        assertEquals(5, upgraded.healthLost);
    }

    private static Ability feastOn(Unit unit) {
        return unit.getAbilities().stream()
            .filter(a -> "feast".equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    /** Unlocking it makes the hunger permanent, before any cast at all. */
    @Test
    void upgradedFeastMakesEveryAttackHealAndRootWithNoFrenzyNeeded() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);
        grivath.getHealthPool().setCurrent(1000);

        assertTrue(grivath.getActiveEffect(FeastEffect.class).isPresent(), "granted on unlock, not on cast");

        // An ordinary attack, nothing to do with the ability's own cast.
        CombatEngine.performAttack(f.state(), grivath, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(1013, grivath.getHealth(), "25% of the 50 dealt, rounded");
        assertTrue(victim.hasStatus(StatusFlag.ROOTED));
    }

    @Test
    void theBaseFeastOnlyHealsAndRootsInsideItsFrenzy() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, false, "feast");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);
        grivath.getHealthPool().setCurrent(1000);

        assertFalse(grivath.getActiveEffect(FeastEffect.class).isPresent());
        CombatEngine.performAttack(f.state(), grivath, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(1000, grivath.getHealth(), "an ordinary attack heals nothing");
        assertFalse(victim.hasStatus(StatusFlag.ROOTED));
    }

    @Test
    void upgradedCastingFeastIsPurelyTheFreeAttack() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);

        feastOn(grivath).onUse(f.state(), new NoTarget());

        assertTrue(victim.getHealth() < 2000, "the cast bit them");
        assertEquals(1, grivath.getEffects().stream()
            .filter(FeastEffect.class::isInstance).count(), "and raised no second frenzy");
    }
}
