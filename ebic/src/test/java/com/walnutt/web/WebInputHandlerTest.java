package com.walnutt.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import com.walnutt.ability.Move;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.ActionChoice;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * WebInputHandler.chooseX methods block the calling thread on a per-team queue.
 * Each test runs the blocking call on a background thread and resolves it from
 * the main test thread via offer(...), the same "second thread completes a
 * pending wait" shape a real WS message handler thread would trigger - no real
 * Jetty/WS server needed, per the RecordingChannel double.
 */
class WebInputHandlerTest {

    @Test
    void chooseAttribute_blocksThenReturnsParsedValue_andRoutesPromptToTheUnitsOwnTeam() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel p1Channel = new RecordingChannel();
        RecordingChannel p2Channel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, p1Channel);
        hub.register(Team.PLAYER_TWO, p2Channel);

        UnitIdRegistry ids = new UnitIdRegistry();
        WebInputHandler input = new WebInputHandler(hub, ids);

        Unit defender = new ChampionUnit("Defender", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 100));
        String unitId = ids.idFor(defender);

        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(new Player("P1", Team.PLAYER_ONE), new Player("P2", Team.PLAYER_TWO)), new Random(1));

        CompletableFuture<Attribute> future = CompletableFuture.supplyAsync(() -> input.chooseAttribute(state, defender));

        waitUntil(() -> !p2Channel.getSent().isEmpty());
        assertTrue(p2Channel.getSent().get(0).contains("\"kind\":\"attribute\""));
        assertTrue(p2Channel.getSent().get(0).contains(unitId));
        assertTrue(p1Channel.getSent().isEmpty(), "the prompt is for the defender's (team two's) side, not team one's");

        JsonObject response = new JsonObject();
        response.addProperty("type", "attribute");
        response.addProperty("value", "AGILITY");
        input.offer(Team.PLAYER_TWO, response);

        assertEquals(Attribute.AGILITY, future.get(2, TimeUnit.SECONDS));
    }

    @Test
    void chooseAction_endTurn() throws Exception {
        ChannelHub hub = new ChannelHub();
        hub.register(Team.PLAYER_ONE, new RecordingChannel());
        WebInputHandler input = new WebInputHandler(hub, new UnitIdRegistry());

        Player player = new Player("P1", Team.PLAYER_ONE);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(player, new Player("P2", Team.PLAYER_TWO)), new Random(1));

        CompletableFuture<ActionChoice> future = CompletableFuture.supplyAsync(() -> input.chooseAction(state, player));

        JsonObject response = new JsonObject();
        response.addProperty("type", "action");
        response.addProperty("kind", "end_turn");
        input.offer(Team.PLAYER_ONE, response);

        assertTrue(future.get(2, TimeUnit.SECONDS).isEndTurn());
    }

    @Test
    void chooseAction_resolvesUnitAbilityAndTileTarget_forAMoveAction() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel channel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, channel);
        UnitIdRegistry ids = new UnitIdRegistry();
        WebInputHandler input = new WebInputHandler(hub, ids);

        GameMap map = new GameMap(3);
        Unit unit = new BasicUnit("Basic", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        unit.addAbility(new Move());
        map.moveUnit(unit, map.getTile(new Position(0, 0)));
        Tile destination = map.getTile(new Position(1, 0));

        Player player = new Player("P1", Team.PLAYER_ONE);
        player.addUnit(unit);
        String unitId = ids.idFor(unit);

        GameState state = new GameState(map, List.of(player, new Player("P2", Team.PLAYER_TWO)), new Random(1));

        CompletableFuture<ActionChoice> future = CompletableFuture.supplyAsync(() -> input.chooseAction(state, player));
        waitUntil(() -> !channel.getSent().isEmpty());

        JsonObject response = new JsonObject();
        response.addProperty("type", "action");
        response.addProperty("kind", "ability");
        response.addProperty("unitId", unitId);
        response.addProperty("abilityId", "move");
        response.addProperty("targetKind", "tile");
        response.addProperty("q", 1);
        response.addProperty("r", 0);
        input.offer(Team.PLAYER_ONE, response);

        ActionChoice choice = future.get(2, TimeUnit.SECONDS);
        assertEquals(unit, choice.getUnit());
        assertTrue(choice.getAbility() instanceof Move);
        assertEquals(destination, ((TileTarget) choice.getTarget()).getTile());
    }

    @Test
    void chooseAction_rejectsAUnitFromTheWrongTeam_thenAcceptsAValidRetry() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel channel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, channel);
        UnitIdRegistry ids = new UnitIdRegistry();
        WebInputHandler input = new WebInputHandler(hub, ids);

        Unit enemyUnit = new BasicUnit("Enemy Basic", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300));
        String enemyId = ids.idFor(enemyUnit);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));

        CompletableFuture<ActionChoice> future = CompletableFuture.supplyAsync(() -> input.chooseAction(state, p1));
        waitUntil(() -> !channel.getSent().isEmpty());

        JsonObject badAttempt = new JsonObject();
        badAttempt.addProperty("type", "action");
        badAttempt.addProperty("kind", "ability");
        badAttempt.addProperty("unitId", enemyId);
        badAttempt.addProperty("abilityId", "move");
        badAttempt.addProperty("targetKind", "none");
        input.offer(Team.PLAYER_ONE, badAttempt);

        JsonObject endTurn = new JsonObject();
        endTurn.addProperty("type", "action");
        endTurn.addProperty("kind", "end_turn");
        input.offer(Team.PLAYER_ONE, endTurn);

        assertTrue(future.get(2, TimeUnit.SECONDS).isEndTurn());
        assertTrue(channel.getSent().stream().anyMatch(s -> s.contains("\"type\":\"message\"")));
    }

    @Test
    void choosePick_matchesByNormalizedDefinitionId() throws Exception {
        ChannelHub hub = new ChannelHub();
        hub.register(Team.PLAYER_ONE, new RecordingChannel());
        WebInputHandler input = new WebInputHandler(hub, new UnitIdRegistry());

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        List<UnitDefinition> options = List.of(
            new UnitDefinition("Valor", "champion", 1100, 60, 50, 60, List.of()),
            new UnitDefinition("Harbinger", "champion", 900, 40, 40, 80, List.of())
        );
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));

        CompletableFuture<UnitDefinition> future = CompletableFuture.supplyAsync(() -> input.choosePick(state, p1, options));

        JsonObject pick = new JsonObject();
        pick.addProperty("type", "pick");
        pick.addProperty("definitionId", "harbinger");
        input.offer(Team.PLAYER_ONE, pick);

        assertEquals("Harbinger", future.get(2, TimeUnit.SECONDS).name());
    }

    @Test
    void choosePlacementTile_includesCandidatesInThePrompt_andMatchesByCoordinate() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel channel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, channel);
        UnitIdRegistry ids = new UnitIdRegistry();
        WebInputHandler input = new WebInputHandler(hub, ids);

        GameMap map = new GameMap(3);
        Unit unit = new BasicUnit("Basic", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        p1.addUnit(unit);
        List<Tile> candidates = List.of(map.getTile(new Position(0, 0)), map.getTile(new Position(1, 0)));
        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));

        CompletableFuture<Tile> future = CompletableFuture.supplyAsync(() -> input.choosePlacementTile(state, p1, unit, candidates));
        waitUntil(() -> !channel.getSent().isEmpty());
        assertTrue(channel.getSent().get(0).contains("\"candidates\""));

        JsonObject placement = new JsonObject();
        placement.addProperty("type", "placement");
        placement.addProperty("q", 1);
        placement.addProperty("r", 0);
        input.offer(Team.PLAYER_ONE, placement);

        assertEquals(candidates.get(1), future.get(2, TimeUnit.SECONDS));
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
