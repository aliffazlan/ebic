package com.walnutt.game;

import java.util.List;

import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.InputHandler;
import com.walnutt.ui.Renderer;
import com.walnutt.unit.Unit;

/**
 * Interactive placement phase: each player places every one of their units
 * (champion, elites, basics - in roster order) onto a tile of their choosing
 * within a zone anchored on their side of the map. Zones are anchored at the two
 * extreme corners the old auto-placement used, far enough apart on a full-size map
 * that a modest ZONE_RADIUS can never let the two overlap.
 */
public class PlacementFlow {
    private static final int ZONE_RADIUS = 3;

    public void run(GameState state, InputHandler input, Renderer renderer) {
        GameMap map = state.getMap();
        int radius = map.getRadius();
        placePlayer(state, input, renderer, state.getPlayers().get(0), new Position(-radius, 0));
        placePlayer(state, input, renderer, state.getPlayers().get(1), new Position(radius, 0));
    }

    private void placePlayer(GameState state, InputHandler input, Renderer renderer, Player player, Position anchor) {
        for (Unit unit : player.getUnits()) {
            renderer.render(state);
            List<Tile> candidates = state.getMap().getEmptyTilesInRadius(anchor, ZONE_RADIUS);
            Tile chosen = input.choosePlacementTile(state, player, unit, candidates);
            state.getMap().moveUnit(unit, chosen);
        }
    }
}
