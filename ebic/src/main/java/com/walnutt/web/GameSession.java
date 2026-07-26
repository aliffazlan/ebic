package com.walnutt.web;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import com.google.gson.JsonObject;

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

    private volatile GameState state;
    private Thread thread;

    public GameSession(String matchId, long playerOneUserId, long playerTwoUserId, MatchService matchService) {
        this.matchId = matchId;
        this.playerOneUserId = playerOneUserId;
        this.playerTwoUserId = playerTwoUserId;
        this.matchService = matchService;
        this.renderer = new WebRenderer(hub, new GameStateSnapshotMapper(ids), vfx, this::onGameOver);
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
            Game game = Game.newConcurrentFullDraftMatch(input, input, renderer);
            this.state = game.getState();
            state.getEventBus().addGlobalListener(vfx);
            matchService.setStatus(matchId, MatchService.Status.IN_PROGRESS);
            game.start();
        } catch (Exception e) {
            LOG.log(Level.ERROR, "Match " + matchId + " aborted due to an internal error", e);
            hub.broadcast(JsonSupport.messageEnvelope("The match hit an internal error and could not continue."));
            try {
                matchService.finishMatch(matchId, null);
            } catch (RuntimeException ignored) {
                // best-effort - the match is already broken, don't compound it with a second failure
            }
        }
    }

    private void onGameOver(GameState finalState) {
        Player winner = finalState.getWinner();
        Long winnerUserId = winner == null ? null : (winner.getTeam() == Team.PLAYER_ONE ? playerOneUserId : playerTwoUserId);
        matchService.finishMatch(matchId, winnerUserId);
    }

    public void registerChannel(Team team, ClientChannel channel) {
        hub.register(team, channel);
    }

    public void unregisterChannel(Team team, ClientChannel channel) {
        hub.unregister(team, channel);
    }

    public void handleMessage(Team team, JsonObject message) {
        input.offer(team, message);
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
