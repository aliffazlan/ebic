package com.walnutt.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;
import com.walnutt.web.dto.GameStateSnapshot;
import com.walnutt.web.dto.UnitSnapshot;

/** Pure mapper test - hand-build a small GameState, no JSON/engine loop involved. */
class GameStateSnapshotMapperTest {

    @Test
    void mapsUnitPositionsStatsAndAbilities() {
        GameMap map = new GameMap(5);
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);

        Unit champ = new ChampionUnit("Valor", Team.PLAYER_ONE, new UnitStats(60, 50, 60, 1100));
        champ.addAbility(new Move());
        champ.addAbility(new Attack());
        p1.addUnit(champ);

        Unit basic = new BasicUnit("P2 Basic 1", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300));
        basic.addAbility(new Move());
        basic.addAbility(new Attack());
        p2.addUnit(basic);

        map.moveUnit(champ, map.getTile(new Position(0, 0)));
        map.moveUnit(basic, map.getTile(new Position(1, 0)));

        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);

        GameStateSnapshotMapper mapper = new GameStateSnapshotMapper(new UnitIdRegistry());
        GameStateSnapshot snapshot = mapper.toSnapshot(state);

        assertEquals("PLAYER_ONE", snapshot.currentTeam());
        assertEquals(3, snapshot.remainingMoves());
        assertFalse(snapshot.gameOver());
        assertEquals(5, snapshot.mapRadius());
        assertEquals(2, snapshot.units().size());

        UnitSnapshot champSnap = snapshot.units().stream().filter(u -> u.name().equals("Valor")).findFirst().orElseThrow();
        assertEquals("valor", champSnap.definitionId());
        assertEquals("CHAMPION", champSnap.unitType());
        assertEquals("PLAYER_ONE", champSnap.team());
        assertEquals(0, champSnap.q());
        assertEquals(0, champSnap.r());
        assertEquals(1100, champSnap.currentHp());
        assertEquals(1100, champSnap.maxHp());
        assertFalse(champSnap.dead());
        assertNotNull(champSnap.id());
        // Move + Attack are real Abilities on the unit and must be addressable by id
        // the same way any other ability is (the client needs an abilityId to submit
        // a move/attack action) - see WebInputHandler.
        assertTrue(champSnap.abilities().stream().anyMatch(a -> a.id().equals("move")));
        assertTrue(champSnap.abilities().stream().anyMatch(a -> a.id().equals("attack")));

        UnitSnapshot basicSnap = snapshot.units().stream().filter(u -> u.unitType().equals("BASIC")).findFirst().orElseThrow();
        assertEquals("basic", basicSnap.definitionId());
    }

    @Test
    void assignsStableIdsAcrossCallsForTheSameUnitInstance() {
        Unit unit = new ChampionUnit("Chronos", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        UnitIdRegistry registry = new UnitIdRegistry();
        GameStateSnapshotMapper mapper = new GameStateSnapshotMapper(registry);

        String first = mapper.toUnitSnapshot(unit).id();
        String second = mapper.toUnitSnapshot(unit).id();

        assertEquals(first, second);
        assertEquals(unit, registry.resolve(first));
    }

    @Test
    void deadUnitIsFlaggedButKeepsItsLastKnownPosition() {
        GameMap map = new GameMap(5);
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Unit unit = new BasicUnit("Dead Basic", Team.PLAYER_ONE, new UnitStats(1, 1, 1, 10));
        p1.addUnit(unit);
        map.moveUnit(unit, map.getTile(new Position(2, -1)));
        unit.getHealthPool().setCurrent(0);

        GameStateSnapshotMapper mapper = new GameStateSnapshotMapper(new UnitIdRegistry());
        UnitSnapshot snap = mapper.toUnitSnapshot(unit);

        assertTrue(snap.dead());
        assertEquals(2, snap.q());
        assertEquals(-1, snap.r());
    }
}
