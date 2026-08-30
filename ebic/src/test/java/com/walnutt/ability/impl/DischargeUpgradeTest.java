package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.effect.impl.StaticLinkEffect;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Discharge's unlocked abilities. */
class DischargeUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    private static long hurt(List<Unit> units) {
        return units.stream().filter(u -> u.getHealth() < 1000).count();
    }

    @Test
    void upgradedEyeOfTheStormStrikesThreeSeparateEnemiesEachTurn() {
        UpgradeFixture f = UpgradeFixture.create(5, 3);
        Unit discharge = f.heroWith("Discharge", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 1000),
            0, 0, true, "eye_of_the_storm");
        List<Unit> ring = List.of(
            f.basic("A", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 1, 0),
            f.basic("B", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 1),
            f.basic("C", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), -1, 1),
            f.basic("D", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), -1, 0));

        on(discharge, "eye_of_the_storm").onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(3, hurt(ring), "three bolts, three separate victims");
    }

    @Test
    void theBaseStormReleasesOneBolt() {
        UpgradeFixture f = UpgradeFixture.create(5, 3);
        Unit discharge = f.heroWith("Discharge", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 1000),
            0, 0, false, "eye_of_the_storm");
        List<Unit> ring = List.of(
            f.basic("A", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 1, 0),
            f.basic("B", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 1),
            f.basic("C", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), -1, 1));

        on(discharge, "eye_of_the_storm").onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(1, hurt(ring));
    }

    /** Fewer enemies than bolts is fine - a unit is never struck twice in a turn. */
    @Test
    void threeBoltsAgainstOneEnemyStillStrikeItOnlyOnce() {
        UpgradeFixture f = UpgradeFixture.create(5, 3);
        Unit discharge = f.heroWith("Discharge", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 1000),
            0, 0, true, "eye_of_the_storm");
        Unit lone = f.basic("Lone", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 1, 0);

        on(discharge, "eye_of_the_storm").onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(998, lone.getHealth(), "one bolt's worth of damage, not three");
    }

    @Test
    void upgradedTheVulnerabilityIsWorthMore() {
        for (boolean upgraded : new boolean[] { false, true }) {
            UpgradeFixture f = UpgradeFixture.create(5, 3);
            Unit discharge = f.heroWith("Discharge", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 1000),
                0, 0, upgraded, "eye_of_the_storm");
            Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 1, 0);

            on(discharge, "eye_of_the_storm").onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

            int stack = victim.getEffects().stream()
                .filter(e -> "Static Charge".equals(e.getName())).findFirst().orElseThrow()
                .getExtraInfo().chars().filter(Character::isDigit)
                .reduce(0, (acc, c) -> acc * 10 + (c - '0'));
            assertEquals(upgraded ? 4 : 2, stack, "upgraded=" + upgraded);
        }
    }

    @Test
    void upgradedStaticLinkHoldsAtTwoTilesRatherThanSnappingAtOne() {
        for (boolean upgraded : new boolean[] { false, true }) {
            UpgradeFixture f = UpgradeFixture.create(5, 3);
            Unit discharge = f.heroWith("Discharge", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 2000),
                0, 0, upgraded, "static_link");
            Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 2000), 0, 1);

            on(discharge, "static_link").onUse(f.state(), new UnitTarget(victim));
            StaticLinkEffect link = discharge.getActiveEffect(StaticLinkEffect.class).orElseThrow();

            // Step out to two tiles apart, then end the turn - the moment the link is tested.
            f.map().moveUnit(victim, f.map().getTile(new Position(0, 2)));
            link.onTurnEnd(f.state(), new TurnEndEvent(Team.PLAYER_ONE));

            if (upgraded) {
                assertTrue(discharge.getActiveEffect(StaticLinkEffect.class).isPresent(),
                    "two tiles is still within reach");
            } else {
                assertTrue(discharge.getActiveEffect(StaticLinkEffect.class).isEmpty(),
                    "the base link snaps the moment they are not adjacent");
            }
        }
    }

    @Test
    void upgradedStaticLinkAlsoReachesTwoTilesToCast() {
        UpgradeFixture f = UpgradeFixture.create(5, 3);
        Unit discharge = f.heroWith("Discharge", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 2000),
            0, 0, true, "static_link");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 2000), 0, 2);

        assertTrue(on(discharge, "static_link").canUse(f.state(), new UnitTarget(victim)));
    }
}
