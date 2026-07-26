package com.walnutt.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.walnutt.game.Game;
import com.walnutt.game.GameState;
import com.walnutt.game.Team;
import com.walnutt.web.dto.GameStateSnapshot;

/**
 * Drives the real Game.newFullDraftMatch(InputHandler, Renderer) seam end-to-end
 * through the web bridge (WebInputHandler/WebRenderer/ChannelHub) with scripted
 * RecordingChannels instead of real sockets - the same "inject a test double"
 * pattern DraftFlowTest/PlacementFlowTest/TurnManagerIntegrationTest already use
 * for the terminal InputHandler/Renderer, applied one level up at the web layer.
 * Loads the real design_ideas/ JSON (4 champions, 12 elites) so it also doubles
 * as an end-to-end check that ability/unit ids round-trip correctly through
 * Identifiers.normalize.
 */
class FullDraftWebIntegrationTest {

    @Test
    void draftAndPlacementFlowThroughRealWsMessages_reachesAFullyPopulatedMatchState() throws Exception {
        ChannelHub hub = new ChannelHub();
        RecordingChannel p1Channel = new RecordingChannel();
        RecordingChannel p2Channel = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, p1Channel);
        hub.register(Team.PLAYER_TWO, p2Channel);

        UnitIdRegistry ids = new UnitIdRegistry();
        VfxCollector vfx = new VfxCollector(ids);
        WebInputHandler input = new WebInputHandler(hub, ids);
        GameStateSnapshotMapper mapper = new GameStateSnapshotMapper(ids);
        AtomicReference<GameState> finishedState = new AtomicReference<>();
        WebRenderer renderer = new WebRenderer(hub, mapper, vfx, finishedState::set);

        Thread gameThread = new Thread(() -> {
            Game game = Game.newFullDraftMatch(input, renderer);
            game.getState().getEventBus().addGlobalListener(vfx);
            game.start();
        }, "test-match-thread");
        gameThread.setDaemon(true);
        gameThread.start();

        Map<Team, RecordingChannel> channels = new EnumMap<>(Team.class);
        channels.put(Team.PLAYER_ONE, p1Channel);
        channels.put(Team.PLAYER_TWO, p2Channel);
        Map<Team, Integer> cursor = new EnumMap<>(Team.class);
        cursor.put(Team.PLAYER_ONE, 0);
        cursor.put(Team.PLAYER_TWO, 0);
        Map<Team, String> lastDraftDefinitionId = new HashMap<>();
        AtomicReference<String> lastStateJson = new AtomicReference<>();
        AtomicReference<Boolean> reachedFirstActionPrompt = new AtomicReference<>(false);

        long deadline = System.currentTimeMillis() + 10_000;
        while (!reachedFirstActionPrompt.get() && System.currentTimeMillis() < deadline) {
            for (Team team : Team.values()) {
                RecordingChannel channel = channels.get(team);
                var sent = channel.getSent();
                int i = cursor.get(team);
                while (i < sent.size()) {
                    JsonObject msg = JsonSupport.parse(sent.get(i)).getAsJsonObject();
                    i++;
                    String type = msg.get("type").getAsString();
                    switch (type) {
                        case "draft_round" -> {
                            JsonArray yourOptions = msg.getAsJsonObject("payload").getAsJsonArray("yourOptions");
                            String definitionId = yourOptions.get(0).getAsJsonObject().get("definitionId").getAsString();
                            lastDraftDefinitionId.put(team, definitionId);
                        }
                        case "state" -> lastStateJson.set(sent.get(sent.size() - 1));
                        case "prompt" -> {
                            JsonObject payload = msg.getAsJsonObject("payload");
                            String kind = payload.get("kind").getAsString();
                            switch (kind) {
                                case "pick" -> {
                                    JsonObject pick = new JsonObject();
                                    pick.addProperty("type", "pick");
                                    pick.addProperty("definitionId", lastDraftDefinitionId.get(team));
                                    input.offer(team, pick);
                                }
                                case "placement" -> {
                                    JsonArray candidates = payload.getAsJsonArray("candidates");
                                    JsonObject firstCandidate = candidates.get(0).getAsJsonObject();
                                    JsonObject placement = new JsonObject();
                                    placement.addProperty("type", "placement");
                                    placement.addProperty("q", firstCandidate.get("q").getAsInt());
                                    placement.addProperty("r", firstCandidate.get("r").getAsInt());
                                    input.offer(team, placement);
                                }
                                case "action" -> reachedFirstActionPrompt.set(true);
                                default -> throw new AssertionError("Unexpected prompt kind: " + kind);
                            }
                        }
                        default -> { /* vfx, message - not needed for this driver */ }
                    }
                }
                cursor.put(team, i);
            }
            if (!reachedFirstActionPrompt.get()) {
                Thread.sleep(5);
            }
        }

        assertTrue(reachedFirstActionPrompt.get(), "Never reached the match loop's first action prompt within the deadline");
        assertTrue(lastStateJson.get() != null, "Never received a state push");

        JsonElement parsed = JsonSupport.parse(lastStateJson.get()).getAsJsonObject().get("payload");
        GameStateSnapshot snapshot = JsonSupport.GSON.fromJson(parsed, GameStateSnapshot.class);

        assertEquals(28, snapshot.units().size(), "1 champion + 3 elites + 10 basics per side = 28 units total");
        assertEquals(8, snapshot.mapRadius());
        assertFalse(snapshot.gameOver());
        assertEquals("PLAYER_ONE", snapshot.currentTeam());
        assertEquals(14, snapshot.units().stream().filter(u -> u.team().equals("PLAYER_ONE")).count());
        assertEquals(14, snapshot.units().stream().filter(u -> u.team().equals("PLAYER_TWO")).count());
        // Every unit should have landed on a distinct tile (PlacementFlow's own contract).
        long distinctTiles = snapshot.units().stream().map(u -> u.q() + "," + u.r()).distinct().count();
        assertEquals(28, distinctTiles);
    }
}
