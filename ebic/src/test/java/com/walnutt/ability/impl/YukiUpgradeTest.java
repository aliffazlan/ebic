package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import com.walnutt.ability.target.MultiTarget;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.map.Position;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.game.Team;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.HealthPool;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;
import com.walnutt.unit.UnitType;

/** Yuki's unlocked abilities. */
class YukiUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    @Test
    void upgradedBlizzardDisarmsAsWellAsRoots() {
        for (boolean upgraded : new boolean[] { false, true }) {
            UpgradeFixture f = UpgradeFixture.create();
            Unit yuki = f.heroWith("Yuki", Team.PLAYER_ONE, new UnitStats(40, 20, 40, 1000),
                0, 0, upgraded, "blizzard");
            Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 2);

            on(yuki, "blizzard").onUse(f.state(), new UnitTarget(victim));

            assertTrue(victim.hasStatus(StatusFlag.ROOTED), "rooted either way");
            if (upgraded) {
                assertTrue(victim.hasStatus(StatusFlag.DISARMED));
            } else {
                assertFalse(victim.hasStatus(StatusFlag.DISARMED));
            }
        }
    }

    /**
     * The golem's own storms are tagged no_upgrade and follow their summoner's instead, which
     * is the only way "Snow Golem blizzards get the same buff" can be true of an ability Shawl
     * can never target.
     */
    @Test
    void aGolemsBlizzardFistFollowsItsSummonersUpgrade() {
        for (boolean upgraded : new boolean[] { false, true }) {
            UpgradeFixture f = UpgradeFixture.create();
            Unit yuki = f.heroWith("Yuki", Team.PLAYER_ONE, new UnitStats(40, 20, 40, 1000),
                0, 0, upgraded, "blizzard");
            Unit golem = new SummonedUnit("Snow Golem", Team.PLAYER_ONE, UnitType.BASIC,
                new UnitStats(60, 40, 15, 1000), new HealthPool(1000), yuki, false, true);
            golem.addAbility(UpgradeFixture.ability("blizzard_fist"));
            f.place(golem, Team.PLAYER_ONE, 1, 0);
            Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 1000), 1, 1);

            // STRENGTH beats INTELLIGENCE, so the fist lands and raises its storm.
            CombatEngine.performAttack(f.state(), golem, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);

            assertTrue(victim.hasStatus(StatusFlag.ROOTED));
            if (upgraded) {
                assertTrue(victim.hasStatus(StatusFlag.DISARMED), "it follows Yuki's own snow");
            } else {
                assertFalse(victim.hasStatus(StatusFlag.DISARMED));
            }
        }
    }

    private static List<Unit> golemsOn(UpgradeFixture f) {
        return f.state().getPlayer(Team.PLAYER_ONE).getUnits().stream()
            .filter(u -> "Snow Golem".equals(u.getName()) && !u.isDead()).toList();
    }

    private static MultiTarget twoTiles(UpgradeFixture f, Position first, Position second) {
        return new MultiTarget(new TileTarget(f.map().getTile(first)),
            new TileTarget(f.map().getTile(second)));
    }

    @Test
    void upgradedSnowGolemRaisesAPairFromTheWeakerPrototype() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit yuki = f.heroWith("Yuki", Team.PLAYER_ONE, new UnitStats(40, 20, 40, 1000),
            0, 0, true, "snow_golem");

        on(yuki, "snow_golem").onUse(f.state(), twoTiles(f, new Position(1, 0), new Position(0, 1)));

        List<Unit> golems = golemsOn(f);
        assertEquals(2, golems.size());
        // yuki_golem_upgrade.json: 800 rather than the single golem's 1000.
        assertTrue(golems.stream().allMatch(g -> g.getMaxHealth() == 800), "the weaker pair");
        assertTrue(golems.stream().allMatch(g -> g.getAbilities().stream()
            .anyMatch(a -> a instanceof com.walnutt.ability.Move)), "they move on their own");
    }

    @Test
    void resummoningClearsEveryGolemStillStanding() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit yuki = f.heroWith("Yuki", Team.PLAYER_ONE, new UnitStats(40, 20, 40, 1000),
            0, 0, true, "snow_golem");

        on(yuki, "snow_golem").onUse(f.state(), twoTiles(f, new Position(1, 0), new Position(0, 1)));
        on(yuki, "snow_golem").decreaseCooldown(99);
        f.state().setRemainingMoves(5);
        on(yuki, "snow_golem").onUse(f.state(), twoTiles(f, new Position(-1, 0), new Position(0, -1)));

        assertEquals(2, golemsOn(f).size(), "the first pair went, the second stands");
    }

    /** At a 16-turn cooldown, an upgrade the player cannot use for another fifteen is barely one. */
    @Test
    void unlockingItRefreshesTheCooldownAtOnce() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit yuki = f.heroWith("Yuki", Team.PLAYER_ONE, new UnitStats(40, 20, 40, 1000),
            0, 0, false, "snow_golem");
        Ability golem = on(yuki, "snow_golem");
        golem.onUse(f.state(), new TileTarget(f.map().getTile(new Position(1, 0))));
        assertFalse(golem.isReady(), "spent on the cast");

        golem.upgrade();

        assertTrue(golem.isReady());
    }

    @Test
    void theBaseSnowGolemRaisesOneAndRefusesAPair() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit yuki = f.heroWith("Yuki", Team.PLAYER_ONE, new UnitStats(40, 20, 40, 1000),
            0, 0, false, "snow_golem");

        assertFalse(on(yuki, "snow_golem").canUse(f.state(), twoTiles(f, new Position(1, 0), new Position(0, 1))));
        on(yuki, "snow_golem").onUse(f.state(), new TileTarget(f.map().getTile(new Position(1, 0))));

        List<Unit> golems = golemsOn(f);
        assertEquals(1, golems.size());
        assertEquals(1000, golems.get(0).getMaxHealth(), "the full-strength single golem");
    }

    /**
     * Re-applying an effect has to carry the CURRENT storm's flags, not just extend the clock -
     * see Effect.extendDuration. Unlocking Blizzard used to leave anyone already buried rooted
     * but never disarmed, however many times Yuki re-cast it on them.
     */
    @Test
    void unlockingBlizzardDisarmsSomeoneAlreadyBuriedOnTheNextCast() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit yuki = f.heroWith("Yuki", Team.PLAYER_ONE, new UnitStats(40, 20, 40, 1000),
            0, 0, false, "blizzard");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 2);

        on(yuki, "blizzard").onUse(f.state(), new UnitTarget(victim));
        assertFalse(victim.hasStatus(StatusFlag.DISARMED), "the base storm only roots");

        on(yuki, "blizzard").upgrade();
        on(yuki, "blizzard").decreaseCooldown(99);
        f.state().setRemainingMoves(5);
        on(yuki, "blizzard").onUse(f.state(), new UnitTarget(victim));

        assertTrue(victim.hasStatus(StatusFlag.DISARMED), "the storm they are standing in is upgraded too");
        assertTrue(victim.hasStatus(StatusFlag.ROOTED));
    }

    /** And the reverse must not happen: a golem's damage-less fist cannot water down Yuki's storm. */
    @Test
    void aGolemsFistNeverStripsADisarmFromYukisOwnStorm() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit yuki = f.heroWith("Yuki", Team.PLAYER_ONE, new UnitStats(40, 20, 40, 1000),
            0, 0, true, "blizzard");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 2);

        on(yuki, "blizzard").onUse(f.state(), new UnitTarget(victim));
        // An un-upgraded fist re-applying onto the same target.
        com.walnutt.effect.impl.BlizzardEffect.applyOrExtend(victim, yuki, 1, 0, false);

        assertTrue(victim.hasStatus(StatusFlag.DISARMED));
    }
}
