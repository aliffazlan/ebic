package com.walnutt.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import com.walnutt.unit.Unit;

/**
 * Hexagonal map using axial coordinates (Position), centered on (0,0): every Position
 * with hexDistance &lt;= radius from the origin is a valid tile. Adjacency is the six
 * directions in Position.DIRECTIONS, so "distance"/"radius"/"adjacent" are all
 * geometrically consistent with each other.
 *
 * A `rowLimit` below the radius trims whole rows off the top and bottom, turning the
 * regular hexagon into an elongated one - r is the vertical axis in both the terminal
 * renderer and the browser client, so "row" means "constant r". The full match uses this
 * to get a wide, shallow battlefield; every other caller gets an untrimmed hexagon.
 *
 * IMPORTANT: with a trim in play, `hexDistance &lt;= radius` no longer implies a tile
 * exists. Ask the map (getTile / isWithinBounds / getTilesInRadius) rather than deriving
 * membership from a coordinate formula.
 */
public class GameMap {
    private final int radius;
    private final int rowLimit;
    private final Map<Position, Tile> tiles = new LinkedHashMap<>();

    /** A regular hexagon - no rows trimmed. */
    public GameMap(int radius) {
        this(radius, radius);
    }

    public GameMap(int radius, int rowLimit) {
        this.radius = radius;
        this.rowLimit = Math.min(rowLimit, radius);
        for (int q = -radius; q <= radius; q++) {
            int rMin = Math.max(-radius, -q - radius);
            int rMax = Math.min(radius, -q + radius);
            for (int r = rMin; r <= rMax; r++) {
                if (Math.abs(r) > this.rowLimit) {
                    continue;
                }
                Position position = new Position(q, r);
                tiles.put(position, new Tile(position, TileType.PLAIN));
            }
        }
    }

    public int getRadius() {
        return radius;
    }

    /** Largest |r| that exists on this map; equal to the radius when nothing is trimmed. */
    public int getRowLimit() {
        return rowLimit;
    }

    public boolean isWithinBounds(Position position) {
        return tiles.containsKey(position);
    }

    public Tile getTile(Position position) {
        return tiles.get(position);
    }

    public void setTileType(Position position, TileType type) {
        Tile old = getTile(position);
        if (old == null) {
            return;
        }
        Tile replacement = new Tile(position, type);
        for (Unit occupant : old.getOccupants()) {
            replacement.addOccupant(occupant);
        }
        tiles.put(position, replacement);
    }

    public int getDistance(Position a, Position b) {
        return a.hexDistance(b);
    }

    public boolean areAdjacent(Position a, Position b) {
        return getDistance(a, b) == 1;
    }

    /** Detaches the unit from its current tile (if any) and attaches it to the destination - allows stacking. */
    public void moveUnit(Unit unit, Tile destination) {
        if (unit.getPosition() != null) {
            Tile origin = getTile(unit.getPosition());
            if (origin != null) {
                origin.removeOccupant(unit);
            }
        }
        destination.addOccupant(unit);
        unit.setPosition(destination.getPosition());
    }

    public List<Tile> getAdjacentTiles(Position position) {
        List<Tile> result = new ArrayList<>();
        for (Position direction : Position.DIRECTIONS) {
            Tile tile = getTile(position.plus(direction));
            if (tile != null) {
                result.add(tile);
            }
        }
        return result;
    }

    public List<Tile> getTilesInRadius(Position center, int searchRadius) {
        List<Tile> result = new ArrayList<>();
        for (Tile tile : tiles.values()) {
            if (getDistance(center, tile.getPosition()) <= searchRadius) {
                result.add(tile);
            }
        }
        return result;
    }

    public List<Unit> getUnitsInRadius(Position center, int searchRadius) {
        List<Unit> result = new ArrayList<>();
        for (Tile tile : getTilesInRadius(center, searchRadius)) {
            result.addAll(tile.getOccupants());
        }
        return result;
    }

    public List<Unit> getAdjacentUnits(Position position, Predicate<Unit> filter) {
        List<Unit> result = new ArrayList<>();
        for (Tile tile : getAdjacentTiles(position)) {
            for (Unit occupant : tile.getOccupants()) {
                if (filter.test(occupant)) {
                    result.add(occupant);
                }
            }
        }
        return result;
    }

    public Optional<Unit> randomAdjacentUnit(Position position, Predicate<Unit> filter, Random rng) {
        List<Unit> candidates = getAdjacentUnits(position, filter);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(rng.nextInt(candidates.size())));
    }

    public Optional<Unit> randomUnitInRadius(Position center, int radius, Predicate<Unit> filter, Random rng) {
        List<Unit> candidates = new ArrayList<>();
        for (Unit unit : getUnitsInRadius(center, radius)) {
            if (filter.test(unit)) {
                candidates.add(unit);
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(candidates.get(rng.nextInt(candidates.size())));
    }

    public boolean hasAdjacentEnemy(Unit unit) {
        return !getAdjacentUnits(unit.getPosition(), other -> other.getTeam() != unit.getTeam()).isEmpty();
    }

    /** Nearest tile (excluding 'from') that is unblocked and has no occupants at all - Cloak and Dagger's exit. */
    public Optional<Tile> findNearestFreeTile(Position from, int searchRadius) {
        return getTilesInRadius(from, searchRadius).stream()
            .filter(tile -> !tile.getPosition().equals(from))
            .filter(tile -> tile.getType() != TileType.BLOCKED && tile.getOccupants().isEmpty())
            .min(Comparator.comparingInt(tile -> getDistance(from, tile.getPosition())));
    }

    /** Empty (unblocked, unoccupied) tiles within range - candidate pool for interactive placement. */
    public List<Tile> getEmptyTilesInRadius(Position center, int searchRadius) {
        return getTilesInRadius(center, searchRadius).stream()
            .filter(tile -> tile.getType() != TileType.BLOCKED && tile.getOccupants().isEmpty())
            .toList();
    }
}
