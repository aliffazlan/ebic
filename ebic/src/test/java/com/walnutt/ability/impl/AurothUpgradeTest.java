package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.status.StatusFlag;
import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.Team;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Auroth's unlocked abilities. */
class AurothUpgradeTest {

    private static Ability frostbiteOn(Unit unit) {
        return unit.getAbilities().stream()
            .filter(a -> "frostbite".equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    /** Bites the target so the effect is on them, then hurts them again to trip the threshold check. */
    private static void bite(UpgradeFixture f, Unit auroth, Unit victim, int damage) {
        DamageEvent event = new DamageEvent(auroth, victim, damage);
        frostbiteOn(auroth).onIncomingDamage(f.state(), event);
        victim.takeDamage(f.state(), event);
    }

    @Test
    void upgradedFrostbiteLastsLonger() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit auroth = f.heroWith("Auroth", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, true, "frostbite");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 1);

        bite(f, auroth, victim, 10);

        assertEquals(5, victim.getEffects().get(0).getRemainingTurns(), "2 turns before the upgrade");
    }

    /**
     * The upgrade's second threshold applies to basics ALONE. An elite on the same health
     * fraction survives, which is the whole distinction.
     */
    @Test
    void upgradedFrostbiteShattersBasicsFarEarlierThanAnythingElse() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit auroth = f.heroWith("Auroth", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, true, "frostbite");
        Unit basic = f.basic("Basic", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100), 0, 1);
        Unit elite = f.elite("Elite", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100), 0, 2);

        // Down to 35% of maximum: past the 40% basic bar, nowhere near the normal 10% one.
        bite(f, auroth, basic, 65);
        bite(f, auroth, elite, 65);

        assertTrue(basic.isDead(), "a frostbitten basic shatters at 40%");
        assertFalse(elite.isDead(), "an elite still has to reach 10%");
    }

    @Test
    void theBaseFrostbiteLeavesBasicsOnTheNormalThreshold() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit auroth = f.heroWith("Auroth", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, false, "frostbite");
        Unit basic = f.basic("Basic", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100), 0, 1);

        bite(f, auroth, basic, 65);

        assertFalse(basic.isDead());
        assertEquals(2, basic.getEffects().get(0).getRemainingTurns());
    }

    /** An embraced ALLY keeps its feet; an embraced enemy is shut down exactly as before. */
    @Test
    void upgradedColdEmbraceLeavesAnAllyAbleToWalk() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit auroth = f.heroWith("Auroth", Team.PLAYER_ONE, new UnitStats(40, 20, 40, 1000),
            0, 0, true, "cold_embrace");
        Unit ally = f.basic("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 1000), 0, 1);
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 2);

        auroth.getAbilities().stream().filter(a -> "cold_embrace".equals(a.getDefinitionId()))
            .findFirst().orElseThrow().onUse(f.state(), new UnitTarget(ally));

        assertFalse(ally.hasStatus(StatusFlag.FROZEN), "not shut down");
        assertTrue(ally.hasStatus(StatusFlag.SILENCED));
        assertTrue(ally.hasStatus(StatusFlag.DISARMED));
        assertTrue(ally.hasStatus(StatusFlag.INVULNERABLE), "still untouchable, which is the point");

        Ability second = UpgradeFixture.ability("cold_embrace");
        second.upgrade();
        auroth.addAbility(second);
        second.onUse(f.state(), new UnitTarget(enemy));

        assertTrue(enemy.hasStatus(StatusFlag.FROZEN), "an enemy is still fully disabled");
    }

    @Test
    void theBaseEmbraceShutsAnAllyDownCompletely() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit auroth = f.heroWith("Auroth", Team.PLAYER_ONE, new UnitStats(40, 20, 40, 1000),
            0, 0, false, "cold_embrace");
        Unit ally = f.basic("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 1000), 0, 1);

        auroth.getAbilities().stream().filter(a -> "cold_embrace".equals(a.getDefinitionId()))
            .findFirst().orElseThrow().onUse(f.state(), new UnitTarget(ally));

        assertTrue(ally.hasStatus(StatusFlag.FROZEN));
        assertFalse(ally.hasStatus(StatusFlag.SILENCED));
    }

    /**
     * The same re-application trap as Blizzard: unlocking Frostbite used to leave everyone
     * already bitten on the old thresholds, so a basic on 35% health simply would not shatter.
     */
    @Test
    void unlockingFrostbiteAppliesTheNewThresholdToSomeoneAlreadyBitten() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit auroth = f.heroWith("Auroth", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, false, "frostbite");
        Unit basic = f.basic("Basic", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100), 0, 1);

        bite(f, auroth, basic, 10);
        assertFalse(basic.isDead(), "90% of maximum, nowhere near the base 10% bar");

        frostbiteOn(auroth).upgrade();
        // Down to 35%: past the upgraded 40% basic bar, still well clear of the base 10% one.
        bite(f, auroth, basic, 55);

        assertTrue(basic.isDead(), "the frostbite they are already carrying is upgraded too");
    }
}
