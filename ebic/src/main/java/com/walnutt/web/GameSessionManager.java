package com.walnutt.web;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.walnutt.ai.BotLevel;
import com.walnutt.game.Team;
import com.walnutt.web.auth.AuthService;
import com.walnutt.web.match.MatchService;

/** matchId -> GameSession, one per in-progress match, lazily created on first WS connect. */
public final class GameSessionManager {
    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();
    private final MatchService matchService;
    private final AuthService auth;
    private final UnitCatalog catalog;

    public GameSessionManager(MatchService matchService, AuthService auth, UnitCatalog catalog) {
        this.matchService = matchService;
        this.auth = auth;
        this.catalog = catalog;
    }

    /**
     * Creates (and starts) the session on first access; a match needs both players known
     * in the DB. A bot match satisfies that the same way a human one does - its second
     * seat is the reserved bot account - so the only difference here is noticing that the
     * seat is the bot's and handing the session a difficulty to play at.
     */
    public GameSession getOrCreate(String matchId) {
        return sessions.computeIfAbsent(matchId, id -> {
            // A finished match's session is evicted once the game ends (see remove()); a
            // stray reconnect after that must not resurrect it, since the row still exists
            // in the DB (kept for history) even though there's no game left to rejoin.
            if (matchService.getRawStatus(id) == MatchService.Status.FINISHED) {
                throw new ApiException(409, "this match has already finished");
            }

            MatchService.MatchParticipants participants = matchService.getParticipants(id)
                .orElseThrow(() -> new ApiException(409, "match doesn't have two players yet"));

            Team botTeam = null;
            BotLevel level = null;
            if (matchService.isBot(participants.playerTwoId())) {
                botTeam = Team.PLAYER_TWO;
            } else if (matchService.isBot(participants.playerOneId())) {
                botTeam = Team.PLAYER_ONE;
            }
            if (botTeam != null) {
                level = storedLevel(id);
            }

            GameSession session = new GameSession(id, participants.playerOneId(), participants.playerTwoId(),
                matchService, botTeam, level == null ? null : level.config(), favourites(participants, botTeam),
                () -> sessions.remove(id));
            session.start();
            return session;
        });
    }

    /** Drops a finished (or crashed) match's session so it doesn't stay resident forever. */
    public void remove(String matchId) {
        sessions.remove(matchId);
    }

    /**
     * Each seat's favourite hero, keyed by team. This is the only layer that can build it:
     * nothing under game/ knows about users at all, and the user-to-team mapping is the
     * positional player_one/player_two one GameSession.teamFor already relies on.
     *
     * A bot seat is skipped outright rather than trusted to have a null column - the
     * computer has no favourites, and giving it one would quietly hand it a guaranteed pick.
     * A stored id that is no longer draftable (a hero pulled from the pool since it was
     * chosen) is dropped here rather than failing the match.
     */
    private Map<Team, String> favourites(MatchService.MatchParticipants participants, Team botTeam) {
        Map<Team, String> favourites = new HashMap<>();
        putFavourite(favourites, Team.PLAYER_ONE, participants.playerOneId(), botTeam);
        putFavourite(favourites, Team.PLAYER_TWO, participants.playerTwoId(), botTeam);
        return favourites;
    }

    private void putFavourite(Map<Team, String> favourites, Team team, long userId, Team botTeam) {
        if (team == botTeam) {
            return;
        }
        String favourite = auth.getFavouriteUnit(userId);
        if (favourite != null && catalog.isDraftable(favourite)) {
            favourites.put(team, favourite);
        }
    }

    /**
     * The level recorded on the match, defaulting rather than throwing if it no longer
     * parses. The value passed API validation when the match was created, so an
     * unrecognised one here means a hand-edited row or a level that has since been
     * removed - neither is worth making a match permanently unplayable over, and the
     * exception would escape the WS connect handler, which only catches ApiException.
     */
    private BotLevel storedLevel(String matchId) {
        try {
            return BotLevel.parse(matchService.getBotLevel(matchId));
        } catch (IllegalArgumentException e) {
            return BotLevel.STANDARD;
        }
    }

    public Optional<GameSession> get(String matchId) {
        return Optional.ofNullable(sessions.get(matchId));
    }
}
