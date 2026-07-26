package com.walnutt.web.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.walnutt.web.ApiException;
import com.walnutt.web.auth.AuthService;
import com.walnutt.web.db.Database;

class MatchServiceTest {
    private Path dbFile;
    private Database db;
    private MatchService matches;
    private long p1UserId;
    private long p2UserId;
    private long p3UserId;

    @BeforeEach
    void setUp() throws IOException {
        dbFile = Files.createTempFile("ebic-test-", ".db");
        db = new Database(dbFile.toString());
        db.migrate();
        matches = new MatchService(db);

        AuthService auth = new AuthService(db);
        p1UserId = auth.register("hostplayer", "password123").user().userId();
        p2UserId = auth.register("guestplayer", "password123").user().userId();
        p3UserId = auth.register("thirdplayer", "password123").user().userId();
    }

    @AfterEach
    void tearDown() throws IOException {
        db.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void createThenJoin_flipsStatusToDrafting() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId);
        assertEquals("WAITING", created.status());
        assertEquals(6, created.joinCode().length());

        MatchService.MatchSummary joined = matches.joinMatch(p2UserId, created.joinCode());
        assertEquals(created.matchId(), joined.matchId());
        assertEquals("DRAFTING", joined.status());
    }

    @Test
    void joinRejectsUnknownCode() {
        ApiException e = assertThrows(ApiException.class, () -> matches.joinMatch(p2UserId, "ZZZZZZ"));
        assertEquals(404, e.getStatus());
    }

    @Test
    void joinRejectsASecondJoinerOnceMatchIsFull() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId);
        matches.joinMatch(p2UserId, created.joinCode());

        ApiException e = assertThrows(ApiException.class, () -> matches.joinMatch(p3UserId, created.joinCode()));
        assertEquals(409, e.getStatus());
    }

    @Test
    void joinRejectsTheCreatorJoiningTheirOwnMatch() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId);
        ApiException e = assertThrows(ApiException.class, () -> matches.joinMatch(p1UserId, created.joinCode()));
        assertEquals(409, e.getStatus());
    }

    @Test
    void getStatus_reportsYourTeamCorrectlyForBothParticipants() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId);
        matches.joinMatch(p2UserId, created.joinCode());

        MatchService.MatchStatusView asHost = matches.getStatus(p1UserId, created.matchId());
        MatchService.MatchStatusView asGuest = matches.getStatus(p2UserId, created.matchId());

        assertEquals("PLAYER_ONE", asHost.yourTeam());
        assertEquals("PLAYER_TWO", asGuest.yourTeam());
        assertEquals("hostplayer", asHost.playerOneName());
        assertEquals("guestplayer", asHost.playerTwoName());
    }

    @Test
    void getStatus_rejectsANonParticipant() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId);
        matches.joinMatch(p2UserId, created.joinCode());

        ApiException e = assertThrows(ApiException.class, () -> matches.getStatus(p3UserId, created.matchId()));
        assertEquals(404, e.getStatus());
    }

    @Test
    void getParticipants_emptyUntilBothPlayersHaveJoined() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId);
        assertTrue(matches.getParticipants(created.matchId()).isEmpty());

        matches.joinMatch(p2UserId, created.joinCode());
        assertTrue(matches.getParticipants(created.matchId()).isPresent());
    }

    @Test
    void finishMatch_setsStatusAndWinner() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId);
        matches.joinMatch(p2UserId, created.joinCode());

        matches.finishMatch(created.matchId(), p1UserId);
        MatchService.MatchStatusView status = matches.getStatus(p1UserId, created.matchId());
        assertEquals("FINISHED", status.status());
        assertEquals("hostplayer", status.winnerName());
    }
}
