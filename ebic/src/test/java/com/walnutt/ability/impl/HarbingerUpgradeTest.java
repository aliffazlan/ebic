package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.effect.impl.OrbEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Harbinger's unlocked abilities. */
class HarbingerUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    private static int blastsFrom(boolean upgraded) {
        UpgradeFixture f = UpgradeFixture.create();
        Unit harbinger = f.heroWith("Harbinger", Team.PLAYER_ONE, new UnitStats(30, 30, 100, 2000),
            0, 0, upgraded, "sanity_eclipse");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 5000), 0, 2);

        on(harbinger, "sanity_eclipse").onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));
        OrbEffect orb = harbinger.getActiveEffect(OrbEffect.class).orElseThrow();

        // Each of Harbinger's turn starts winds the delay down; two are enough for two orbs.
        for (int i = 0; i < 4; i++) {
            orb.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        }
        // 100 intelligence over 0, at 1 damage per point.
        return (5000 - victim.getHealth()) / 100;
    }

    @Test
    void theBaseOrbFallsOnce() {
        assertEquals(1, blastsFrom(false));
    }

    @Test
    void upgradedTheOrbFallsASecondTimeOnTheSameTile() {
        assertEquals(2, blastsFrom(true));
    }

    /**
     * The frontend serializes a unit's effects list straight off unit.getEffects(), with no
     * expired/resolved filtering - so "pending" lingering in that raw list even after
     * detonation reads as a still-charging orb overlapping the actual blast (see
     * Effect.expireNow and OrbEffect.onTurnStart).
     */
    @Test
    void thePendingEffectIsRemovedImmediatelyOnDetonation_notLeftLingeringUntilEndTurn() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit harbinger = f.heroWith("Harbinger", Team.PLAYER_ONE, new UnitStats(30, 30, 100, 2000),
            0, 0, false, "sanity_eclipse");
        f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 5000), 0, 2);

        on(harbinger, "sanity_eclipse").onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));
        OrbEffect orb = harbinger.getActiveEffect(OrbEffect.class).orElseThrow();
        assertTrue(harbinger.getEffects().contains(orb), "charging - still present");

        orb.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertFalse(harbinger.getEffects().contains(orb),
            "removed the instant it detonates, not left present-but-expired until endTurn's sweep");
    }

    @Test
    void upgradedObjurgationBurnsEveryPointOfIntelligenceToSurviveAKillingBlow() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit harbinger = f.heroWith("Harbinger", Team.PLAYER_ONE, new UnitStats(30, 30, 100, 2000),
            0, 0, true, "objurgation");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 1);
        harbinger.getHealthPool().setCurrent(50);

        harbinger.takeDamage(f.state(), new DamageEvent(enemy, harbinger, 500));

        assertFalse(harbinger.isDead(), "he cheated death");
        assertEquals(100, harbinger.getHealth(), "all 100 intelligence became 100 health");
        assertEquals(0, harbinger.getAttributeValue(Attribute.INTELLIGENCE), "and there is none left");
    }

    /**
     * Base kit is a conditional barrier, not a guarantee - it mitigates a survivable hit but
     * does not save him from one that is genuinely fatal (that guarantee is the upgrade's own
     * perk, see upgradedObjurgationBurnsEveryPointOfIntelligenceToSurviveAKillingBlow).
     */
    @Test
    void theBaseObjurgationBurnsOnlyItsUsualShare() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit harbinger = f.heroWith("Harbinger", Team.PLAYER_ONE, new UnitStats(30, 30, 100, 2000),
            0, 0, false, "objurgation");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 1);
        harbinger.getHealthPool().setCurrent(50);

        // 20% of 100 intelligence, at 1 hp per point, is a 20 hp barrier - big enough to
        // fully absorb a 15-damage hit while leaving 5 hp of it standing afterwards.
        harbinger.takeDamage(f.state(), new DamageEvent(enemy, harbinger, 15));

        assertFalse(harbinger.isDead());
        assertEquals(50, harbinger.getHealth(), "the barrier ate the whole hit");
        assertEquals(80, harbinger.getAttributeValue(Attribute.INTELLIGENCE), "20% burned regardless of how much of the barrier was spent");
        assertEquals(5, harbinger.getActiveEffect(com.walnutt.effect.impl.BarrierEffect.class)
            .orElseThrow().getRemainingBarrierHp(), "5 hp of the 20 hp barrier survived the hit");
    }

    /** A hit too big for the barrier alone still gets partial mitigation, but is not survived. */
    @Test
    void theBaseObjurgationDoesNotGuaranteeSurvivingAGenuinelyFatalBlow() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit harbinger = f.heroWith("Harbinger", Team.PLAYER_ONE, new UnitStats(30, 30, 100, 2000),
            0, 0, false, "objurgation");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 1);
        harbinger.getHealthPool().setCurrent(50);

        harbinger.takeDamage(f.state(), new DamageEvent(enemy, harbinger, 500));

        assertTrue(harbinger.isDead(), "the 20 hp barrier is nowhere near enough to stop this");
    }

    @Test
    void itStillOnlySavesHimOnceEveryFewTurns() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit harbinger = f.heroWith("Harbinger", Team.PLAYER_ONE, new UnitStats(30, 30, 100, 2000),
            0, 0, true, "objurgation");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 1);
        harbinger.getHealthPool().setCurrent(50);

        harbinger.takeDamage(f.state(), new DamageEvent(enemy, harbinger, 500));
        assertFalse(harbinger.isDead());
        assertFalse(on(harbinger, "objurgation").isReady());

        harbinger.takeDamage(f.state(), new DamageEvent(enemy, harbinger, 500));

        assertTrue(harbinger.isDead(), "no second save while it is on cooldown");
    }
}
