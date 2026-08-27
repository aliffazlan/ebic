package com.walnutt.game;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.data.UnitDefinition;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.ConcurrentSetupHandler;
import com.walnutt.ui.InputHandler;
import com.walnutt.ui.Renderer;
import com.walnutt.ui.TerminalInputHandler;
import com.walnutt.ui.TerminalRenderer;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitFactory;

/**
 * Owns the main loop. Conceptually: initialize GameState/map/players, then
 * alternate TurnManager.takeTurn calls until a Champion dies.
 */
public class Game {
    /**
     * Radius 7 trimmed to rows |r| <= 5: an elongated hexagon of 135 tiles (a regular
     * radius-7 hex would be 169, the old radius-8 one 217). Wide enough to flank in,
     * shallow enough that ~28 units meet in a couple of turns instead of marching.
     */
    private static final int FULL_MATCH_MAP_RADIUS = 7;
    private static final int FULL_MATCH_MAP_ROW_LIMIT = 5;
    private static final int MINIMAL_MATCH_MAP_RADIUS = 3;

    private final GameState state;
    private final Renderer renderer;
    private final TurnManager turnManager = new TurnManager();

    private Game(GameState state, Renderer renderer) {
        this.state = state;
        this.renderer = renderer;
    }

    /**
     * Full draft pool match: 1 champion + 3 elites + 10 basics per side, picked
     * interactively via DraftFlow (random 2-choice pairs, opponent's pair visible,
     * pool never repeats) and placed interactively via PlacementFlow (each player
     * chooses a tile for every unit within their own starting zone). Terminal I/O
     * by default; see the (InputHandler, Renderer) overload to drive it from a test.
     */
    public static Game newFullDraftMatch() {
        return newFullDraftMatch(null, null);
    }

    /**
     * Same as {@link #newFullDraftMatch()} but with the input/output swappable -
     * lets a test drive the interactive draft/placement flow with a scripted
     * InputHandler and a silent Renderer instead of blocking on real terminal
     * input, the same way TurnManagerIntegrationTest scripts the main game loop.
     * Passing null for either uses the real terminal implementation.
     */
    public static Game newFullDraftMatch(InputHandler input, Renderer renderer) {
        return newFullDraftMatch(input, renderer, new Random());
    }

    /**
     * Seedable variant. The draft pool is shuffled from this Random, so a fixed seed
     * makes which heroes get offered reproducible - without one, a test asserting on
     * the drafted roster silently depends on which subset of the pool happened to be
     * revealed, and only fails some of the time.
     */
    public static Game newFullDraftMatch(InputHandler input, Renderer renderer, Random random) {
        JsonDataLoader loader = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());
        Map<String, UnitDefinition> unitDefs = loader.loadAllUnits();
        Map<String, AbilityDefinition> abilityDefs = loader.loadAllAbilities();

        GameMap map = new GameMap(FULL_MATCH_MAP_RADIUS, FULL_MATCH_MAP_ROW_LIMIT);
        Player playerOne = new Player("Player One", Team.PLAYER_ONE);
        Player playerTwo = new Player("Player Two", Team.PLAYER_TWO);

        GameState state = new GameState(map, List.of(playerOne, playerTwo), random);
        state.setUnitDefinitions(unitDefs);
        state.setAbilityDefinitions(abilityDefs);
        InputHandler actualInput = input != null ? input : new TerminalInputHandler(new Scanner(System.in));
        Renderer actualRenderer = renderer != null ? renderer : new TerminalRenderer();
        state.setInputHandler(actualInput);

        new DraftFlow().run(state, actualInput, actualRenderer);
        new PlacementFlow().run(state, actualInput, actualRenderer);

        return new Game(state, actualRenderer);
    }

    /**
     * Same shape as {@link #newFullDraftMatch()} but draft+placement run concurrently,
     * one thread per player (see ConcurrentSetupFlow) - neither player waits on the
     * other's pace during either phase. {@code setupHandler} drives the draft picks
     * and placement arrangement (see ConcurrentSetupHandler); {@code input}/{@code
     * renderer} take over for the ordinary turn-based match loop afterward, exactly
     * like {@link #newFullDraftMatch(InputHandler, Renderer)} - in practice a web
     * bridge implements all three via the same underlying object, but they're kept
     * as separate parameters so a test can substitute different doubles for each role.
     * This does not touch DraftFlow/PlacementFlow/newFullDraftMatch at all - terminal
     * mode (a single shared stdin) has no meaningful way to run two players
     * concurrently, so it keeps using the strictly-sequential path.
     */
    public static Game newConcurrentFullDraftMatch(ConcurrentSetupHandler setupHandler, InputHandler input, Renderer renderer) {
        JsonDataLoader loader = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());
        Map<String, UnitDefinition> unitDefs = loader.loadAllUnits();
        Map<String, AbilityDefinition> abilityDefs = loader.loadAllAbilities();

        GameMap map = new GameMap(FULL_MATCH_MAP_RADIUS, FULL_MATCH_MAP_ROW_LIMIT);
        Player playerOne = new Player("Player One", Team.PLAYER_ONE);
        Player playerTwo = new Player("Player Two", Team.PLAYER_TWO);

        GameState state = new GameState(map, List.of(playerOne, playerTwo), new Random());
        state.setUnitDefinitions(unitDefs);
        state.setAbilityDefinitions(abilityDefs);
        state.setInputHandler(input);

        ConcurrentSetupFlow.run(state, setupHandler);

        return new Game(state, renderer);
    }

    /** chat.txt's minimal first playable test: 1 Champion + 1 Basic per side on a small hex map. */
    public static Game newMinimalMatch() {
        JsonDataLoader loader = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());
        Map<String, UnitDefinition> unitDefs = loader.loadAllUnits();
        Map<String, AbilityDefinition> abilityDefs = loader.loadAllAbilities();

        DraftService draft = new DraftService();
        String championId = draft.getAvailableChampions(unitDefs).get(0);

        Player playerOne = new Player("Player One", Team.PLAYER_ONE);
        playerOne.addUnit(UnitFactory.createFromDefinition(unitDefs.get(championId), Team.PLAYER_ONE, abilityDefs));
        playerOne.addUnit(UnitFactory.createBasic("Player One Basic 1", Team.PLAYER_ONE));

        Player playerTwo = new Player("Player Two", Team.PLAYER_TWO);
        playerTwo.addUnit(UnitFactory.createFromDefinition(unitDefs.get(championId), Team.PLAYER_TWO, abilityDefs));
        playerTwo.addUnit(UnitFactory.createBasic("Player Two Basic 1", Team.PLAYER_TWO));

        GameMap map = new GameMap(MINIMAL_MATCH_MAP_RADIUS);
        return buildGame(map, playerOne, playerTwo, unitDefs, abilityDefs);
    }

    private static Game buildGame(GameMap map, Player playerOne, Player playerTwo,
                                   Map<String, UnitDefinition> unitDefs, Map<String, AbilityDefinition> abilityDefs) {
        int radius = map.getRadius();
        placeArmy(map, playerOne, -radius, 1);
        placeArmy(map, playerTwo, radius, -1);

        GameState state = new GameState(map, List.of(playerOne, playerTwo), new Random());
        state.setInputHandler(new TerminalInputHandler(new Scanner(System.in)));
        state.setUnitDefinitions(unitDefs);
        state.setAbilityDefinitions(abilityDefs);

        return new Game(state, new TerminalRenderer());
    }

    /**
     * Fills the hex column at startQ top-to-bottom, then spills into the next
     * column (stepping by qDirection) once the current one is full.
     */
    private static void placeArmy(GameMap map, Player player, int startQ, int qDirection) {
        int radius = map.getRadius();
        int totalUnits = player.getUnits().size();
        int index = 0;
        int qOffset = 0;

        while (index < totalUnits && qOffset <= 2 * radius) {
            int q = startQ + qDirection * qOffset;
            int rMin = Math.max(-radius, -q - radius);
            int rMax = Math.min(radius, -q + radius);
            for (int r = rMin; r <= rMax && index < totalUnits; r++) {
                Tile tile = map.getTile(new Position(q, r));
                if (tile != null && !tile.isOccupied()) {
                    map.moveUnit(player.getUnits().get(index), tile);
                    index++;
                }
            }
            qOffset++;
        }
    }

    public void start() {
        // Baseline render before the very first turn's own TurnStartEvent (which can
        // trigger turn-1 passive combat, e.g. Dirge's Decay) - without this, a fresh
        // client's first-ever "state" would already reflect post-combat HP with no
        // prior snapshot to diff against, and any "vfx" flushed alongside that first
        // turn's own render() call would have no known unit names to resolve (a real
        // "Unknown takes N damage" bug observed once this way).
        renderer.render(state);
        while (!state.isGameOver()) {
            turnManager.takeTurn(state, state.getInputHandler(), renderer);
        }
        renderer.render(state);
        renderer.renderGameOver(state);
    }

    public GameState getState() {
        return state;
    }
}
