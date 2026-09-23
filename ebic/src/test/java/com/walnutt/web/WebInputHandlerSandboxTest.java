package com.walnutt.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.game.sandbox.SandboxCommand;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.ui.ActionChoice;

class WebInputHandlerSandboxTest {

    private static JsonObject sandboxAction(String tool) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "action");
        msg.addProperty("kind", "sandbox");
        msg.addProperty("tool", tool);
        return msg;
    }

    private static JsonObject endTurn() {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "action");
        msg.addProperty("kind", "end_turn");
        return msg;
    }

    @Test
    void aSandboxActionPromptCarriesSpawnTiles_andASpawnCommandResolves() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel channel = new RecordingChannel();
        hub.registerSandbox(channel);
        WebInputHandler input = new WebInputHandler(hub, new UnitIdRegistry());

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        GameState state = new GameState(new GameMap(2), List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));
        state.setSandbox(true);

        CompletableFuture<ActionChoice> future = CompletableFuture.supplyAsync(() -> input.chooseAction(state, p1));
        waitUntil(() -> !channel.getSent().isEmpty());
        assertTrue(channel.getSent().get(0).contains("\"sandboxSpawnTiles\""));

        JsonObject spawn = sandboxAction("spawn");
        spawn.addProperty("team", "PLAYER_TWO");
        spawn.addProperty("definitionId", "valor");
        spawn.addProperty("q", 1);
        spawn.addProperty("r", 0);
        input.offer(Team.PLAYER_ONE, spawn);

        ActionChoice choice = future.get(2, TimeUnit.SECONDS);
        assertTrue(choice.isSandbox());
        SandboxCommand.Spawn command = assertInstanceOf(SandboxCommand.Spawn.class, choice.getSandboxCommand());
        assertEquals(Team.PLAYER_TWO, command.team());
        assertEquals("valor", command.definitionId());
        assertEquals(new Position(1, 0), command.position());
    }

    @Test
    void anOrdinaryMatchRefusesSandboxCommands_andOffersNoSpawnTiles() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel channel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, channel);
        WebInputHandler input = new WebInputHandler(hub, new UnitIdRegistry());

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        GameState state = new GameState(new GameMap(2), List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));

        CompletableFuture<ActionChoice> future = CompletableFuture.supplyAsync(() -> input.chooseAction(state, p1));
        waitUntil(() -> !channel.getSent().isEmpty());
        assertFalse(channel.getSent().get(0).contains("sandboxSpawnTiles"));

        input.offer(Team.PLAYER_ONE, sandboxAction("clear"));
        waitUntil(() -> channel.getSent().size() >= 2);
        assertTrue(channel.last().contains("Unrecognized action kind"));

        input.offer(Team.PLAYER_ONE, endTurn());
        assertTrue(future.get(2, TimeUnit.SECONDS).isEndTurn());
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("Condition never became true within 2s");
            }
            Thread.sleep(10);
        }
    }
}
