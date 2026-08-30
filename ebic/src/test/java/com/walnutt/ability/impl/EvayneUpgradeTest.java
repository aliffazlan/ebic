package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Evayne's unlocked abilities. */
class EvayneUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    /** Damage from one swing Evayne loses outright - INTELLIGENCE into STRENGTH. */
    private static int defendedSwingDamage(boolean upgraded) {
        UpgradeFixture f = UpgradeFixture.create();
        Unit evayne = f.heroWith("Evayne", Team.PLAYER_ONE, new UnitStats(10, 50, 10, 1000),
            0, 0, upgraded, "backstab");
        Unit target = f.basic("Target", Team.PLAYER_TWO, new UnitStats(50, 0, 0, 1000), 0, 1);

        CombatEngine.performAttack(f.state(), evayne, target, Attribute.INTELLIGENCE, Attribute.STRENGTH);
        return 1000 - target.getHealth();
    }

    /**
     * The base used to hand a defended swing the whole backstab bonus, which made failing
     * one of Evayne's better outcomes. It now carries none of it.
     */
    @Test
    void aDefendedSwingCarriesNoBackstabDamageAtAll() {
        assertEquals(0, defendedSwingDamage(false));
    }

    @Test
    void upgradedADefendedSwingStillCarriesHalfTheBonus() {
        // 50 agility x 0.8 = 40 of backstab, halved on a failure.
        assertEquals(20, defendedSwingDamage(true));
    }

    @Test
    void alandedSwingIsUnaffectedByTheUpgrade() {
        for (boolean upgraded : new boolean[] { false, true }) {
            UpgradeFixture f = UpgradeFixture.create();
            Unit evayne = f.heroWith("Evayne", Team.PLAYER_ONE, new UnitStats(10, 50, 10, 1000),
                0, 0, upgraded, "backstab");
            Unit target = f.basic("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 1000), 0, 1);

            // AGILITY beats STRENGTH... but the target brings INTELLIGENCE, which AGILITY loses
            // to. Use STRENGTH into INTELLIGENCE so Evayne wins: 10 base + 40 backstab.
            CombatEngine.performAttack(f.state(), evayne, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

            assertEquals(50, 1000 - target.getHealth(), "upgraded=" + upgraded);
        }
    }

    @Test
    void upgradedCloakAndDaggerRootsAndSilencesAnAmbushedVictim() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit evayne = f.heroWith("Evayne", Team.PLAYER_ONE, new UnitStats(60, 60, 60, 1000),
            0, 0, true, "cloak_and_dagger");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 1);

        on(evayne, "cloak_and_dagger").onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 1))));

        assertTrue(victim.getHealth() < 1000, "the ambush landed");
        assertTrue(victim.hasStatus(StatusFlag.ROOTED));
        assertTrue(victim.hasStatus(StatusFlag.SILENCED));
    }

    @Test
    void theBaseAmbushLeavesThemFreeToWalkAway() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit evayne = f.heroWith("Evayne", Team.PLAYER_ONE, new UnitStats(60, 60, 60, 1000),
            0, 0, false, "cloak_and_dagger");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 1);

        on(evayne, "cloak_and_dagger").onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 1))));

        assertTrue(victim.getHealth() < 1000);
        assertFalse(victim.hasStatus(StatusFlag.ROOTED));
        assertFalse(victim.hasStatus(StatusFlag.SILENCED));
    }
}
