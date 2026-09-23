package com.walnutt.web;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
    private final Map<Team, String> lastPlacementStateJson = new ConcurrentHashMap<>();
    private final Map<Team, String> lastPromptJson = new ConcurrentHashMap<>();
    /** Read-only viewers, tracked separately from `channels` so they never affect isConnected(Team). */
    private final Set<ClientChannel> spectatorChannels = ConcurrentHashMap.newKeySet();
    /** One "combat_log_batch" envelope per render tick since the match started, in order - see
     * recordCombatLogBatch(). Writes only ever come from the single per-match game thread;
     * reads happen from Javalin WS threads on spectator connect. */
    private final List<String> combatLogHistory = new CopyOnWriteArrayList<>();
    private volatile String lastStateJson;

    /** Registers (or replaces, on reconnect) the channel for a team and replays cached state to it. */
    public void register(Team team, ClientChannel channel) {
        channels.put(team, channel);
        String draftJson = lastDraftRoundJson.get(team);
        if (draftJson != null) {
            channel.send(draftJson);
        }
        String placementJson = lastPlacementStateJson.get(team);
        if (placementJson != null) {
            channel.send(placementJson);
        }
        if (lastStateJson != null) {
            channel.send(lastStateJson);
        }
        String promptJson = lastPromptJson.get(team);
        if (promptJson != null) {
            channel.send(promptJson);
        }
    }

    /**
     * The sandbox's single player holds both seats through one socket. Replays the shared
     * state once, then BOTH seats' outstanding prompts - mid-encounter, that is both halves
     * of the attribute chooser.
     */
    public void registerSandbox(ClientChannel channel) {
        channels.put(Team.PLAYER_ONE, channel);
        channels.put(Team.PLAYER_TWO, channel);
        if (lastStateJson != null) {
            channel.send(lastStateJson);
        }
        for (Team team : Team.values()) {
            String promptJson = lastPromptJson.get(team);
            if (promptJson != null) {
                channel.send(promptJson);
            }
        }
    }

    /**
     * Only removes the mapping if it's still the same channel instance (a newer reconnect wins
     * the race). Returns whether it actually removed anything, so a caller can tell a genuine
     * disconnect from a stale close of an already-replaced channel.
     */
    public boolean unregister(Team team, ClientChannel channel) {
        return channels.remove(team, channel);
    }

    public void sendTo(Team team, String json) {
        ClientChannel channel = channels.get(team);
        if (channel != null && channel.isOpen()) {
            channel.send(json);
        }
    }

    /**
     * A sandbox's one socket sits in both seats (see registerSandbox), so the two seats are
     * sent to as a set rather than one after the other - otherwise it would get everything twice.
     */
    public void broadcast(String json) {
        Set<ClientChannel> seated = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        seated.addAll(channels.values());
        for (ClientChannel channel : seated) {
            if (channel.isOpen()) {
                channel.send(json);
            }
        }
        for (ClientChannel channel : spectatorChannels) {
            if (channel.isOpen()) {
                channel.send(json);
            }
        }
    }

    /** Registers a read-only viewer and immediately replays the current board so they aren't
     * stuck staring at nothing until the next server-initiated push. Draft/placement/prompt
     * caches are deliberately not replayed here - spectators only ever connect once a match is
     * IN_PROGRESS, so those per-player, pre-combat payloads never apply to them. */
    public void registerSpectator(ClientChannel channel) {
        spectatorChannels.add(channel);
        // Replays the whole combat log history first so it's already in place by the time the
        // live board (lastStateJson, below) shows up - see recordCombatLogBatch().
        replayCombatLogTo(channel);
        if (lastStateJson != null) {
            channel.send(lastStateJson);
        }
    }

    public void unregisterSpectator(ClientChannel channel) {
        spectatorChannels.remove(channel);
    }

    /** Replays the full combat-log history to one channel, in order - used both for a fresh
     * spectator (registerSpectator, above) and a reconnecting seated player (see GameSession.
     * registerChannel), so either can rebuild an accurate combat log instead of starting empty. */
    public void replayCombatLogTo(ClientChannel channel) {
        for (String batchJson : combatLogHistory) {
            channel.send(batchJson);
        }
    }

    /** Appends one render tick's combat-log envelope to the replay history - see the field's own doc comment. */
    public void recordCombatLogBatch(String json) {
        combatLogHistory.add(json);
    }

    public void cacheState(String json) {
        this.lastStateJson = json;
    }

    public void cacheDraftRound(Team team, String json) {
        lastDraftRoundJson.put(team, json);
    }

    /** Same reconnect-replay rationale as cacheDraftRound - see register(). */
    public void cachePlacementState(Team team, String json) {
        lastPlacementStateJson.put(team, json);
    }

    /** Called once combat begins (draft+placement are permanently over for every team) - the
     * last-sent draft_round/placement_state payloads would otherwise stay cached forever and
     * get wrongly replayed to a player who reconnects mid-combat. See register(). */
    public void clearDraftAndPlacementCaches() {
        lastDraftRoundJson.clear();
        lastPlacementStateJson.clear();
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
