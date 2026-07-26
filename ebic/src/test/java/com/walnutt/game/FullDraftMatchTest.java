package com.walnutt.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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
        Renderer silent = new Renderer() {
            @Override public void render(GameState s) {}
            @Override public void renderMessage(String message) {}
            @Override public void renderGameOver(GameState s) {}
            @Override public void renderDraftRound(String roundLabel, Player p1, List<UnitDefinition> p1Options,
                                                     Player p2, List<UnitDefinition> p2Options) {}
        };

        GameState state = Game.newFullDraftMatch(alwaysFirst, silent).getState();

        assertEquals(2, state.getPlayers().size());
        for (Player player : state.getPlayers()) {
            List<Unit> units = player.getUnits();
            assertEquals(14, units.size());
            assertEquals(1, units.stream().filter(u -> u.getUnitType() == UnitType.CHAMPION).count());
            assertEquals(3, units.stream().filter(u -> u.getUnitType() == UnitType.ELITE).count());
            assertEquals(10, units.stream().filter(u -> u.getUnitType() == UnitType.BASIC).count());
            assertTrue(player.getChampion().isPresent());

            // Every drafted champion/elite should have picked up at least one
            // implemented special ability beyond the universal Move+Attack - true for
            // every hero currently in the pool, Lucifer included now that Doom/Infernal
            // Blade are wired up.
            for (Unit unit : units) {
                if (unit.getUnitType() == UnitType.BASIC) {
                    continue;
                }
                long specialAbilities = unit.getAbilities().size() - 2; // minus Move, Attack
                assertTrue(specialAbilities >= 1,
                    unit.getName() + " (" + unit.getUnitType() + ") has no implemented special ability wired");
            }
        }
    }
}
