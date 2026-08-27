package com.walnutt.web;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.walnutt.ai.BotLevel;
import com.walnutt.game.Team;
import com.walnutt.web.match.MatchService;

/** matchId -> GameSession, one per in-progress match, lazily created on first WS connect. */
public final class GameSessionManager {
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final MatchService matchService;

    public GameSessionManager(MatchService matchService) {
        this.matchService = matchService;
    }

    /**
     * Creates (and starts) the session on first access; a match needs both players known
     * in the DB. A bot match satisfies that the same way a human one does - its second
     * seat is the reserved bot account - so the only difference here is noticing that the
     * seat is the bot's and handing the session a difficulty to play at.
     */
    public GameSession getOrCreate(String matchId) {
        return sessions.computeIfAbsent(matchId, id -> {
            MatchService.MatchParticipants participants = matchService.getParticipants(id)
                .orElseThrow(() -> new ApiException(409, "match doesn't have two players yet"));

            Team botTeam = null;
            BotLevel level = null;
            if (matchService.isBot(participants.playerTwoId())) {
                botTeam = Team.PLAYER_TWO;
                level = BotLevel.parse(matchService.getBotLevel(id));
            } else if (matchService.isBot(participants.playerOneId())) {
                botTeam = Team.PLAYER_ONE;
                level = BotLevel.parse(matchService.getBotLevel(id));
            }

            GameSession session = new GameSession(id, participants.playerOneId(), participants.playerTwoId(),
                matchService, botTeam, level == null ? null : level.config());
            session.start();
            return session;
        });
    }

    public Optional<GameSession> get(String matchId) {
        return Optional.ofNullable(sessions.get(matchId));
    }
}
