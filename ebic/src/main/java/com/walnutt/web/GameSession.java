package com.walnutt.web;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;

import com.walnutt.ai.BotConfig;
import com.walnutt.ai.BotHandler;
import com.walnutt.ai.TeamRoutingHandler;
import com.walnutt.game.Game;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.web.match.MatchService;

/**
 * One running match = one GameState + one dedicated thread + its own
 * WebInputHandler/WebRenderer, exactly as prescribed: GameState/EventBus aren't
 * thread-safe, so only this session's own thread ever touches them. WS handler
 * threads (Javalin) only register channels and offer() incoming messages onto
 * WebInputHandler's queues - they never read/write GameState directly.
 */
public final class GameSession {
    private static final Logger LOG = System.getLogger(GameSession.class.getName());

    private final String matchId;
    private final long playerOneUserId;
    private final long playerTwoUserId;
    private final MatchService matchService;

    private final ChannelHub hub = new ChannelHub();
    private final UnitIdRegistry ids = new UnitIdRegistry();
    private final VfxCollector vfx = new VfxCollector(ids);
    private final WebInputHandler input = new WebInputHandler(hub, ids);
    private final WebRenderer renderer;

    /** Null for an ordinary two-human match; otherwise the seat the computer plays. */
    private final Team botTeam;
    private final BotHandler bot;
    /** Each human seat's favourite hero, guaranteed one of its two options in the matching draft round. */
    private final Map<Team, String> favourites;
    /** For the disconnect/reconnect/spectator-join broadcast text - not used by game logic. */
    private final String playerOneUsername;
    private final String playerTwoUsername;
    /** Tracks which teams have connected at least once, to tell a first connect (silent) from a
     * reconnect (broadcasts a message) in registerChannel(). */
    private final Set<Team> everConnected = ConcurrentHashMap.newKeySet();
    /** Evicts this session from GameSessionManager's map once the match ends (success or error). */
    private final Runnable onCleanup;
    /** Shared with all sessions via GameSessionManager; null in the no-scheduler test constructors. */
    private final ScheduledExecutorService scheduler;
    /** How long both seats can sit disconnected before the match is torn down as abandoned. */
    private static final long ABANDON_GRACE_SECONDS = 60;
    private volatile ScheduledFuture<?> abandonCheck;

    /** One player holding both seats on an empty board - see Game.newSandboxMatch. */
    private final boolean sandbox;
    /** Set by shutdown() so the interrupt it causes isn't reported as an internal error. */
    private volatile boolean stopping;

    private volatile GameState state;
    private Thread thread;

    public GameSession(String matchId, long playerOneUserId, long playerTwoUserId, MatchService matchService) {
        this(matchId, playerOneUserId, playerTwoUserId, matchService, null, null, Map.of(),
            "Player One", "Player Two", () -> { }, null);
    }

    public GameSession(String matchId, long playerOneUserId, long playerTwoUserId, MatchService matchService,
                        Team botTeam, BotConfig botConfig) {
        this(matchId, playerOneUserId, playerTwoUserId, matchService, botTeam, botConfig, Map.of(),
            "Player One", "Player Two", () -> { }, null);
    }

    public GameSession(String matchId, long playerOneUserId, long playerTwoUserId, MatchService matchService,
                        Team botTeam, BotConfig botConfig, Map<Team, String> favourites,
                        String playerOneUsername, String playerTwoUsername, Runnable onCleanup,
                        ScheduledExecutorService scheduler) {
        this(matchId, playerOneUserId, playerTwoUserId, matchService, botTeam, botConfig, favourites,
            playerOneUsername, playerTwoUsername, onCleanup, scheduler, false);
    }

    public GameSession(String matchId, long playerOneUserId, long playerTwoUserId, MatchService matchService,
                        Team botTeam, BotConfig botConfig, Map<Team, String> favourites,
                        String playerOneUsername, String playerTwoUsername, Runnable onCleanup,
                        ScheduledExecutorService scheduler, boolean sandbox) {
        this.sandbox = sandbox;
        this.matchId = matchId;
        this.playerOneUserId = playerOneUserId;
        this.playerTwoUserId = playerTwoUserId;
        this.matchService = matchService;
        this.botTeam = botTeam;
        this.bot = botTeam == null ? null : new BotHandler(botConfig);
        this.favourites = favourites == null ? Map.of() : Map.copyOf(favourites);
        this.playerOneUsername = playerOneUsername == null ? "Player One" : playerOneUsername;
        this.playerTwoUsername = playerTwoUsername == null ? "Player Two" : playerTwoUsername;
        this.onCleanup = onCleanup == null ? () -> { } : onCleanup;
        this.scheduler = scheduler;
        this.renderer = new WebRenderer(hub, new GameStateSnapshotMapper(ids), vfx, this::onGameOver);
    }

    /**
     * Both setup and turn input for this match. For a bot match the two seats are split
     * by team, so the human's prompts still go over the socket while the bot's are
     * answered in-process - see TeamRoutingHandler, which also explains why the
     * attribute-pair path must not be left to WebInputHandler's own override.
     */
    private TeamRoutingHandler routedHandler() {
        return botTeam == Team.PLAYER_ONE
            ? TeamRoutingHandler.of(bot, input)
            : TeamRoutingHandler.of(input, bot);
    }

    public synchronized void start() {
        if (thread != null) {
            return;
        }
        thread = new Thread(this::runMatch, "match-" + matchId);
        thread.setDaemon(true);
        thread.start();
    }

    private void runMatch() {
        try {
            Game game = newMatch();
            this.state = game.getState();
            renderer.setAbilityDefinitions(state.getAbilityDefinitions());
            state.getEventBus().addGlobalListener(vfx);
            matchService.setStatus(matchId, MatchService.Status.IN_PROGRESS);
            // Draft+placement are permanently over for every team the moment combat starts -
            // stop caching (and thus wrongly replaying) their last-sent payloads to anyone who
            // reconnects from here on. See ChannelHub.clearDraftAndPlacementCaches.
            hub.clearDraftAndPlacementCaches();
            game.start();
        } catch (Exception e) {
            if (stopping) {
                return; // shutdown() interrupted the blocked input wait - a deliberate end, not a crash
            }
            LOG.log(Level.ERROR, "Match " + matchId + " aborted due to an internal error", e);
            hub.broadcast(JsonSupport.messageEnvelope("The match hit an internal error and could not continue."));
            try {
                matchService.finishMatch(matchId, null);
            } catch (RuntimeException ignored) {
                // best-effort - the match is already broken, don't compound it with a second failure
            }
            cancelAbandonCheck();
            onCleanup.run();
        }
    }

    private Game newMatch() {
        if (sandbox) {
            return Game.newSandboxMatch(input, renderer);
        }
        if (botTeam == null) {
            return Game.newConcurrentFullDraftMatch(input, input, renderer, favourites);
        }
        TeamRoutingHandler router = routedHandler();
        return Game.newConcurrentFullDraftMatch(router, router, renderer, favourites);
    }

    private void onGameOver(GameState finalState) {
        Player winner = finalState.getWinner();
        Long winnerUserId = winner == null ? null : (winner.getTeam() == Team.PLAYER_ONE ? playerOneUserId : playerTwoUserId);
        matchService.finishMatch(matchId, winnerUserId);
        cancelAbandonCheck();
        onCleanup.run();
    }

    public void registerChannel(Team team, ClientChannel channel) {
        if (sandbox) {
            registerSandboxChannel(channel);
            return;
        }
        boolean isReconnect = !everConnected.add(team);
        // Replayed before register()'s own sends so the frontend's round-rollover detection
        // (GameStateStore.commitCombatLog) starts fresh against the history, the same order
        // registerSpectator already uses - see ChannelHub.replayCombatLogTo.
        if (isReconnect) {
            hub.replayCombatLogTo(channel);
        }
        hub.register(team, channel);
        if (isReconnect && matchService.getRawStatus(matchId) == MatchService.Status.IN_PROGRESS) {
            hub.broadcast(JsonSupport.messageEnvelope(usernameFor(team) + " reconnected to the game."));
        }
        onPresenceChanged();
    }

    /** No join/leave chatter in a sandbox - there is nobody else to tell. */
    private void registerSandboxChannel(ClientChannel channel) {
        if (!everConnected.add(Team.PLAYER_ONE)) {
            hub.replayCombatLogTo(channel);
        }
        hub.registerSandbox(channel);
        onPresenceChanged();
    }

    public void unregisterChannel(Team team, ClientChannel channel) {
        if (sandbox) {
            hub.unregister(Team.PLAYER_ONE, channel);
            hub.unregister(Team.PLAYER_TWO, channel);
            onPresenceChanged();
            return;
        }
        boolean actuallyDisconnected = hub.unregister(team, channel);
        if (actuallyDisconnected && matchService.getRawStatus(matchId) == MatchService.Status.IN_PROGRESS) {
            hub.broadcast(JsonSupport.messageEnvelope(usernameFor(team) + " disconnected from the game."));
        }
        onPresenceChanged();
    }

    private String usernameFor(Team team) {
        return team == Team.PLAYER_ONE ? playerOneUsername : playerTwoUsername;
    }

    /** A read-only viewer joining never affects presence/abandon logic - only real seats do. */
    public void registerSpectatorChannel(ClientChannel channel, String username) {
        hub.registerSpectator(channel);
        hub.broadcast(JsonSupport.messageEnvelope(username + " joined the game as Spectator."));
    }

    public void unregisterSpectatorChannel(ClientChannel channel) {
        hub.unregisterSpectator(channel);
    }

    /**
     * A bot seat never holds a channel, so it's never counted as "gone" - a bot match's
     * only relevant seat is its human. Runs after every register/unregister to decide
     * whether the match now looks abandoned (schedule a grace-period check) or has
     * someone back (cancel any pending one).
     */
    private void onPresenceChanged() {
        if (everyoneRelevantIsDisconnected()) {
            scheduleAbandonCheckIfNeeded();
        } else {
            cancelAbandonCheck();
        }
    }

    private boolean everyoneRelevantIsDisconnected() {
        boolean playerOneGone = botTeam == Team.PLAYER_ONE || !hub.isConnected(Team.PLAYER_ONE);
        boolean playerTwoGone = botTeam == Team.PLAYER_TWO || !hub.isConnected(Team.PLAYER_TWO);
        return playerOneGone && playerTwoGone;
    }

    private synchronized void scheduleAbandonCheckIfNeeded() {
        if (scheduler == null || abandonCheck != null) {
            return;
        }
        abandonCheck = scheduler.schedule(this::abandonIfStillEmpty, ABANDON_GRACE_SECONDS, TimeUnit.SECONDS);
    }

    private synchronized void cancelAbandonCheck() {
        if (abandonCheck != null) {
            abandonCheck.cancel(false);
            abandonCheck = null;
        }
    }

    /**
     * Fires once the grace period elapses with nobody relevant still connected. Re-checks
     * presence (a reconnect may have raced the timer) and the match's real status (a
     * legitimate conclusion right before this fired must never be overwritten with a
     * null-winner FINISHED) before actually tearing anything down.
     */
    private void abandonIfStillEmpty() {
        synchronized (this) {
            abandonCheck = null;
        }
        if (!everyoneRelevantIsDisconnected()) {
            return;
        }
        if (matchService.getRawStatus(matchId) == MatchService.Status.FINISHED) {
            return;
        }
        LOG.log(Level.INFO, "Match " + matchId + " destroyed - both players disconnected and never reconnected");
        // Sent before finishing/evicting the session so any still-connected spectator (spectators
        // never count toward the disconnect check above, so they're typically the only ones left)
        // learns why the board just froze, rather than being left to wonder.
        hub.broadcast(JsonSupport.messageEnvelope("This match was abandoned and has been destroyed."));
        try {
            matchService.finishMatch(matchId, null);
        } catch (RuntimeException ignored) {
            // best-effort - nothing left to fix up if this itself fails
        }
        onCleanup.run();
    }

    public void handleMessage(Team team, JsonObject message) {
        input.offer(sandbox ? sandboxSeatFor(message) : team, message);
    }

    /**
     * Which seat a sandbox message is answering. An attribute or choice pick names its team -
     * mid-encounter both seats are waiting at once. Everything else is an action, and only the
     * side whose turn it is is ever asked for one; routing it anywhere else would leave it
     * queued up to fire, stale, at the start of the other side's next turn.
     */
    private Team sandboxSeatFor(JsonObject message) {
        String type = JsonSupport.optString(message, "type");
        if ("attribute".equals(type) || "choice".equals(type)) {
            String named = JsonSupport.optString(message, "team");
            for (Team team : Team.values()) {
                if (team.name().equals(named)) {
                    return team;
                }
            }
        }
        GameState current = state;
        return current == null ? Team.PLAYER_ONE : current.getCurrentPlayer().getTeam();
    }

    public boolean isSandbox() {
        return sandbox;
    }

    /**
     * Ends a sandbox on its player's say-so. The match thread is parked waiting for input,
     * so it has to be interrupted out of that wait - flagged first so runMatch doesn't
     * mistake the interrupt for a crash.
     */
    public void shutdown() {
        stopping = true;
        cancelAbandonCheck();
        try {
            matchService.finishMatch(matchId, null);
        } catch (RuntimeException ignored) {
            // best-effort - the session is going away regardless
        }
        synchronized (this) {
            if (thread != null) {
                thread.interrupt();
            }
        }
        onCleanup.run();
    }

    /** Whether a seat currently has a live channel - used by the "rejoin match" list to tell
     * "still connected somewhere else" apart from "safe to rejoin". */
    public boolean isTeamConnected(Team team) {
        return hub.isConnected(team);
    }

    public Team teamFor(long userId) {
        if (userId == playerOneUserId) {
            return Team.PLAYER_ONE;
        }
        if (userId == playerTwoUserId) {
            return Team.PLAYER_TWO;
        }
        return null;
    }

    public String getMatchId() {
        return matchId;
    }
}
