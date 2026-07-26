package com.walnutt.web;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.walnutt.web.match.MatchService;

/** matchId -> GameSession, one per in-progress match, lazily created on first WS connect. */
public final class GameSessionManager {
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final MatchService matchService;

    public GameSessionManager(MatchService matchService) {
        this.matchService = matchService;
    }

    /** Creates (and starts) the session on first access; a match needs both players known in the DB. */
    public GameSession getOrCreate(String matchId) {
        return sessions.computeIfAbsent(matchId, id -> {
            MatchService.MatchParticipants participants = matchService.getParticipants(id)
                .orElseThrow(() -> new ApiException(409, "match doesn't have two players yet"));
            GameSession session = new GameSession(id, participants.playerOneId(), participants.playerTwoId(), matchService);
            session.start();
            return session;
        });
    }

    public Optional<GameSession> get(String matchId) {
        return Optional.ofNullable(sessions.get(matchId));
    }
}
