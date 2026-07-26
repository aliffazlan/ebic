package com.walnutt.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.data.UnitDefinition;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.InputHandler;
import com.walnutt.ui.Renderer;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class PlacementFlowTest {

    @Test
    void placesEveryUnitOnADistinctLegalTileWithinItsOwnPlayersZone() {
        GameMap map = new GameMap(8);
        Player p1 = new Player("Player One", Team.PLAYER_ONE);
        Player p2 = new Player("Player Two", Team.PLAYER_TWO);
        for (int i = 1; i <= 14; i++) {
            p1.addUnit(new BasicUnit("P1 Unit " + i, Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300)));
            p2.addUnit(new BasicUnit("P2 Unit " + i, Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300)));
        }
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));

        new PlacementFlow().run(state, alwaysFirstCandidate(), silentRenderer());

        Set<Position> occupied = new HashSet<>();
        Position p1Anchor = new Position(-8, 0);
        Position p2Anchor = new Position(8, 0);
        for (Unit unit : p1.getUnits()) {
            assertNotNull(unit.getPosition());
            assertTrue(occupied.add(unit.getPosition()), "two units placed on the same tile");
            assertTrue(map.getDistance(p1Anchor, unit.getPosition()) <= 3, "outside Player One's zone");
        }
        for (Unit unit : p2.getUnits()) {
            assertNotNull(unit.getPosition());
            assertTrue(occupied.add(unit.getPosition()), "two units placed on the same tile");
            assertTrue(map.getDistance(p2Anchor, unit.getPosition()) <= 3, "outside Player Two's zone");
        }
        assertEquals(28, occupied.size());
    }

    private InputHandler alwaysFirstCandidate() {
        return new InputHandler() {
            @Override
            public ActionChoice chooseAction(GameState s, Player player) {
                return ActionChoice.endTurn();
            }

            @Override
            public Attribute chooseAttribute(GameState s, Unit unit) {
                return Attribute.STRENGTH;
            }

            @Override
            public UnitDefinition choosePick(GameState s, Player player, List<UnitDefinition> options) {
                return options.get(0);
            }

            @Override
            public Tile choosePlacementTile(GameState s, Player player, Unit unitToPlace, List<Tile> candidates) {
                return candidates.get(0);
            }
        };
    }

    private Renderer silentRenderer() {
        return new Renderer() {
            @Override public void render(GameState s) {}
            @Override public void renderMessage(String message) {}
            @Override public void renderGameOver(GameState s) {}
            @Override public void renderDraftRound(String roundLabel, Player p1, List<UnitDefinition> p1Options,
                                                     Player p2, List<UnitDefinition> p2Options) {}
        };
    }
}
