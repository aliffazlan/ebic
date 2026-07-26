package com.walnutt.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.walnutt.data.UnitDefinition;
import com.walnutt.map.Position;
import com.walnutt.ui.ConcurrentSetupHandler;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

/**
 * Correctness (and, critically, *concurrency*) coverage for ConcurrentSetupFlow -
 * the whole point of the feature is that neither player waits on the other, so a
 * test that only checks the end state (and would pass identically whether the two
 * players ran one after another or genuinely in parallel) wouldn't actually prove
 * anything new. sleepingHandlerProvesBothPlayersRunConcurrently is the one that
 * would fail if a future change accidentally serialized the two players again.
 */
class ConcurrentSetupFlowTest {

    @Test
    @Timeout(10)
    void bothPlayersEndUpWithFullDistinctRostersAndNonOverlappingPlacements() {
        Game game = Game.newConcurrentFullDraftMatch(new InstantPickHandler(), noOpInputHandler(), silentRenderer());
        GameState state = game.getState();

        Player p1 = state.getPlayers().get(0);
        Player p2 = state.getPlayers().get(1);

        assertEquals(14, p1.getUnits().size());
        assertEquals(14, p2.getUnits().size());
        assertEquals(1, countOfType(p1, UnitType.CHAMPION));
        assertEquals(3, countOfType(p1, UnitType.ELITE));
        assertEquals(10, countOfType(p1, UnitType.BASIC));
        assertEquals(1, countOfType(p2, UnitType.CHAMPION));
        assertEquals(3, countOfType(p2, UnitType.ELITE));
        assertEquals(10, countOfType(p2, UnitType.BASIC));

        // No named hero (champion/elite) drafted by both players - the pre-shuffled
        // pool allocation must never hand out the same definition twice.
        Set<String> p1Heroes = heroNames(p1);
        Set<String> p2Heroes = heroNames(p2);
        assertEquals(4, p1Heroes.size());
        assertEquals(4, p2Heroes.size());
        Set<String> overlap = new HashSet<>(p1Heroes);
        overlap.retainAll(p2Heroes);
        assertTrue(overlap.isEmpty(), "same hero drafted by both players: " + overlap);

        // Every unit on the board (both players combined) sits on its own tile.
        Set<Position> allPositions = new HashSet<>();
        for (Unit unit : p1.getUnits()) {
            assertTrue(allPositions.add(unit.getPosition()), "duplicate position: " + unit.getPosition());
        }
        for (Unit unit : p2.getUnits()) {
            assertTrue(allPositions.add(unit.getPosition()), "duplicate position: " + unit.getPosition());
        }
        assertEquals(28, allPositions.size());
    }

    @Test
    @Timeout(10)
    void sleepingHandlerProvesBothPlayersRunConcurrentlyNotSequentially() {
        // 5 blocking calls per player (4 draft rounds + 1 placement), 60ms each.
        // Sequential would take ~2 * 5 * 60ms = 600ms; concurrent should take ~5 * 60ms = 300ms.
        SlowPickHandler handler = new SlowPickHandler(60);
        long start = System.nanoTime();
        Game.newConcurrentFullDraftMatch(handler, noOpInputHandler(), silentRenderer());
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 500,
            "expected roughly one player's worth of delay (~300ms) if truly concurrent, took " + elapsedMs + "ms - "
                + "this strongly suggests the two players are being processed sequentially, not in parallel");
    }

    private long countOfType(Player player, UnitType type) {
        return player.getUnits().stream().filter(u -> u.getUnitType() == type).count();
    }

    private Set<String> heroNames(Player player) {
        Set<String> names = new HashSet<>();
        for (Unit unit : player.getUnits()) {
            if (unit.getUnitType() != UnitType.BASIC) {
                names.add(unit.getName());
            }
        }
        return names;
    }

    /** Always picks the first offered option and confirms the default arrangement unedited, instantly. */
    private static class InstantPickHandler implements ConcurrentSetupHandler {
        @Override
        public UnitDefinition choosePick(GameState state, Player player, String roundLabel, List<UnitDefinition> options) {
            return options.get(0);
        }

        @Override
        public Map<Unit, Position> arrangePlacement(GameState state, Player player, Map<Unit, Position> defaultArrangement) {
            return defaultArrangement;
        }
    }

    /** Same as InstantPickHandler but sleeps before every response, regardless of which team asked. */
    private static class SlowPickHandler implements ConcurrentSetupHandler {
        private final long delayMillis;
        private final AtomicInteger calls = new AtomicInteger();

        SlowPickHandler(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        @Override
        public UnitDefinition choosePick(GameState state, Player player, String roundLabel, List<UnitDefinition> options) {
            sleep();
            return options.get(0);
        }

        @Override
        public Map<Unit, Position> arrangePlacement(GameState state, Player player, Map<Unit, Position> defaultArrangement) {
            sleep();
            return defaultArrangement;
        }

        private void sleep() {
            calls.incrementAndGet();
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private com.walnutt.ui.InputHandler noOpInputHandler() {
        return new com.walnutt.ui.InputHandler() {
            @Override
            public com.walnutt.ui.ActionChoice chooseAction(GameState state, Player player) {
                return com.walnutt.ui.ActionChoice.endTurn();
            }

            @Override
            public com.walnutt.combat.Attribute chooseAttribute(GameState state, Unit unit) {
                return com.walnutt.combat.Attribute.STRENGTH;
            }

            @Override
            public UnitDefinition choosePick(GameState state, Player player, List<UnitDefinition> options) {
                return options.get(0);
            }

            @Override
            public com.walnutt.map.Tile choosePlacementTile(GameState state, Player player, Unit unitToPlace, List<com.walnutt.map.Tile> candidates) {
                return candidates.get(0);
            }
        };
    }

    private com.walnutt.ui.Renderer silentRenderer() {
        return new com.walnutt.ui.Renderer() {
            @Override public void render(GameState state) {}
            @Override public void renderMessage(String message) {}
            @Override public void renderGameOver(GameState state) {}
            @Override public void renderDraftRound(String roundLabel, Player p1, List<UnitDefinition> p1Options,
                                                     Player p2, List<UnitDefinition> p2Options) {}
        };
    }
}
