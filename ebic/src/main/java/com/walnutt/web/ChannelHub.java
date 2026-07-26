package com.walnutt.web;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.walnutt.game.Team;

/**
 * Per-match routing table from Team -> the currently-connected ClientChannel for
 * that team (if any), plus a small "last sent" cache per message kind so a
 * reconnecting socket (new tab, refresh) gets caught up immediately instead of
 * waiting for the next server-initiated push - see API_CONTRACT.md's WS
 * reconnect note. One instance per GameSession/match; safe to call from both the
 * dedicated game thread (sends) and Javalin's WS threads (register/unregister).
 */
public final class ChannelHub {
    private final Map<Team, ClientChannel> channels = new ConcurrentHashMap<>();
    private final Map<Team, String> lastDraftRoundJson = new ConcurrentHashMap<>();
    private final Map<Team, String> lastPromptJson = new ConcurrentHashMap<>();
    private volatile String lastStateJson;

    /** Registers (or replaces, on reconnect) the channel for a team and replays cached state to it. */
    public void register(Team team, ClientChannel channel) {
        channels.put(team, channel);
        String draftJson = lastDraftRoundJson.get(team);
        if (draftJson != null) {
            channel.send(draftJson);
        }
        if (lastStateJson != null) {
            channel.send(lastStateJson);
        }
        String promptJson = lastPromptJson.get(team);
        if (promptJson != null) {
            channel.send(promptJson);
        }
    }

    /** Only removes the mapping if it's still the same channel instance (a newer reconnect wins the race). */
    public void unregister(Team team, ClientChannel channel) {
        channels.remove(team, channel);
    }

    public void sendTo(Team team, String json) {
        ClientChannel channel = channels.get(team);
        if (channel != null && channel.isOpen()) {
            channel.send(json);
        }
    }

    public void broadcast(String json) {
        sendTo(Team.PLAYER_ONE, json);
        sendTo(Team.PLAYER_TWO, json);
    }

    public void cacheState(String json) {
        this.lastStateJson = json;
    }

    public void cacheDraftRound(Team team, String json) {
        lastDraftRoundJson.put(team, json);
    }

    public void cachePrompt(Team team, String json) {
        lastPromptJson.put(team, json);
    }

    public void clearPrompt(Team team) {
        lastPromptJson.remove(team);
    }

    public boolean isConnected(Team team) {
        ClientChannel channel = channels.get(team);
        return channel != null && channel.isOpen();
    }
}
