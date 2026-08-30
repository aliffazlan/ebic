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

    @Test
    void theBaseObjurgationBurnsOnlyItsUsualShare() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit harbinger = f.heroWith("Harbinger", Team.PLAYER_ONE, new UnitStats(30, 30, 100, 2000),
            0, 0, false, "objurgation");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 1);
        harbinger.getHealthPool().setCurrent(50);

        harbinger.takeDamage(f.state(), new DamageEvent(enemy, harbinger, 500));

        assertFalse(harbinger.isDead());
        assertEquals(20, harbinger.getHealth(), "20% of 100 intelligence");
        assertEquals(80, harbinger.getAttributeValue(Attribute.INTELLIGENCE));
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
