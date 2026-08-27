package com.walnutt.ability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Regression coverage for the original skeleton's Move cast bug and dead GameState.map. */
class MoveAndAttackTest {

    @Test
    void moveUpdatesUnitPositionAndTileOccupancy() {
        GameMap map = new GameMap(3);
        Unit unit = new BasicUnit("Mover", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 50));
        unit.addAbility(new Move());
        map.moveUnit(unit, map.getTile(new Position(0, 0)));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        p1.addUnit(unit);
        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));
        state.setRemainingMoves(3);

        Move move = (Move) unit.getAbilities().get(0);
        TileTarget target = new TileTarget(map.getTile(new Position(1, 0)));

        assertTrue(move.canUse(state, target));
        move.onUse(state, target);

        assertEquals(new Position(1, 0), unit.getPosition());
        assertTrue(map.getTile(new Position(1, 0)).getFirstOccupant() == unit);
        assertFalse(map.getTile(new Position(0, 0)).isOccupied());
        assertTrue(unit.hasMovedThisTurn());
        assertFalse(move.canUse(state, target)); // already moved this turn
    }

    @Test
    void basicAttackCostsZeroMoves_championAttackCostsOne() {
        GameMap map = new GameMap(3);
        Unit basic = new BasicUnit("Basic", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 50));
        Unit champion = new ChampionUnit("Champ", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 50));
        basic.addAbility(new Attack());
        champion.addAbility(new Attack());

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        p1.addUnit(basic);
        p1.addUnit(champion);
        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));

        assertEquals(0, ((Attack) basic.getAbilities().get(0)).getMoveCost(state));
        assertEquals(1, ((Attack) champion.getAbilities().get(0)).getMoveCost(state));
    }

    @Test
    void attackRejectsSameTeamAndNonAdjacentTargets() {
        GameMap map = new GameMap(5);
        Unit a = new BasicUnit("A", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 50));
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 50));
        Unit farEnemy = new BasicUnit("FarEnemy", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 50));
        a.addAbility(new Attack());

        map.moveUnit(a, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(1, 0)));
        map.moveUnit(farEnemy, map.getTile(new Position(3, 0)));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(a);
        p1.addUnit(ally);
        p2.addUnit(farEnemy);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);

        Attack attack = (Attack) a.getAbilities().get(0);
        assertFalse(attack.canUse(state, new UnitTarget(ally)));
        assertFalse(attack.canUse(state, new UnitTarget(farEnemy)));
    }

    /** Basics no longer eat into the turn budget at all - moves are free like their attacks. */
    @Test
    void basicMoveCostsZeroMoves_championMoveCostsOne() {
        Unit basic = new BasicUnit("Basic", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        Unit champion = new ChampionUnit("Champ", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        Move basicMove = new Move();
        Move championMove = new Move();
        basic.addAbility(basicMove);
        champion.addAbility(championMove);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(basic);
        p1.addUnit(champion);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));

        assertEquals(0, basicMove.getMoveCost(state));
        assertEquals(1, championMove.getMoveCost(state));
    }

    @Test
    void aBasicCanStillMoveWithAnEmptyBudget_butOnlyOncePerTurn() {
        Unit basic = new BasicUnit("Basic", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        Move move = new Move();
        basic.addAbility(move);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(basic);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(basic, map.getTile(new Position(0, 0)));
        state.setRemainingMoves(0);

        TileTarget target = new TileTarget(map.getTile(new Position(1, 0)));
        assertTrue(move.canUse(state, target), "no move points left, but a basic move is free");
        move.onUse(state, target);

        assertEquals(0, state.getRemainingMoves(), "and it spent nothing");
        assertFalse(move.canUse(state, new TileTarget(map.getTile(new Position(0, 1)))),
            "still capped at one move per turn");
    }
}
