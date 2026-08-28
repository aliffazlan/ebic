package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.MultiTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
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

class TranslocationTest {

    private record Fixture(GameState state, GameMap map, Translocation translocation,
                           Unit maxwell, Unit ally, Unit enemy) {
    }

    private static Fixture fixture() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        Translocation translocation = new Translocation(
            new AbilityDefinition("Translocation", "active", "desc", Map.of(
                "cooldown", 5.0, "cast_range", 3.0, "self_range", 4.0, "ally_range", 3.0, "enemy_range", 2.0)));
        maxwell.addAbility(translocation);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 300));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 300));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(maxwell);
        p1.addUnit(ally);
        p2.addUnit(enemy);
        GameMap map = new GameMap(8);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(0, 1)));
        map.moveUnit(enemy, map.getTile(new Position(0, 2)));
        return new Fixture(state, map, translocation, maxwell, ally, enemy);
    }

    private static MultiTarget pair(Fixture f, Unit subject, int q, int r) {
        return new MultiTarget(new UnitTarget(subject), new TileTarget(f.map().getTile(new Position(q, r))));
    }

    @Test
    void movesTheChosenUnitToTheChosenTile() {
        Fixture f = fixture();

        f.translocation().onUse(f.state(), pair(f, f.enemy(), 1, 2));

        assertEquals(new Position(1, 2), f.enemy().getPosition());
        assertTrue(f.map().getTile(new Position(1, 2)).getOccupants().contains(f.enemy()));
        assertFalse(f.map().getTile(new Position(0, 2)).getOccupants().contains(f.enemy()),
            "and is no longer on its old tile");
    }

    /** The displacement allowance is what differs by relationship, not the cast range. */
    @Test
    void eachRelationshipHasItsOwnDisplacementAllowance() {
        Fixture f = fixture();

        assertEquals(4, f.translocation().displacementRangeFor(f.maxwell()));
        assertEquals(3, f.translocation().displacementRangeFor(f.ally()));
        assertEquals(2, f.translocation().displacementRangeFor(f.enemy()));

        // Maxwell may throw himself 4 tiles, but may only shove an enemy 2.
        assertTrue(f.translocation().canUse(f.state(), pair(f, f.maxwell(), 0, 4)));
        assertFalse(f.translocation().canUse(f.state(), pair(f, f.enemy(), 0, 5)),
            "3 tiles is beyond an enemy's allowance of 2");
        assertTrue(f.translocation().canUse(f.state(), pair(f, f.enemy(), 0, 4)),
            "2 tiles is exactly the allowance");
    }

    @Test
    void refusesASubjectOutOfCastRange() {
        Fixture f = fixture();
        f.map().moveUnit(f.enemy(), f.map().getTile(new Position(0, 5)));

        assertFalse(f.translocation().canUse(f.state(), pair(f, f.enemy(), 0, 4)),
            "5 tiles away is outside the cast range of 3");
    }

    @Test
    void refusesAnOccupiedDestinationAndAZeroTileMove() {
        Fixture f = fixture();

        assertFalse(f.translocation().canUse(f.state(), pair(f, f.enemy(), 0, 1)),
            "the ally is standing there");
        assertFalse(f.translocation().canUse(f.state(), pair(f, f.enemy(), 0, 2)),
            "setting a unit down where it already stands is a no-op, not a cast");
    }

    @Test
    void refusesASingleShapeTarget() {
        Fixture f = fixture();

        assertFalse(f.translocation().canUse(f.state(), new UnitTarget(f.enemy())));
        assertFalse(f.translocation().canUse(f.state(),
            new TileTarget(f.map().getTile(new Position(1, 1)))));
    }

    /**
     * The default enumeration only ever builds single-shape candidates, so without the
     * override this ability would look untargetable - to the highlighting the client draws
     * and to the bot alike.
     */
    @Test
    void enumeratesMultiTargetPairsThatAreAllActuallyLegal() {
        Fixture f = fixture();

        List<Target> legal = f.translocation().getLegalTargets(f.state());

        assertFalse(legal.isEmpty(), "a multi-target ability must not report itself untargetable");
        assertTrue(legal.stream().allMatch(t -> t instanceof MultiTarget));
        assertTrue(legal.stream().allMatch(t -> f.translocation().canUse(f.state(), t)),
            "every enumerated pair must pass the ability's own canUse");

        // All three units are within cast range 3, so all three should be movable.
        assertEquals(3, legal.stream()
            .map(t -> ((UnitTarget) ((MultiTarget) t).primary()).getUnit())
            .distinct().count());
    }
}
