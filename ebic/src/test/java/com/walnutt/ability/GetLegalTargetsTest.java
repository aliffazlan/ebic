package com.walnutt.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Ability.getLegalTargets is a brute-force enumeration filtered through each ability's own
 * canUse - these tests check it agrees with Move/Attack's hand-written legality logic. */
class GetLegalTargetsTest {

    @Test
    void moveLegalTargetsAreExactlyTheWalkableAdjacentTiles() {
        GameMap map = new GameMap(3);
        Unit mover = new BasicUnit("Mover", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 50));
        Unit blocker = new BasicUnit("Blocker", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 50));
        mover.addAbility(new Move());
        map.moveUnit(mover, map.getTile(new Position(0, 0)));
        map.moveUnit(blocker, map.getTile(new Position(1, 0))); // occupies one of the 6 neighbors

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(mover);
        p2.addUnit(blocker);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);

        Move move = (Move) mover.getAbilities().get(0);
        List<Target> legal = move.getLegalTargets(state);

        // 6 neighbors, minus the one occupied by blocker.
        assertEquals(5, legal.size());
        for (Target target : legal) {
            assertTrue(target instanceof TileTarget);
            Position pos = ((TileTarget) target).getTile().getPosition();
            assertTrue(map.areAdjacent(mover.getPosition(), pos));
        }
    }

    @Test
    void attackLegalTargetsAreExactlyAdjacentEnemies() {
        GameMap map = new GameMap(5);
        Unit attacker = new BasicUnit("A", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 50));
        Unit adjacentAlly = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 50));
        Unit adjacentEnemy = new BasicUnit("Enemy1", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 50));
        Unit farEnemy = new BasicUnit("Enemy2", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 50));
        attacker.addAbility(new Attack());

        map.moveUnit(attacker, map.getTile(new Position(0, 0)));
        map.moveUnit(adjacentAlly, map.getTile(new Position(1, 0)));
        map.moveUnit(adjacentEnemy, map.getTile(new Position(-1, 0)));
        map.moveUnit(farEnemy, map.getTile(new Position(3, 0)));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(attacker);
        p1.addUnit(adjacentAlly);
        p2.addUnit(adjacentEnemy);
        p2.addUnit(farEnemy);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);

        Attack attack = (Attack) attacker.getAbilities().get(0);
        List<Target> legal = attack.getLegalTargets(state);

        assertEquals(1, legal.size());
        assertTrue(legal.get(0) instanceof UnitTarget);
        assertEquals(adjacentEnemy, ((UnitTarget) legal.get(0)).getUnit());
    }

    @Test
    void notReadyAbilityHasNoLegalTargetsAtAll() {
        GameMap map = new GameMap(3);
        Unit unit = new BasicUnit("Idle", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 50));
        Attack attack = new Attack();
        unit.addAbility(attack);
        map.moveUnit(unit, map.getTile(new Position(0, 0)));
        attack.setMaxCooldown(3);
        attack.resetToMax(); // simulate "on cooldown"

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        p1.addUnit(unit);
        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));
        state.setRemainingMoves(3);

        assertFalse(attack.isReady());
        assertTrue(attack.getLegalTargets(state).isEmpty());
    }
}
