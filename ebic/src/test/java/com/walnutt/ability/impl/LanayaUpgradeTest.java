package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.walnutt.ability.target.TileTarget;
import com.walnutt.effect.impl.PsychicProjectionEffect;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.Team;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Lanaya's unlocked abilities. */
class LanayaUpgradeTest {

    private record Setup(UpgradeFixture fixture, Unit lanaya, Unit neighbour, Unit enemy) {
        void beginTurn() {
            lanaya.getAbilities().forEach(a -> a.onTurnStart(fixture.state(), new TurnStartEvent(Team.PLAYER_ONE)));
        }

        void hitLanayaFor(int amount) {
            lanaya.takeDamage(fixture.state(), new DamageEvent(enemy, lanaya, amount));
        }
    }

    private static Setup setup(boolean upgraded) {
        UpgradeFixture f = UpgradeFixture.create();
        Unit lanaya = f.heroWith("Lanaya", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 1000),
            0, 0, upgraded, "refraction");
        // The only adjacent unit, so "a random adjacent unit" is deterministic.
        Unit neighbour = f.basic("Neighbour", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 1000), 0, 1);
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 3);
        Setup s = new Setup(f, lanaya, neighbour, enemy);
        s.beginTurn();
        return s;
    }

    @Test
    void theBaseRefractsOnceATurnAndTakesTheRestItself() {
        Setup s = setup(false);

        s.hitLanayaFor(100);
        assertEquals(1000, s.lanaya.getHealth(), "the whole blow went elsewhere");
        assertEquals(900, s.neighbour.getHealth());

        s.hitLanayaFor(100);
        assertEquals(900, s.lanaya.getHealth(), "the allowance is spent");
        assertEquals(900, s.neighbour.getHealth());
    }

    @Test
    void upgradedTheSecondRefractionOfATurnIsOnlyHalfEfficient() {
        Setup s = setup(true);

        s.hitLanayaFor(100);
        assertEquals(1000, s.lanaya.getHealth());
        assertEquals(900, s.neighbour.getHealth(), "the first passes the whole blow on");

        s.hitLanayaFor(100);
        assertEquals(950, s.lanaya.getHealth(), "she wears the half that did not pass");
        assertEquals(850, s.neighbour.getHealth(), "and only half is passed on");

        s.hitLanayaFor(100);
        assertEquals(850, s.lanaya.getHealth(), "two a turn, and no more");
        assertEquals(850, s.neighbour.getHealth());
    }

    @Test
    void theAllowanceComesBackEachTurn() {
        Setup s = setup(true);
        s.hitLanayaFor(100);
        s.hitLanayaFor(100);

        s.beginTurn();
        s.hitLanayaFor(100);

        assertEquals(950, s.lanaya.getHealth(), "unchanged - the fresh first refraction took it all");
        assertEquals(750, s.neighbour.getHealth());
    }

    /** Upgrading mid-turn tops the allowance up rather than leaving her on last turn's count. */
    @Test
    void unlockingItPartWayThroughATurnGrantsTheSecondUseImmediately() {
        Setup s = setup(false);
        s.hitLanayaFor(100);
        assertEquals(900, s.neighbour.getHealth());

        s.lanaya.getAbilities().stream()
            .filter(a -> "refraction".equals(a.getDefinitionId())).forEach(Ability::upgrade);
        s.hitLanayaFor(100);

        assertEquals(1000, s.lanaya.getHealth(), "a fresh first refraction, at full efficiency");
        assertEquals(800, s.neighbour.getHealth());
    }

    private static Ability projectionOn(Unit unit) {
        return unit.getAbilities().stream()
            .filter(a -> "psychic_projection".equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    private static long clonesOf(UpgradeFixture f) {
        return f.state().getPlayer(Team.PLAYER_ONE).getUnits().stream()
            .filter(u -> u.getName().endsWith("(Clone)")).count();
    }

    /** The base rebalance: projecting costs no action, so she can still act on the same turn. */
    @Test
    void projectingCostsNoActionEvenBeforeItIsUnlocked() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit lanaya = f.heroWith("Lanaya", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 1000),
            0, 0, false, "psychic_projection");

        assertEquals(0, projectionOn(lanaya).getMoveCost(f.state()));
    }

    @Test
    void upgradedTheCopyStaysIndefinitelyAndSheIsNotStunnedHoldingIt() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit lanaya = f.heroWith("Lanaya", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 1000),
            0, 0, true, "psychic_projection");

        projectionOn(lanaya).onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));

        assertFalse(lanaya.hasStatus(StatusFlag.STUNNED), "she acts as normal the whole time");
        assertTrue(lanaya.getActiveEffect(PsychicProjectionEffect.class).orElseThrow()
            .getRemainingTurns() > 100);
        assertEquals(1, clonesOf(f));
    }

    @Test
    void upgradedRecastingRelocatesTheCopyRatherThanRaisingASecond() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit lanaya = f.heroWith("Lanaya", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 1000),
            0, 0, true, "psychic_projection");

        projectionOn(lanaya).onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));
        projectionOn(lanaya).decreaseCooldown(99);
        projectionOn(lanaya).onUse(f.state(), new TileTarget(f.map().getTile(new Position(2, 0))));

        assertEquals(1, clonesOf(f), "exactly one copy is ever on the board");
    }

    @Test
    void theBaseProjectionStillStunsHerForItsDuration() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit lanaya = f.heroWith("Lanaya", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 1000),
            0, 0, false, "psychic_projection");

        projectionOn(lanaya).onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));

        assertTrue(lanaya.hasStatus(StatusFlag.STUNNED));
        assertEquals(3, lanaya.getActiveEffect(PsychicProjectionEffect.class).orElseThrow()
            .getRemainingTurns());
    }
}
