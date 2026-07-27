package com.walnutt.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;

import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * Covers WebInputHandler's ConcurrentSetupHandler side (choosePick/arrangePlacement) -
 * the web-layer plumbing on top of the already-tested ConcurrentSetupFlow engine code.
 * Same "drive the blocking call on a background thread, resolve it via offer(...) from
 * the main test thread" shape WebInputHandlerTest already uses for the InputHandler side.
 */
class WebInputHandlerConcurrentSetupTest {

    @Test
    void choosePick_sendsSoloDraftRoundThenResolvesPickByDefinitionId() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel p1Channel = new RecordingChannel();
        RecordingChannel p2Channel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, p1Channel);
        hub.register(Team.PLAYER_TWO, p2Channel);
        WebInputHandler input = new WebInputHandler(hub, new UnitIdRegistry());

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));

        List<UnitDefinition> options = List.of(
            new UnitDefinition("Valor", "champion", 1100, 60, 50, 60, List.of()),
            new UnitDefinition("Harbinger", "champion", 900, 40, 40, 80, List.of())
        );
        List<UnitDefinition> opponentOptions = List.of(
            new UnitDefinition("Chronos", "champion", 1200, 70, 110, 60, List.of()),
            new UnitDefinition("Zenith", "champion", 950, 15, 60, 74, List.of())
        );

        CompletableFuture<UnitDefinition> future =
            CompletableFuture.supplyAsync(() -> input.choosePick(state, p1, "Champion", options, opponentOptions));
        waitUntil(() -> !p1Channel.getSent().isEmpty());

        String sent = p1Channel.getSent().get(0);
        assertTrue(sent.contains("\"type\":\"draft_round\""));
        assertTrue(sent.contains("\"roundLabel\":\"Champion\""));
        assertTrue(sent.contains("\"options\""));
        assertTrue(sent.contains("\"opponentOptions\""), "the opponent's same-round options should be shown for transparency");
        assertTrue(sent.contains("Chronos"), "opponentOptions should carry the opponent's actual pre-allocated pair");
        assertTrue(p2Channel.getSent().isEmpty(), "the round is only pushed to the player who's actually picking");

        JsonObject pick = new JsonObject();
        pick.addProperty("type", "pick");
        pick.addProperty("definitionId", "harbinger");
        input.offer(Team.PLAYER_ONE, pick);

        assertEquals("Harbinger", future.get(2, TimeUnit.SECONDS).name());
    }

    @Test
    void choosePick_rejectsAnOptionNotInTheCurrentRound_thenAcceptsAValidRetry() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel channel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, channel);
        WebInputHandler input = new WebInputHandler(hub, new UnitIdRegistry());

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));

        List<UnitDefinition> options = List.of(
            new UnitDefinition("Valor", "champion", 1100, 60, 50, 60, List.of())
        );
        List<UnitDefinition> opponentOptions = List.of(
            new UnitDefinition("Chronos", "champion", 1200, 70, 110, 60, List.of())
        );

        CompletableFuture<UnitDefinition> future =
            CompletableFuture.supplyAsync(() -> input.choosePick(state, p1, "Champion", options, opponentOptions));
        waitUntil(() -> !channel.getSent().isEmpty());

        JsonObject badPick = new JsonObject();
        badPick.addProperty("type", "pick");
        badPick.addProperty("definitionId", "harbinger");
        input.offer(Team.PLAYER_ONE, badPick);

        waitUntil(() -> channel.getSent().stream().anyMatch(s -> s.contains("\"type\":\"message\"")));

        JsonObject goodPick = new JsonObject();
        goodPick.addProperty("type", "pick");
        goodPick.addProperty("definitionId", "valor");
        input.offer(Team.PLAYER_ONE, goodPick);

        assertEquals("Valor", future.get(2, TimeUnit.SECONDS).name());
    }

    @Test
    void arrangePlacement_pushesInitialState_thenAppliesSwapAndMove_thenReturnsWorkingCopyOnConfirm() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel channel = new RecordingChannel();
        RecordingChannel otherChannel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, channel);
        hub.register(Team.PLAYER_TWO, otherChannel);
        UnitIdRegistry ids = new UnitIdRegistry();
        WebInputHandler input = new WebInputHandler(hub, ids);

        GameMap map = new GameMap(5);
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Unit champion = new ChampionUnit("Valor", Team.PLAYER_ONE, new UnitStats(60, 50, 60, 1100));
        Unit basicA = new BasicUnit("P1 Basic 1", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        Unit basicB = new BasicUnit("P1 Basic 2", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        p1.addUnit(champion);
        p1.addUnit(basicA);
        p1.addUnit(basicB);
        String championId = ids.idFor(champion);
        String basicAId = ids.idFor(basicA);
        String basicBId = ids.idFor(basicB);

        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));

        Map<Unit, Position> defaultArrangement = new LinkedHashMap<>();
        defaultArrangement.put(champion, new Position(-5, 0));
        defaultArrangement.put(basicA, new Position(-4, 0));
        defaultArrangement.put(basicB, new Position(-4, 1));

        CompletableFuture<Map<Unit, Position>> future =
            CompletableFuture.supplyAsync(() -> input.arrangePlacement(state, p1, defaultArrangement));
        waitUntil(() -> !channel.getSent().isEmpty());

        // Initial push: this player's own default arrangement, not confirmed, and never
        // leaked to the other team's channel (fog of war during placement too).
        String initial = channel.getSent().get(0);
        assertTrue(initial.contains("\"type\":\"placement_state\""));
        assertTrue(initial.contains("\"confirmed\":false"));
        assertTrue(initial.contains(championId));
        assertTrue(initial.contains("\"legalTiles\""), "legalTiles should be included so the client can highlight the placement zone");
        assertTrue(otherChannel.getSent().isEmpty(), "the opponent must never see this player's placement");

        // Swap champion and basicA.
        JsonObject swap = new JsonObject();
        swap.addProperty("type", "placement_edit");
        swap.addProperty("kind", "swap");
        swap.addProperty("unitId", championId);
        swap.addProperty("targetUnitId", basicAId);
        input.offer(Team.PLAYER_ONE, swap);
        waitUntil(() -> channel.getSent().size() >= 2);
        assertTrue(channel.getSent().get(1).contains("\"confirmed\":false"));

        // Reject: move basicB onto (-5, 0), which is basicA's tile after the swap above
        // (occupied within the working copy, even though nothing's on the real GameMap yet).
        JsonObject badMove = new JsonObject();
        badMove.addProperty("type", "placement_edit");
        badMove.addProperty("kind", "move");
        badMove.addProperty("unitId", basicBId);
        badMove.addProperty("q", -5);
        badMove.addProperty("r", 0);
        input.offer(Team.PLAYER_ONE, badMove);
        waitUntil(() -> channel.getSent().stream().anyMatch(s -> s.contains("\"type\":\"message\"")));
        long placementPushesAfterReject = channel.getSent().stream().filter(s -> s.contains("\"type\":\"placement_state\"")).count();
        assertEquals(2, placementPushesAfterReject, "a rejected edit must not push a new placement_state");

        // Valid move: relocate basicB to an empty tile.
        JsonObject move = new JsonObject();
        move.addProperty("type", "placement_edit");
        move.addProperty("kind", "move");
        move.addProperty("unitId", basicBId);
        move.addProperty("q", 0);
        move.addProperty("r", 0);
        input.offer(Team.PLAYER_ONE, move);
        waitUntil(() -> channel.getSent().stream().filter(s -> s.contains("\"type\":\"placement_state\"")).count() == 3);

        // Confirm.
        JsonObject confirm = new JsonObject();
        confirm.addProperty("type", "placement_edit");
        confirm.addProperty("kind", "confirm");
        input.offer(Team.PLAYER_ONE, confirm);

        Map<Unit, Position> result = future.get(2, TimeUnit.SECONDS);
        assertEquals(new Position(-4, 0), result.get(champion), "champion swapped into basicA's original spot");
        assertEquals(new Position(-5, 0), result.get(basicA), "basicA swapped into champion's original spot");
        assertEquals(new Position(0, 0), result.get(basicB), "basicB relocated by the valid move");

        String last = channel.getSent().get(channel.getSent().size() - 1);
        assertTrue(last.contains("\"type\":\"placement_state\""));
        assertTrue(last.contains("\"confirmed\":true"));
        assertTrue(otherChannel.getSent().isEmpty(), "still never leaked to the opponent, even at confirm");
    }

    @Test
    void arrangePlacement_rejectsAMoveOutsideThePlacementZone() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel channel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, channel);
        UnitIdRegistry ids = new UnitIdRegistry();
        WebInputHandler input = new WebInputHandler(hub, ids);

        // A big map so there's genuine "outside the zone" territory to test against -
        // a small map (like the other test's radius 5) would have its whole surface
        // within PLACEMENT_ZONE_RADIUS of the corner, which wouldn't exercise the check.
        GameMap map = new GameMap(20);
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Unit champion = new ChampionUnit("Valor", Team.PLAYER_ONE, new UnitStats(60, 50, 60, 1100));
        p1.addUnit(champion);
        String championId = ids.idFor(champion);

        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));

        Map<Unit, Position> defaultArrangement = new LinkedHashMap<>();
        defaultArrangement.put(champion, new Position(-20, 0)); // this player's actual corner anchor

        CompletableFuture<Map<Unit, Position>> future =
            CompletableFuture.supplyAsync(() -> input.arrangePlacement(state, p1, defaultArrangement));
        waitUntil(() -> !channel.getSent().isEmpty());

        // Center of the map, hexDistance 20 from the anchor - way outside any reasonable zone.
        JsonObject farMove = new JsonObject();
        farMove.addProperty("type", "placement_edit");
        farMove.addProperty("kind", "move");
        farMove.addProperty("unitId", championId);
        farMove.addProperty("q", 0);
        farMove.addProperty("r", 0);
        input.offer(Team.PLAYER_ONE, farMove);
        waitUntil(() -> channel.getSent().stream().anyMatch(s -> s.contains("outside your placement area")));
        long placementPushesAfterReject = channel.getSent().stream().filter(s -> s.contains("\"type\":\"placement_state\"")).count();
        assertEquals(1, placementPushesAfterReject, "an out-of-zone move must be rejected, not applied");

        // A nearby move (still within the zone) should still work fine.
        JsonObject nearMove = new JsonObject();
        nearMove.addProperty("type", "placement_edit");
        nearMove.addProperty("kind", "move");
        nearMove.addProperty("unitId", championId);
        nearMove.addProperty("q", -18);
        nearMove.addProperty("r", 0);
        input.offer(Team.PLAYER_ONE, nearMove);
        waitUntil(() -> channel.getSent().stream().filter(s -> s.contains("\"type\":\"placement_state\"")).count() == 2);

        JsonObject confirm = new JsonObject();
        confirm.addProperty("type", "placement_edit");
        confirm.addProperty("kind", "confirm");
        input.offer(Team.PLAYER_ONE, confirm);

        Map<Unit, Position> result = future.get(2, TimeUnit.SECONDS);
        assertEquals(new Position(-18, 0), result.get(champion));
    }

    @Test
    void choosePickAndArrangePlacement_doNotSerializeAcrossTeams() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel p1Channel = new RecordingChannel();
        RecordingChannel p2Channel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, p1Channel);
        hub.register(Team.PLAYER_TWO, p2Channel);
        WebInputHandler input = new WebInputHandler(hub, new UnitIdRegistry());

        GameMap map = new GameMap(3);
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));

        List<UnitDefinition> options = List.of(
            new UnitDefinition("Valor", "champion", 1100, 60, 50, 60, List.of()),
            new UnitDefinition("Harbinger", "champion", 900, 40, 40, 80, List.of())
        );

        // Both players' picks are started concurrently on their own background threads,
        // exactly like ConcurrentSetupFlow's two dedicated setup threads would call in.
        CompletableFuture<UnitDefinition> p1Future =
            CompletableFuture.supplyAsync(() -> input.choosePick(state, p1, "Champion", options, options));
        CompletableFuture<UnitDefinition> p2Future =
            CompletableFuture.supplyAsync(() -> input.choosePick(state, p2, "Champion", options, options));
        waitUntil(() -> !p1Channel.getSent().isEmpty() && !p2Channel.getSent().isEmpty());

        // Resolve only team TWO's pick. If this class accidentally shared a single
        // queue/lock across teams instead of the per-team inbox, team two's response
        // would either never arrive or would incorrectly unblock/interfere with team one.
        JsonObject pickTwo = new JsonObject();
        pickTwo.addProperty("type", "pick");
        pickTwo.addProperty("definitionId", "harbinger");
        input.offer(Team.PLAYER_TWO, pickTwo);

        assertEquals("Harbinger", p2Future.get(2, TimeUnit.SECONDS).name());
        assertFalse(p1Future.isDone(), "team one must still be blocked - proves the teams aren't sharing a queue/lock");

        JsonObject pickOne = new JsonObject();
        pickOne.addProperty("type", "pick");
        pickOne.addProperty("definitionId", "valor");
        input.offer(Team.PLAYER_ONE, pickOne);

        assertEquals("Valor", p1Future.get(2, TimeUnit.SECONDS).name());

        // Now the same proof for arrangePlacement: start both concurrently, resolve
        // only team ONE's confirm first, and check team TWO is unaffected/still blocked.
        Unit p1Champion = new ChampionUnit("Valor", Team.PLAYER_ONE, new UnitStats(60, 50, 60, 1100));
        p1.addUnit(p1Champion);
        Unit p2Champion = new ChampionUnit("Harbinger", Team.PLAYER_TWO, new UnitStats(40, 40, 80, 900));
        p2.addUnit(p2Champion);

        Map<Unit, Position> p1Default = Map.of(p1Champion, new Position(-3, 0));
        Map<Unit, Position> p2Default = Map.of(p2Champion, new Position(3, 0));

        CompletableFuture<Map<Unit, Position>> p1Placement =
            CompletableFuture.supplyAsync(() -> input.arrangePlacement(state, p1, p1Default));
        CompletableFuture<Map<Unit, Position>> p2Placement =
            CompletableFuture.supplyAsync(() -> input.arrangePlacement(state, p2, p2Default));
        waitUntil(() -> p1Channel.getSent().stream().anyMatch(s -> s.contains("placement_state"))
            && p2Channel.getSent().stream().anyMatch(s -> s.contains("placement_state")));

        JsonObject confirmOne = new JsonObject();
        confirmOne.addProperty("type", "placement_edit");
        confirmOne.addProperty("kind", "confirm");
        input.offer(Team.PLAYER_ONE, confirmOne);

        assertEquals(new Position(-3, 0), p1Placement.get(2, TimeUnit.SECONDS).get(p1Champion));
        assertFalse(p2Placement.isDone(), "team two's placement must still be blocked on its own queue, independent of team one's confirm");

        JsonObject confirmTwo = new JsonObject();
        confirmTwo.addProperty("type", "placement_edit");
        confirmTwo.addProperty("kind", "confirm");
        input.offer(Team.PLAYER_TWO, confirmTwo);

        assertEquals(new Position(3, 0), p2Placement.get(2, TimeUnit.SECONDS).get(p2Champion));
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
