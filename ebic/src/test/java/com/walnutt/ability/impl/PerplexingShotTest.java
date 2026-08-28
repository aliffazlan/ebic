package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
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
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class PerplexingShotTest {

    private static final int HP = 1000;

    private static PerplexingShot shot() {
        return new PerplexingShot(new AbilityDefinition("Perplexing Shot", "active", "desc", Map.of(
            "cast_range", 2.0, "cooldown", 3.0, "damage", 30.0, "bonus_damage", 20.0, "bounces", 2.0)));
    }

    private record Fixture(GameState state, GameMap map, PerplexingShot shot, Unit joker,
                           Player one, Player two) {
        Unit spawn(String name, Team team, Position position) {
            Unit unit = new BasicUnit(name, team, new UnitStats(20, 20, 20, HP));
            (team == Team.PLAYER_ONE ? one : two).addUnit(unit);
            map.moveUnit(unit, map.getTile(position));
            return unit;
        }

        int damageTaken(Unit unit) {
            return HP - unit.getHealth();
        }
    }

    private static Fixture fixture(long seed) {
        Unit joker = new ChampionUnit("Joker", Team.PLAYER_ONE, new UnitStats(54, 68, 85, 990, 2));
        PerplexingShot shot = shot();
        joker.addAbility(shot);

        Player one = new Player("P1", Team.PLAYER_ONE);
        Player two = new Player("P2", Team.PLAYER_TWO);
        one.addUnit(joker);
        GameMap map = new GameMap(6);
        GameState state = new GameState(map, List.of(one, two), new Random(seed));
        state.setRemainingMoves(3);
        map.moveUnit(joker, map.getTile(new Position(0, 0)));
        return new Fixture(state, map, shot, joker, one, two);
    }

    /**
     * The brief's worked example: with two bounces the chain is 30, then 50, then 70. The
     * three enemies are arranged in a line far from Joker so each hop has exactly one
     * unhit neighbour and the random pick has nothing to choose between - the escalation
     * is what's under test, not the shuffle.
     */
    @Test
    void damageEscalatesByTheBonusOnEveryBounce() {
        Fixture f = fixture(1);
        Unit first = f.spawn("First", Team.PLAYER_TWO, new Position(0, 2));
        Unit second = f.spawn("Second", Team.PLAYER_TWO, new Position(0, 3));
        Unit third = f.spawn("Third", Team.PLAYER_TWO, new Position(0, 4));

        f.shot.onUse(f.state, new UnitTarget(first));

        assertEquals(30, f.damageTaken(first));
        assertEquals(50, f.damageTaken(second));
        assertEquals(70, f.damageTaken(third));
    }

    /** The chain is capped by `bounces`, not by how many units are standing in a row. */
    @Test
    void stopsAfterTheConfiguredNumberOfBounces() {
        Fixture f = fixture(1);
        Unit first = f.spawn("First", Team.PLAYER_TWO, new Position(0, 2));
        f.spawn("Second", Team.PLAYER_TWO, new Position(0, 3));
        f.spawn("Third", Team.PLAYER_TWO, new Position(0, 4));
        Unit fourth = f.spawn("Fourth", Team.PLAYER_TWO, new Position(0, 5));

        f.shot.onUse(f.state, new UnitTarget(first));

        assertEquals(0, f.damageTaken(fourth), "a fourth unit is one bounce past the limit");
    }

    /**
     * The rule that makes a lone target safe from the chain: with nothing beside it the
     * bolt lands once and fizzles, rather than hitting the same unit again for more.
     */
    @Test
    void fizzlesWhenThereIsNothingBesideTheLastVictim() {
        Fixture f = fixture(1);
        Unit alone = f.spawn("Alone", Team.PLAYER_TWO, new Position(0, 2));

        f.shot.onUse(f.state, new UnitTarget(alone));

        assertEquals(30, f.damageTaken(alone), "hit once, not repeatedly for the escalating amounts");
    }

    /** No unit is ever damaged twice by one cast, even when the chain doubles back past it. */
    @Test
    void neverHitsTheSameUnitTwiceInOneCast() {
        Fixture f = fixture(1);
        // A tight triangle: every unit is adjacent to both of the others, so a chain with
        // no memory would happily bounce back and forth between two of them.
        Unit a = f.spawn("A", Team.PLAYER_TWO, new Position(0, 2));
        Unit b = f.spawn("B", Team.PLAYER_TWO, new Position(1, 2));
        Unit c = f.spawn("C", Team.PLAYER_TWO, new Position(0, 3));

        f.shot.onUse(f.state, new UnitTarget(a));

        List<Integer> taken = new ArrayList<>(List.of(f.damageTaken(a), f.damageTaken(b), f.damageTaken(c)));
        taken.sort(null);
        assertEquals(List.of(30, 50, 70), taken, "each unit took exactly one distinct hit of the chain");
    }

    /** The whole risk of the ability: the bolt does not care whose side anyone is on. */
    @Test
    void bouncesOntoAlliesAndOntoJokerHimself() {
        Fixture f = fixture(1);
        // Joker at (0,0), the target and a friendly Basic beside him and beside each
        // other - a mutually adjacent triangle, so after the target is hit the only two
        // places left to bounce are both on Joker's own side.
        Unit victim = f.spawn("Victim", Team.PLAYER_TWO, new Position(0, 1));
        Unit ally = f.spawn("Ally", Team.PLAYER_ONE, new Position(1, 0));

        f.shot.onUse(f.state, new UnitTarget(victim));

        assertEquals(30, f.damageTaken(victim));
        assertTrue(f.damageTaken(ally) > 0 || f.joker.getHealth() < 990,
            "the chain reached at least one friendly unit");
        assertEquals(50 + 70, f.damageTaken(ally) + (990 - f.joker.getHealth()),
            "and both remaining hits landed on Joker's own side");
    }

    /**
     * Tiles stack whenever an ability forces them to, so "beside" has to mean distance
     * <= 1, not the six neighbouring tiles - the same rule Killer Drone uses.
     */
    @Test
    void aUnitSharingTheVictimsTileIsAValidBounceTarget() {
        Fixture f = fixture(1);
        Unit victim = f.spawn("Victim", Team.PLAYER_TWO, new Position(0, 2));
        Unit stacked = f.spawn("Stacked", Team.PLAYER_TWO, new Position(0, 2));

        f.shot.onUse(f.state, new UnitTarget(victim));

        assertEquals(30, f.damageTaken(victim));
        assertEquals(50, f.damageTaken(stacked));
    }

    /** An early fizzle is still a cast: it costs the move point and the cooldown. */
    @Test
    void chargesTheCooldownEvenWhenTheChainFindsNothingToBounceTo() {
        Fixture f = fixture(1);
        Unit alone = f.spawn("Alone", Team.PLAYER_TWO, new Position(0, 2));

        f.shot.onUse(f.state, new UnitTarget(alone));

        assertEquals(3, f.shot.getCurrentCooldown());
        assertEquals(2, f.state.getRemainingMoves());
    }
}
