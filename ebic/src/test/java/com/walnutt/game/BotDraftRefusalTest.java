package com.walnutt.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.walnutt.ai.BotConfig;
import com.walnutt.ai.BotHandler;
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

/**
 * A draft round offering the computer two heroes it refuses is dealt again before anyone
 * sees it, rather than resolved by the bot picking one anyway.
 *
 * This was reachable before Shawl - maxwell and joker were already both refused, so a round
 * of exactly those two put an unplayable hero on the bot's roster. Shawl makes it likelier,
 * which is what prompted the fix.
 */
class BotDraftRefusalTest {

    /** A seat that refuses whatever the test names, standing in for the bot's own preferences. */
    private static final class RefusingSeat implements InputHandler {
        private final Set<String> refused;
        private final List<List<String>> offered = new ArrayList<>();

        RefusingSeat(Set<String> refused) {
            this.refused = refused;
        }

        @Override
        public boolean refusesToDraft(Player player, UnitDefinition definition) {
            return refused.contains(definition.name());
        }

        @Override
        public UnitDefinition choosePick(GameState state, Player player, List<UnitDefinition> options) {
            offered.add(options.stream().map(UnitDefinition::name).toList());
            return options.stream().filter(o -> !refused.contains(o.name())).findFirst().orElse(options.get(0));
        }

        @Override
        public ActionChoice chooseAction(GameState state, Player player) {
            return ActionChoice.endTurn();
        }

        @Override
        public Attribute chooseAttribute(GameState state, Unit unit, Unit opponent) {
            return Attribute.STRENGTH;
        }

        @Override
        public Tile choosePlacementTile(GameState state, Player player, Unit toPlace, List<Tile> candidates) {
            return candidates.get(0);
        }
    }

    private static Renderer silentRenderer() {
        return new Renderer() {
            @Override public void render(GameState s) { }
            @Override public void renderMessage(String message) { }
            @Override public void renderGameOver(GameState s) { }
            @Override public void renderDraftRound(String label, Player p1, List<UnitDefinition> p1Options,
                                                    Player p2, List<UnitDefinition> p2Options) { }
        };
    }

    /**
     * A pool where all but two elites are refused, so a naive deal hits an all-refused pair
     * almost immediately - the case a rare random one would only reach on an unlucky seed.
     */
    private static Map<String, UnitDefinition> poolWithRefusedElites(int total, int playable) {
        Map<String, UnitDefinition> pool = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            pool.put("champ_" + i, new UnitDefinition("Champ " + i, "champion", 100, 10, 10, 10, List.of()));
        }
        for (int i = 0; i < total; i++) {
            String name = i < playable ? "Playable " + i : "Refused " + i;
            pool.put("elite_" + i, new UnitDefinition(name, "elite", 100, 10, 10, 10, List.of()));
        }
        return pool;
    }

    private static Set<String> refusedNames(Map<String, UnitDefinition> pool) {
        Set<String> names = new java.util.HashSet<>();
        pool.values().stream().map(UnitDefinition::name)
            .filter(name -> name.startsWith("Refused")).forEach(names::add);
        return names;
    }

    private static GameState stateWith(Map<String, UnitDefinition> pool, long seed) {
        Player p1 = new Player("Player One", Team.PLAYER_ONE);
        Player p2 = new Player("Player Two", Team.PLAYER_TWO);
        GameState state = new GameState(new GameMap(8), List.of(p1, p2), new Random(seed));
        state.setUnitDefinitions(pool);
        state.setAbilityDefinitions(Map.of());
        return state;
    }

    /**
     * The real roster shape: 16 elites of which the computer refuses three. Both seats here
     * refuse (DraftFlow hands one handler to both players, which is also the self-play case),
     * so this is the worst configuration the shipped game can actually produce.
     *
     * With 13 playable elites against 6 pairs there is always something to redeal into, so the
     * guarantee is absolute: no seat is ever shown two heroes it refuses.
     */
    @Test
    @Timeout(20)
    void withTheRealRosterAPairIsNeverAllRefused() {
        Map<String, UnitDefinition> units =
            new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllUnits();
        Set<String> refused = Set.of("Maxwell", "Joker", "Shawl");

        for (long seed = 0; seed < 60; seed++) {
            RefusingSeat seat = new RefusingSeat(refused);
            Player p1 = new Player("Player One", Team.PLAYER_ONE);
            Player p2 = new Player("Player Two", Team.PLAYER_TWO);
            GameState state = new GameState(new GameMap(8), List.of(p1, p2), new Random(seed));
            state.setUnitDefinitions(units);
            state.setAbilityDefinitions(Map.of());

            new DraftFlow().run(state, seat, silentRenderer());

            for (List<String> pair : seat.offered) {
                assertEquals(2, pair.size());
                assertTrue(pair.stream().anyMatch(name -> !refused.contains(name)),
                    "seed " + seed + " dealt an all-refused pair: " + pair);
            }
        }
    }

    /**
     * The reroll must not become a hang, and it must not pretend to a guarantee it cannot
     * keep. A pool with a single playable elite for six pairs is arithmetically impossible to
     * satisfy - four pairs MUST come out all-refused - so what is asserted here is only that
     * the draft terminates and still hands back full rosters, rather than spinning on a
     * reshuffle that could never help.
     */
    @Test
    @Timeout(10)
    void anImpossiblePoolTerminatesRatherThanSpinning() {
        Map<String, UnitDefinition> pool = poolWithRefusedElites(12, 1);
        Set<String> refused = refusedNames(pool);
        RefusingSeat seat = new RefusingSeat(refused);
        GameState state = stateWith(pool, 7);

        new DraftFlow().run(state, seat, silentRenderer());

        assertEquals(14, state.getPlayers().get(0).getUnits().size());
        assertEquals(14, state.getPlayers().get(1).getUnits().size());
        // It did have to give up on some of them - that is the honest outcome, not a bug.
        assertTrue(seat.offered.stream()
            .anyMatch(pair -> pair.stream().allMatch(refused::contains)),
            "with one playable elite for six pairs, some pairs are unavoidably all-refused");
    }

    @Test
    void aHumanSeatRefusesNothingAndIsOfferedTheWholeRoster() {
        RefusingSeat neverRefuses = new RefusingSeat(Set.of());
        Player player = new Player("Human", Team.PLAYER_ONE);
        UnitDefinition shawl = new UnitDefinition("Shawl", "elite", 760, 48, 32, 54, List.of());

        assertFalse(neverRefuses.refusesToDraft(player, shawl));
        // The interface default is what a real human handler inherits, and it must agree.
        assertFalse(new InputHandler() {
            @Override public ActionChoice chooseAction(GameState s, Player p) {
                throw new UnsupportedOperationException();
            }

            @Override public Attribute chooseAttribute(GameState s, Unit u, Unit o) {
                throw new UnsupportedOperationException();
            }

            @Override public UnitDefinition choosePick(GameState s, Player p, List<UnitDefinition> o) {
                throw new UnsupportedOperationException();
            }

            @Override public Tile choosePlacementTile(GameState s, Player p, Unit u, List<Tile> c) {
                throw new UnsupportedOperationException();
            }
        }.refusesToDraft(player, shawl));
    }

    /** The computer's actual refusal list, read through the handler the match really uses. */
    @Test
    void theComputerRefusesShawlAlongsideMaxwellAndJoker() {
        Map<String, UnitDefinition> units =
            new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllUnits();
        BotHandler bot = new BotHandler(BotConfig.standard());
        Player player = new Player("Bot", Team.PLAYER_TWO);

        for (String id : List.of("shawl", "maxwell", "joker")) {
            assertTrue(bot.refusesToDraft(player, units.get(id)), id + " should be refused by the bot");
        }
        for (String id : List.of("valor", "yuki", "thaddeus")) {
            assertFalse(bot.refusesToDraft(player, units.get(id)), id + " should still be drafted");
        }
    }

    /** Shawl is a real draft option for a human now - the pool query is the source of truth. */
    @Test
    void shawlIsInTheHumanDraftPool() {
        JsonDataLoader loader = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());
        Map<String, UnitDefinition> units = loader.loadAllUnits();
        Map<String, AbilityDefinition> abilities = loader.loadAllAbilities();

        assertTrue(new DraftService().getAvailableElites(units).contains("shawl"));
        // And he arrives with a real kit rather than as a Move+Attack shell.
        Unit shawl = com.walnutt.unit.UnitFactory.createFromDefinition(
            units.get("shawl"), Team.PLAYER_ONE, abilities);
        List<String> ids = shawl.getAbilities().stream()
            .map(a -> a.getDefinitionId()).filter(id -> id != null).toList();
        assertTrue(ids.contains("hidden_potential"), ids.toString());
        assertTrue(ids.contains("acidic_brew"), ids.toString());
    }
}
