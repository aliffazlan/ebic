package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class HomingMissileTest {

    private record Fixture(GameState state, GameMap map, HomingMissile missile, Unit maxwell,
                           Unit victim, Unit bystander, Unit ally) {
    }

    private static Fixture fixture() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        HomingMissile missile = new HomingMissile(new AbilityDefinition("Homing Missile", "active", "desc", Map.of(
            "cooldown", 6.0, "cast_range", 8.0, "delay", 1.0, "damage", 70.0, "aoe_damage", 20.0)));
        maxwell.addAbility(missile);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 500));
        Unit bystander = new BasicUnit("Bystander", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 500));
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(maxwell);
        p1.addUnit(ally);
        p2.addUnit(victim);
        p2.addUnit(bystander);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(0, 3)));
        map.moveUnit(bystander, map.getTile(new Position(0, 4)));
        map.moveUnit(ally, map.getTile(new Position(1, 3)));
        return new Fixture(state, map, missile, maxwell, victim, bystander, ally);
    }

    /** One of the victim's own turns, which ticks the lock and then detonates it. */
    private static void passVictimTurn(Fixture f) {
        f.victim().startTurn(f.state());
        f.victim().endTurn(f.state());
    }

    @Test
    void doesNothingOnCastAndDetonatesAfterTheDelay() {
        Fixture f = fixture();

        f.missile().onUse(f.state(), new UnitTarget(f.victim()));
        assertEquals(500, f.victim().getHealth(), "the missile is still in flight");
        assertTrue(f.victim().getEffects().stream().anyMatch(e -> e.getName().equals("Missile Lock")));

        passVictimTurn(f);
        assertEquals(430, f.victim().getHealth(), "70 on impact");
    }

    @Test
    void splashesEnemiesBesideTheImpactButNeverTheCastersOwnSide() {
        Fixture f = fixture();

        f.missile().onUse(f.state(), new UnitTarget(f.victim()));
        passVictimTurn(f);

        assertEquals(480, f.bystander().getHealth(), "an adjacent enemy takes the 20 splash");
        assertEquals(500, f.ally().getHealth(), "an adjacent ally of the caster takes nothing");
    }

    /** The point of a homing missile: the effect rides the target, so running does not help. */
    @Test
    void followsATargetThatMovedAndSplashesWhereItActuallyLands() {
        Fixture f = fixture();
        f.missile().onUse(f.state(), new UnitTarget(f.victim()));

        // Victim runs; the bystander it was standing next to is now out of the blast.
        f.map().moveUnit(f.victim(), f.map().getTile(new Position(3, 0)));
        Unit newNeighbour = new BasicUnit("Neighbour", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 500));
        f.state().getPlayer(Team.PLAYER_TWO).addUnit(newNeighbour);
        f.map().moveUnit(newNeighbour, f.map().getTile(new Position(4, 0)));

        passVictimTurn(f);

        assertEquals(430, f.victim().getHealth(), "still hit after moving");
        assertEquals(480, newNeighbour.getHealth(), "splash lands where the victim ended up");
        assertEquals(500, f.bystander().getHealth(), "and not where it started");
    }

    @Test
    void cleansingTheLockShootsTheMissileDown() {
        Fixture f = fixture();
        f.missile().onUse(f.state(), new UnitTarget(f.victim()));

        f.victim().dispelDebuffs(f.state());

        assertEquals(500, f.victim().getHealth(), "a dispelled lock must not detonate");
        assertEquals(500, f.bystander().getHealth());
        assertFalse(f.victim().getEffects().stream().anyMatch(e -> e.getName().equals("Missile Lock")));
    }

    @Test
    void recastingRefreshesTheExistingLockRatherThanStackingASecondMissile() {
        Fixture f = fixture();
        f.missile().onUse(f.state(), new UnitTarget(f.victim()));
        f.state().setRemainingMoves(3);
        f.missile().decreaseCooldown(f.missile().getCurrentCooldown());
        f.missile().onUse(f.state(), new UnitTarget(f.victim()));

        assertEquals(1, f.victim().getEffects().stream()
            .filter(e -> e.getName().equals("Missile Lock")).count());

        passVictimTurn(f);
        assertEquals(430, f.victim().getHealth(), "one impact, not two");
    }

    @Test
    void refusesAllies() {
        Fixture f = fixture();
        assertFalse(f.missile().canUse(f.state(), new UnitTarget(f.ally())));
        assertTrue(f.missile().canUse(f.state(), new UnitTarget(f.victim())));
    }
}
