package com.walnutt.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;
import com.walnutt.unit.UnitType;

class DefaultArrangementTest {

    @Test
    void championAtCornerElitesOnItsThreeNeighborsBasicsFillOutward() {
        GameMap map = new GameMap(8);
        Player player = new Player("P1", Team.PLAYER_ONE);
        Unit champion = new ChampionUnit("Champ", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        player.addUnit(champion);
        for (int i = 0; i < 3; i++) {
            player.addUnit(new EliteUnit("Elite" + i, Team.PLAYER_ONE, new UnitStats(10, 10, 10, 80)));
        }
        for (int i = 0; i < 10; i++) {
            player.addUnit(new BasicUnit("Basic" + i, Team.PLAYER_ONE, new UnitStats(5, 5, 5, 40)));
        }

        GameState state = new GameState(map, List.of(player, new Player("P2", Team.PLAYER_TWO)), new Random(1));
        Map<Unit, Position> arrangement = DefaultArrangement.compute(state, player);

        Position anchor = new Position(-8, 0);
        assertEquals(anchor, arrangement.get(champion));

        Set<Position> eliteNeighbors = new HashSet<>(map.getAdjacentTiles(anchor).stream().map(t -> t.getPosition()).toList());
        assertEquals(3, eliteNeighbors.size());
        for (Unit unit : player.getUnits()) {
            if (unit.getUnitType() == UnitType.ELITE) {
                assertTrue(eliteNeighbors.contains(arrangement.get(unit)), "elite should be on a corner-adjacent tile");
            }
        }

        // Every unit gets a distinct position, and every position is within a reasonable
        // radius of the anchor (i.e. actually clustered near this player's corner, not scattered).
        assertEquals(14, arrangement.size());
        assertEquals(14, new HashSet<>(arrangement.values()).size());
        for (Position pos : arrangement.values()) {
            assertTrue(map.getDistance(anchor, pos) <= 5, "position " + pos + " is unexpectedly far from the anchor");
        }
    }
}
