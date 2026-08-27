package com.walnutt.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.Stat;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class AttackRangeTest {

    /** Places attacker at (0,0) and defender `distance` tiles away along a single axis. */
    private static GameState scenario(Unit attacker, Unit defender, int distance) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(attacker);
        p2.addUnit(defender);
        GameMap map = new GameMap(8);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(attacker, map.getTile(new Position(0, 0)));
        map.moveUnit(defender, map.getTile(new Position(0, distance)));
        return state;
    }

    private static Unit meleeAttacker() {
        return new BasicUnit("Melee", Team.PLAYER_ONE, new UnitStats(30, 10, 10, 200));
    }

    private static Unit rangedAttacker(int range) {
        return new BasicUnit("Ranged", Team.PLAYER_ONE, new UnitStats(30, 10, 10, 200, range));
    }

    private static Unit defender() {
        return new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(10, 10, 30, 200));
    }

    @Test
    void defaultAttackRangeIsOneTile() {
        Unit attacker = meleeAttacker();
        Unit target = defender();
        Attack attack = new Attack();
        attacker.addAbility(attack);
        GameState state = scenario(attacker, target, 1);

        assertEquals(1, (int) attacker.getEffective(Stat.ATTACK_RANGE));
        assertTrue(attack.canUse(state, new UnitTarget(target)));
    }

    @Test
    void meleeCannotReachTwoTilesAway() {
        Unit attacker = meleeAttacker();
        Unit target = defender();
        Attack attack = new Attack();
        attacker.addAbility(attack);
        GameState state = scenario(attacker, target, 2);

        assertFalse(attack.canUse(state, new UnitTarget(target)));
    }

    @Test
    void aRangedUnitCanReachWithinItsRangeButNotBeyond() {
        Unit attacker = rangedAttacker(3);
        Unit target = defender();
        Attack attack = new Attack();
        attacker.addAbility(attack);
        GameState state = scenario(attacker, target, 3);

        assertTrue(attack.canUse(state, new UnitTarget(target)), "3 tiles is within range 3");

        Unit farAttacker = rangedAttacker(3);
        Unit farTarget = defender();
        Attack farAttack = new Attack();
        farAttacker.addAbility(farAttack);
        GameState farState = scenario(farAttacker, farTarget, 4);

        assertFalse(farAttack.canUse(farState, new UnitTarget(farTarget)), "4 tiles is beyond range 3");
    }

    /**
     * Tiles can hold more than one unit (Cloak and Dagger stacks Evayne onto an
     * occupied tile), so a range check written as "distance <= range" would newly
     * allow attacking something on your own tile. Adjacency never permitted that.
     */
    @Test
    void cannotAttackAUnitStandingOnTheSameTile() {
        Unit attacker = rangedAttacker(3);
        Unit target = defender();
        Attack attack = new Attack();
        attacker.addAbility(attack);
        GameState state = scenario(attacker, target, 0);

        assertEquals(0, state.getMap().getDistance(attacker.getPosition(), target.getPosition()));
        assertFalse(attack.canUse(state, new UnitTarget(target)),
            "distance 0 must stay illegal for every attack range");
    }

    @Test
    void rangeComesFromTheUnitJsonForTheDesignatedRangedUnits() {
        Map<String, UnitDefinition> units =
            new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllUnits();

        assertEquals(2, units.get("spitter").effectiveAttackRange());
        assertEquals(2, units.get("zenith").effectiveAttackRange());
        assertEquals(2, units.get("yuki").effectiveAttackRange());
        assertEquals(2, units.get("auroth").effectiveAttackRange());
        assertEquals(2, units.get("harbinger").effectiveAttackRange());
        assertEquals(4, units.get("artemis").effectiveAttackRange());
        assertEquals(2, units.get("branch").effectiveAttackRange());

        assertEquals(1, units.get("valor").effectiveAttackRange());
        assertEquals(1, units.get("dirge").effectiveAttackRange());
    }

    @Test
    void anAbsentAttackRangeKeyMeansMeleeRatherThanZero() {
        UnitDefinition noRange =
            new UnitDefinition("Nameless", "elite", 100, 10, 10, 10, 0, List.of());

        assertEquals(1, noRange.effectiveAttackRange());
    }
}
