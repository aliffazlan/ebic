package com.walnutt.ui;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * The terminal renderer walks a q/r bounding box rather than the real tile set, so it is
 * the one place a trimmed map can hand out a null tile. It used to dereference that
 * straight away.
 */
class TerminalRendererTest {

    private static String render(GameMap map) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        Unit unit = new BasicUnit("Scout", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        p1.addUnit(unit);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(unit, map.getTile(new Position(0, 0)));

        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            new TerminalRenderer().render(state);
        } finally {
            System.setOut(original);
        }
        return captured.toString(StandardCharsets.UTF_8);
    }

    @Test
    void rendersATrimmedMapWithoutDereferencingMissingTiles() {
        GameMap trimmed = new GameMap(7, 5);

        String output = assertDoesNotThrow(() -> render(trimmed));

        assertTrue(output.contains("Scout"), "the roster listing should still print");
        // One printed row per r, over the full radius-height bounding box the renderer
        // walks - the trimmed rows come out blank rather than crashing.
        long boardRows = output.lines().takeWhile(line -> !line.startsWith("Player ")).count();
        assertTrue(boardRows >= 2 * 7 + 1, "expected a row per r, got " + boardRows);
    }

    @Test
    void stillRendersAnUntrimmedMap() {
        String output = assertDoesNotThrow(() -> render(new GameMap(3)));

        assertTrue(output.contains("Scout"));
        assertEquals(1, output.lines().filter(l -> l.contains("Scout")).count());
    }
}
