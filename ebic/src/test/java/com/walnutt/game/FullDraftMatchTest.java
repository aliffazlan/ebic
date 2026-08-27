package com.walnutt.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.data.UnitDefinition;
import com.walnutt.map.Tile;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.InputHandler;
import com.walnutt.ui.Renderer;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

/**
 * Builds the real 1 champion + 3 elite + 10 basic draft roster from the JSON in
 * design_ideas/, driving the interactive DraftFlow/PlacementFlow with a scripted
 * InputHandler (always picks the first option) and a silent Renderer instead of
 * blocking on real terminal input - the same pattern TurnManagerIntegrationTest
 * uses for the main game loop.
 */
class FullDraftMatchTest {

    @Test
    void buildsFullRosterPerSide_withImplementedAbilitiesWired() {
        InputHandler alwaysFirst = new InputHandler() {
            @Override
            public ActionChoice chooseAction(GameState s, Player player) {
                return ActionChoice.endTurn();
            }

            @Override
            public Attribute chooseAttribute(GameState s, Unit unit, Unit opponent) {
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
        Renderer silent = new Renderer() {
            @Override public void render(GameState s) {}
            @Override public void renderMessage(String message) {}
            @Override public void renderGameOver(GameState s) {}
            @Override public void renderDraftRound(String roundLabel, Player p1, List<UnitDefinition> p1Options,
                                                     Player p2, List<UnitDefinition> p2Options) {}
        };

        // Fixed seed: the draft pool is now larger than what a single draft consumes
        // (5 champions for 4 slots, 14 elites for 12), so WHICH heroes get offered
        // varies run to run. Without a seed this test's roster assertions silently
        // depend on that draw and only fail some of the time.
        GameState state = Game.newFullDraftMatch(alwaysFirst, silent, new Random(20260827L)).getState();

        assertEquals(2, state.getPlayers().size());
        for (Player player : state.getPlayers()) {
            List<Unit> units = player.getUnits();
            assertEquals(14, units.size());
            assertEquals(1, units.stream().filter(u -> u.getUnitType() == UnitType.CHAMPION).count());
            assertEquals(3, units.stream().filter(u -> u.getUnitType() == UnitType.ELITE).count());
            assertEquals(10, units.stream().filter(u -> u.getUnitType() == UnitType.BASIC).count());
            assertTrue(player.getChampion().isPresent());

            // Kit-wiring is NOT asserted here any more - a random draft only ever
            // reveals a subset of the pool, so doing it here silently tested whichever
            // heroes happened to be drawn. DraftPoolCoverageTest checks all of them.
        }
    }
}
