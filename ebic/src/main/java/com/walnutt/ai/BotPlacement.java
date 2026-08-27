package com.walnutt.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.walnutt.game.DefaultArrangement;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.map.Position;
import com.walnutt.status.Stat;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

/**
 * Where the bot puts its army: the engine's own default layout, re-sorted by role.
 *
 * Reusing game.DefaultArrangement's positions rather than choosing tiles from scratch
 * is deliberate and load-bearing. That layout is already known to be legal, to fit, and
 * to sit inside the player's own placement zone - and on a row-trimmed map (the full
 * match is an elongated hexagon, so hexDistance &lt;= radius no longer implies a tile
 * exists) deriving fresh coordinates from a formula is exactly the mistake the map
 * warns about. So this only ever permutes an already-valid set of positions, which
 * cannot produce an illegal placement however the map is later reshaped.
 *
 * The ordering itself: melee and durable units take the tiles nearest the enemy so they
 * meet the enemy first, long-range units sit behind where they can still shoot over the
 * line, and the champion never leaves the back corner - it is the win condition, and no
 * positional advantage is worth exposing it.
 */
public final class BotPlacement {
    private BotPlacement() {
    }

    public static Map<Unit, Position> arrange(GameState state, Player player) {
        Map<Unit, Position> base = DefaultArrangement.compute(state, player);
        Position ownAnchor = DefaultArrangement.anchorFor(state, player);
        Position enemyAnchor = enemyAnchor(state, player);
        if (enemyAnchor == null) {
            return base;
        }

        Unit champion = player.getChampion().orElse(null);

        List<Position> openPositions = new ArrayList<>(base.values());
        List<Unit> unitsToPlace = new ArrayList<>(base.keySet());
        Map<Unit, Position> arrangement = new LinkedHashMap<>();

        // The champion keeps the anchor - the tile furthest from the enemy.
        if (champion != null && base.containsKey(champion)) {
            Position championSpot = openPositions.contains(ownAnchor) ? ownAnchor : safest(openPositions, enemyAnchor, ownAnchor, state);
            arrangement.put(champion, championSpot);
            openPositions.remove(championSpot);
            unitsToPlace.remove(champion);
        }

        // Most-forward tiles to the units that most want to be forward. Distance from the
        // enemy anchor ties heavily on an elongated map, so own-anchor distance breaks it -
        // otherwise "forward" is decided arbitrarily among equidistant tiles.
        openPositions.sort(Comparator
            .<Position>comparingInt(p -> state.getMap().getDistance(p, enemyAnchor))
            .thenComparingInt(p -> state.getMap().getDistance(p, ownAnchor)));
        unitsToPlace.sort(Comparator.comparingDouble(BotPlacement::forwardness).reversed());

        for (int i = 0; i < unitsToPlace.size() && i < openPositions.size(); i++) {
            arrangement.put(unitsToPlace.get(i), openPositions.get(i));
        }
        return arrangement;
    }

    /**
     * How much this unit wants to be at the front. Reach dominates - a unit that can
     * strike from four tiles away gains nothing from standing in the front rank and
     * loses the protection of the line - with health as the tiebreaker among units of
     * equal reach, so the sturdiest of them soaks the first hits.
     */
    private static double forwardness(Unit unit) {
        double range = unit.getEffective(Stat.ATTACK_RANGE);
        double durability = unit.getMaxHealth() / 10_000.0;
        double elitePull = unit.getUnitType() == UnitType.ELITE ? 0.05 : 0.0;
        return -range + durability + elitePull;
    }

    /** Furthest from the enemy, ties broken toward home - the same rule the main sort uses, and for the same reason. */
    private static Position safest(List<Position> positions, Position enemyAnchor, Position ownAnchor, GameState state) {
        return positions.stream()
            .min(Comparator
                .<Position>comparingInt(p -> -state.getMap().getDistance(p, enemyAnchor))
                .thenComparingInt(p -> state.getMap().getDistance(p, ownAnchor)))
            .orElseThrow(() -> new IllegalStateException("No positions to place the champion on"));
    }

    private static Position enemyAnchor(GameState state, Player player) {
        for (Player other : state.getPlayers()) {
            if (other.getTeam() != player.getTeam()) {
                return DefaultArrangement.anchorFor(state, other);
            }
        }
        return null;
    }
}
