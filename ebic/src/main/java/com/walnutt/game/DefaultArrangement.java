package com.walnutt.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

/**
 * Computes a sensible default placement so a player isn't starting from a blank
 * board: champion in the corner of the map furthest into their own territory
 * (same anchors PlacementFlow already used), the 3 tiles adjacent to that corner
 * for the 3 elites (a corner of a hex-shaped map has exactly 3 in-bounds
 * neighbors - confirmed empirically, not just by the general hex-grid rule, before
 * relying on it here), then the 10 basics filling outward in increasing rings
 * around that cluster. Order within the elites/basics groups is arbitrary (roster
 * order) - the player can rearrange everything via ConcurrentSetupHandler.arrangePlacement
 * before confirming, this is just a reasonable starting point, not a final answer.
 */
public final class DefaultArrangement {
    private DefaultArrangement() {
    }

    public static Map<Unit, Position> compute(GameState state, Player player) {
        GameMap map = state.getMap();
        int radius = map.getRadius();
        Position anchor = player.getTeam() == Team.PLAYER_ONE ? new Position(-radius, 0) : new Position(radius, 0);

        Map<Unit, Position> arrangement = new LinkedHashMap<>();
        Set<Position> used = new java.util.HashSet<>();

        Unit champion = player.getChampion()
            .orElseThrow(() -> new IllegalStateException("Player has no champion to place"));
        arrangement.put(champion, anchor);
        used.add(anchor);

        List<Unit> elites = player.getUnits().stream().filter(u -> u.getUnitType() == UnitType.ELITE).toList();
        List<Tile> eliteTiles = map.getAdjacentTiles(anchor);
        if (eliteTiles.size() < elites.size()) {
            throw new IllegalStateException(
                "Expected at least " + elites.size() + " tiles adjacent to the corner anchor " + anchor
                    + " for elites, found " + eliteTiles.size());
        }
        for (int i = 0; i < elites.size(); i++) {
            Position pos = eliteTiles.get(i).getPosition();
            arrangement.put(elites.get(i), pos);
            used.add(pos);
        }

        List<Unit> basics = player.getUnits().stream().filter(u -> u.getUnitType() == UnitType.BASIC).toList();
        List<Position> basicSpots = nextFreePositions(map, anchor, basics.size(), used);
        for (int i = 0; i < basics.size(); i++) {
            arrangement.put(basics.get(i), basicSpots.get(i));
        }

        return arrangement;
    }

    /** Walks outward ring by ring from the anchor, collecting the first N walkable, not-yet-used tiles. */
    private static List<Position> nextFreePositions(GameMap map, Position anchor, int count, Set<Position> used) {
        List<Position> result = new ArrayList<>();
        int maxRadius = map.getRadius() * 2 + 1;
        for (int r = 1; r <= maxRadius && result.size() < count; r++) {
            for (Tile tile : map.getTilesInRadius(anchor, r)) {
                Position pos = tile.getPosition();
                if (used.contains(pos) || result.contains(pos) || !tile.isWalkable()) {
                    continue;
                }
                result.add(pos);
                if (result.size() == count) {
                    break;
                }
            }
        }
        if (result.size() < count) {
            throw new IllegalStateException("Not enough room on the map to place all units in a default arrangement");
        }
        return result;
    }
}
