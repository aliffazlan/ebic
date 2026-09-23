package com.walnutt.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.walnutt.game.Team;

class ChannelHubTest {

    @Test
    void broadcastReachesBothTeamsAndSpectators() {
        ChannelHub hub = new ChannelHub();
        RecordingChannel p1 = new RecordingChannel();
        RecordingChannel p2 = new RecordingChannel();
        RecordingChannel spectator = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, p1);
        hub.register(Team.PLAYER_TWO, p2);
        hub.registerSpectator(spectator);

        hub.broadcast("{\"type\":\"message\"}");

        assertTrue(p1.getSent().contains("{\"type\":\"message\"}"));
        assertTrue(p2.getSent().contains("{\"type\":\"message\"}"));
        assertTrue(spectator.getSent().contains("{\"type\":\"message\"}"));
    }

    @Test
    void registeringASpectatorReplaysTheLastKnownState() {
        ChannelHub hub = new ChannelHub();
        hub.cacheState("{\"type\":\"state\",\"round\":1}");

        RecordingChannel spectator = new RecordingChannel();
        hub.registerSpectator(spectator);

        assertTrue(spectator.getSent().contains("{\"type\":\"state\",\"round\":1}"));
    }

    @Test
    void spectatorPresenceNeverAffectsIsConnected() {
        ChannelHub hub = new ChannelHub();
        RecordingChannel spectator = new RecordingChannel();

        hub.registerSpectator(spectator);
        assertFalse(hub.isConnected(Team.PLAYER_ONE));
        assertFalse(hub.isConnected(Team.PLAYER_TWO));

        hub.unregisterSpectator(spectator);
        assertFalse(hub.isConnected(Team.PLAYER_ONE));
    }

    @Test
    void unregisterReturnsWhetherItActuallyRemovedTheCurrentChannel() {
        ChannelHub hub = new ChannelHub();
        RecordingChannel first = new RecordingChannel();
        RecordingChannel second = new RecordingChannel();

        hub.register(Team.PLAYER_ONE, first);
        hub.register(Team.PLAYER_ONE, second); // a reconnect replaces the mapping

        // A stale close of the replaced channel must not report a real removal.
        assertFalse(hub.unregister(Team.PLAYER_ONE, first));
        assertTrue(hub.isConnected(Team.PLAYER_ONE));

        // Closing the actually-current channel does report a real removal.
        assertTrue(hub.unregister(Team.PLAYER_ONE, second));
        assertFalse(hub.isConnected(Team.PLAYER_ONE));
    }

    @Test
    void spectatorReplaysCombatLogHistoryInOrderBeforeTheCurrentState() {
        ChannelHub hub = new ChannelHub();
        hub.recordCombatLogBatch("{\"type\":\"combat_log_batch\",\"tick\":1}");
        hub.recordCombatLogBatch("{\"type\":\"combat_log_batch\",\"tick\":2}");
        hub.cacheState("{\"type\":\"state\",\"round\":3}");

        RecordingChannel spectator = new RecordingChannel();
        hub.registerSpectator(spectator);

        assertEquals(
            java.util.List.of(
                "{\"type\":\"combat_log_batch\",\"tick\":1}",
                "{\"type\":\"combat_log_batch\",\"tick\":2}",
                "{\"type\":\"state\",\"round\":3}"),
            spectator.getSent());
    }

    @Test
    void combatLogHistoryIsNotBroadcastToTeamChannels() {
        ChannelHub hub = new ChannelHub();
        RecordingChannel p1 = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, p1);

        hub.recordCombatLogBatch("{\"type\":\"combat_log_batch\",\"tick\":1}");

        assertTrue(p1.getSent().isEmpty(), "recording history must not itself send anything to live players");
    }

    @Test
    void replayCombatLogToSendsHistoryInOrderToAnyChannel() {
        ChannelHub hub = new ChannelHub();
        hub.recordCombatLogBatch("{\"type\":\"combat_log_batch\",\"tick\":1}");
        hub.recordCombatLogBatch("{\"type\":\"combat_log_batch\",\"tick\":2}");
        hub.cacheState("{\"type\":\"state\",\"round\":3}");

        // Mirrors GameSession.registerChannel's reconnect path: history replayed explicitly
        // before the channel is registered, so it lands before register()'s own state send.
        RecordingChannel reconnecting = new RecordingChannel();
        hub.replayCombatLogTo(reconnecting);
        hub.register(Team.PLAYER_ONE, reconnecting);

        assertEquals(
            java.util.List.of(
                "{\"type\":\"combat_log_batch\",\"tick\":1}",
                "{\"type\":\"combat_log_batch\",\"tick\":2}",
                "{\"type\":\"state\",\"round\":3}"),
            reconnecting.getSent());
    }

    @Test
    void clearDraftAndPlacementCachesStopsThemFromBeingReplayed() {
        ChannelHub hub = new ChannelHub();
        hub.cacheDraftRound(Team.PLAYER_ONE, "{\"type\":\"draft_round\"}");
        hub.cachePlacementState(Team.PLAYER_ONE, "{\"type\":\"placement_state\"}");
        hub.cacheState("{\"type\":\"state\",\"round\":5}");

        hub.clearDraftAndPlacementCaches();

        RecordingChannel p1 = new RecordingChannel();
        hub.register(Team.PLAYER_ONE, p1);

        assertEquals(java.util.List.of("{\"type\":\"state\",\"round\":5}"), p1.getSent(),
            "a stale draft_round/placement_state must never be replayed once combat has started");
    }

    @Test
    void unregisterSpectatorStopsFurtherBroadcasts() {
        ChannelHub hub = new ChannelHub();
        RecordingChannel spectator = new RecordingChannel();
        hub.registerSpectator(spectator);
        hub.unregisterSpectator(spectator);

        hub.broadcast("{\"type\":\"message\"}");

        assertTrue(spectator.getSent().isEmpty());
    }

    @Test
    void aSandboxSocketHoldsBothSeatsButGetsEachBroadcastOnce() {
        ChannelHub hub = new ChannelHub();
        hub.cachePrompt(Team.PLAYER_ONE, "{\"type\":\"prompt\",\"team\":\"PLAYER_ONE\"}");
        hub.cachePrompt(Team.PLAYER_TWO, "{\"type\":\"prompt\",\"team\":\"PLAYER_TWO\"}");
        RecordingChannel sandbox = new RecordingChannel();

        hub.registerSandbox(sandbox);
        assertEquals(2, sandbox.getSent().size(), "both seats' outstanding prompts are replayed");
        assertTrue(hub.isConnected(Team.PLAYER_ONE));
        assertTrue(hub.isConnected(Team.PLAYER_TWO));

        hub.broadcast("{\"type\":\"message\"}");
        assertEquals(1, sandbox.getSent().stream().filter("{\"type\":\"message\"}"::equals).count());
    }
}
