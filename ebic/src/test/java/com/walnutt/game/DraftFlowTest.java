package com.walnutt.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.data.UnitDefinition;
import com.walnutt.map.GameMap;
import com.walnutt.map.Tile;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.InputHandler;
import com.walnutt.ui.Renderer;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

class DraftFlowTest {

    @Test
    void runProducesOneChampionAndThreeElitesPerPlayer_neverRepeatingAUnitAcrossBothRosters() {
        JsonDataLoader loader = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());
        Map<String, UnitDefinition> unitDefs = loader.loadAllUnits();
        Map<String, AbilityDefinition> abilityDefs = loader.loadAllAbilities();

        GameMap map = new GameMap(8);
        Player p1 = new Player("Player One", Team.PLAYER_ONE);
        Player p2 = new Player("Player Two", Team.PLAYER_TWO);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setUnitDefinitions(unitDefs);
        state.setAbilityDefinitions(abilityDefs);

        new DraftFlow().run(state, alwaysFirst(), silentRenderer());

        Set<String> namedUnitNames = new HashSet<>();
        for (Player player : List.of(p1, p2)) {
            List<Unit> units = player.getUnits();
            assertEquals(14, units.size());
            assertEquals(1, units.stream().filter(u -> u.getUnitType() == UnitType.CHAMPION).count());
            assertEquals(3, units.stream().filter(u -> u.getUnitType() == UnitType.ELITE).count());
            assertEquals(10, units.stream().filter(u -> u.getUnitType() == UnitType.BASIC).count());

            for (Unit unit : units) {
                if (unit.getUnitType() == UnitType.BASIC) {
                    continue;
                }
                assertTrue(namedUnitNames.add(unit.getName()),
                    unit.getName() + " was drafted by both players - pool exclusion failed");
            }
        }
    }

    @Test
    void throwsIfThePoolRunsOutMidRound() {
        Map<String, UnitDefinition> tinyPool = Map.of(
            "champ_a", new UnitDefinition("Champ A", "champion", 100, 10, 10, 10, List.of()),
            "champ_b", new UnitDefinition("Champ B", "champion", 100, 10, 10, 10, List.of()),
            "champ_c", new UnitDefinition("Champ C", "champion", 100, 10, 10, 10, List.of()),
            "elite_a", new UnitDefinition("Elite A", "elite", 100, 10, 10, 10, List.of()),
            "elite_b", new UnitDefinition("Elite B", "elite", 100, 10, 10, 10, List.of())
        );

        GameMap map = new GameMap(8);
        Player p1 = new Player("Player One", Team.PLAYER_ONE);
        Player p2 = new Player("Player Two", Team.PLAYER_TWO);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setUnitDefinitions(tinyPool);
        state.setAbilityDefinitions(Map.of());

        // 3 champions can't split into two pairs (needs 4); should fail fast rather
        // than silently offering a short pair.
        assertThrows(IllegalStateException.class, () -> new DraftFlow().run(state, alwaysFirst(), silentRenderer()));
    }

    private InputHandler alwaysFirst() {
        return new InputHandler() {
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
