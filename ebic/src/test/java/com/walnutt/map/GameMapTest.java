package com.walnutt.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.walnutt.game.Team;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.UnitStats;

class GameMapTest {

    @Test
    void hexagonHasExpectedTileCount() {
        // 3*radius^2 + 3*radius + 1, the standard hexagon-of-hexagons formula.
        for (int radius = 0; radius <= 6; radius++) {
            GameMap map = new GameMap(radius);
            int expected = 3 * radius * radius + 3 * radius + 1;
            assertEquals(expected, map.getTilesInRadius(new Position(0, 0), radius).size(),
                "radius " + radius);
        }
    }

    @Test
    void originHasExactlySixNeighbors_matchingDirections() {
        GameMap map = new GameMap(3);
        List<Tile> neighbors = map.getAdjacentTiles(new Position(0, 0));
        assertEquals(6, neighbors.size());

        Set<Position> expected = Set.of(Position.DIRECTIONS);
        Set<Position> actual = neighbors.stream().map(Tile::getPosition).collect(Collectors.toSet());
        assertEquals(expected, actual);
    }

    @Test
    void adjacencyIsSymmetricAndDistanceIsConsistent() {
        GameMap map = new GameMap(3);
        Position a = new Position(0, 0);
        Position b = new Position(1, 0);
        Position far = new Position(2, -1);

        assertTrue(map.areAdjacent(a, b));
        assertTrue(map.areAdjacent(b, a));
        assertEquals(1, map.getDistance(a, b));

        assertFalse(map.areAdjacent(a, far));
        assertEquals(2, map.getDistance(a, far));
    }

    @Test
    void outOfBoundsPositionsReturnNullTile() {
        GameMap map = new GameMap(2);
        assertFalse(map.isWithinBounds(new Position(3, 0)));
        assertTrue(map.isWithinBounds(new Position(2, 0)));
        assertEquals(null, map.getTile(new Position(3, 0)));
    }

    @Test
    void findNearestFreeTile_skipsOccupiedAndOrigin_picksClosest() {
        GameMap map = new GameMap(3);
        Position origin = new Position(0, 0);

        // Occupy every distance-1 neighbor except one, and the origin itself.
        UnitStats occupantStats = new UnitStats(1, 1, 1, 1);
        map.getTile(origin).addOccupant(new BasicUnit("Self", Team.PLAYER_ONE, occupantStats));
        List<Tile> neighbors = map.getAdjacentTiles(origin);
        Tile expectedFree = neighbors.get(neighbors.size() - 1);
        for (Tile neighbor : neighbors) {
            if (neighbor != expectedFree) {
                neighbor.addOccupant(new BasicUnit("Blocker", Team.PLAYER_TWO, occupantStats));
            }
        }

        Tile found = map.findNearestFreeTile(origin, 3).orElseThrow();
        assertEquals(expectedFree.getPosition(), found.getPosition());
        assertEquals(1, map.getDistance(origin, found.getPosition()));
    }

    @Test
    void trimmingRowsRemovesTheTopAndBottomOfTheHexagon() {
        // The shape the full match uses: radius 7 with rows cut to |r| <= 5.
        GameMap map = new GameMap(7, 5);

        assertEquals(5, map.getRowLimit());
        assertEquals(7, map.getRadius(), "the horizontal half-extent is unchanged by a row trim");
        assertEquals(135, map.getTilesInRadius(new Position(0, 0), 7).size());

        // Rows beyond the limit are gone even though they're inside the radius.
        assertFalse(map.isWithinBounds(new Position(0, 6)));
        assertFalse(map.isWithinBounds(new Position(0, -6)));
        assertEquals(null, map.getTile(new Position(0, 7)));
        assertTrue(map.isWithinBounds(new Position(0, 5)));
        assertTrue(map.isWithinBounds(new Position(0, -5)));

        // The wide axis is untouched, so it's elongated rather than uniformly smaller.
        assertTrue(map.isWithinBounds(new Position(7, 0)));
        assertTrue(map.isWithinBounds(new Position(-7, 0)));
    }

    @Test
    void theDefaultConstructorStillBuildsARegularHexagon() {
        GameMap map = new GameMap(4);

        assertEquals(4, map.getRowLimit(), "no trim means the row limit equals the radius");
        assertTrue(map.isWithinBounds(new Position(0, 4)));
        assertEquals(3 * 16 + 3 * 4 + 1, map.getTilesInRadius(new Position(0, 0), 4).size());
    }

    /**
     * DefaultArrangement throws unless a player's corner anchor has at least 3 in-bounds
     * neighbours (one per elite), so the trimmed shape has to preserve that.
     */
    @Test
    void cornerAnchorsKeepThreeNeighboursOnATrimmedMap() {
        GameMap map = new GameMap(7, 5);

        for (Position anchor : List.of(new Position(-7, 0), new Position(7, 0))) {
            assertTrue(map.isWithinBounds(anchor), "anchor " + anchor + " must exist");
            assertEquals(3, map.getAdjacentTiles(anchor).size(), "anchor " + anchor);
        }
    }

    /** A row trim larger than the radius would leave nothing sensible; it clamps instead. */
    @Test
    void rowLimitIsClampedToTheRadius() {
        GameMap map = new GameMap(3, 99);

        assertEquals(3, map.getRowLimit());
        assertEquals(3 * 9 + 3 * 3 + 1, map.getTilesInRadius(new Position(0, 0), 3).size());
    }
}
