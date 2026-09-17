package com.walnutt.web.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void createMatch_defaultsAreRespected() {
        MatchService.MatchSummary privateMatch = matches.createMatch(p1UserId, false);
        assertFalse(privateMatch.isPublic());

        MatchService.MatchSummary publicMatch = matches.createMatch(p1UserId, true);
        assertTrue(publicMatch.isPublic());
    }

    @Test
    void createThenJoin_staysInLobbyUntilOwnerStarts() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
        assertEquals("LOBBY", created.status());
        assertEquals(6, created.joinCode().length());

        MatchService.MatchSummary joined = matches.joinMatch(p2UserId, created.joinCode());
        assertEquals(created.matchId(), joined.matchId());
        assertEquals("LOBBY", joined.status(), "joining seats the player but does not start the game");
    }

    @Test
    void joinRejectsUnknownCode() {
        ApiException e = assertThrows(ApiException.class, () -> matches.joinMatch(p2UserId, "ZZZZZZ"));
        assertEquals(404, e.getStatus());
    }

    @Test
    void joinRejectsASecondJoinerOnceMatchIsFull() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
        matches.joinMatch(p2UserId, created.joinCode());

        ApiException e = assertThrows(ApiException.class, () -> matches.joinMatch(p3UserId, created.joinCode()));
        assertEquals(409, e.getStatus());
        assertEquals("lobby is full", e.getMessage());
    }

    @Test
    void joinRejectsTheCreatorJoiningTheirOwnMatch() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
        ApiException e = assertThrows(ApiException.class, () -> matches.joinMatch(p1UserId, created.joinCode()));
        assertEquals(409, e.getStatus());
    }

    @Test
    void getStatus_reportsYourTeamCorrectlyForBothParticipants() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
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
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
        matches.joinMatch(p2UserId, created.joinCode());

        ApiException e = assertThrows(ApiException.class, () -> matches.getStatus(p3UserId, created.matchId()));
        assertEquals(404, e.getStatus());
    }

    @Test
    void getParticipants_emptyUntilBothPlayersHaveJoined() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
        assertTrue(matches.getParticipants(created.matchId()).isEmpty());

        matches.joinMatch(p2UserId, created.joinCode());
        assertTrue(matches.getParticipants(created.matchId()).isPresent());
    }

    @Test
    void finishMatch_setsStatusAndWinner() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
        matches.joinMatch(p2UserId, created.joinCode());

        matches.finishMatch(created.matchId(), p1UserId);
        MatchService.MatchStatusView status = matches.getStatus(p1UserId, created.matchId());
        assertEquals("FINISHED", status.status());
        assertEquals("hostplayer", status.winnerName());
    }

    @Test
    void startLobby_ownerCanStartOnceBothSeated() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
        matches.joinMatch(p2UserId, created.joinCode());

        MatchService.MatchSummary started = matches.startLobby(p1UserId, created.matchId());
        assertEquals("DRAFTING", started.status());
    }

    @Test
    void startLobby_rejectsNonOwner() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
        matches.joinMatch(p2UserId, created.joinCode());

        ApiException e = assertThrows(ApiException.class, () -> matches.startLobby(p2UserId, created.matchId()));
        assertEquals(403, e.getStatus());
    }

    @Test
    void startLobby_rejectsWhenOnlyOnePlayerSeated() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);

        ApiException e = assertThrows(ApiException.class, () -> matches.startLobby(p1UserId, created.matchId()));
        assertEquals(409, e.getStatus());
    }

    @Test
    void startLobby_rejectsWhenAlreadyStarted() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
        matches.joinMatch(p2UserId, created.joinCode());
        matches.startLobby(p1UserId, created.matchId());

        ApiException e = assertThrows(ApiException.class, () -> matches.startLobby(p1UserId, created.matchId()));
        assertEquals(409, e.getStatus());
    }

    @Test
    void leaveLobby_joinerLeavingFreesTheSeat() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
        matches.joinMatch(p2UserId, created.joinCode());

        matches.leaveLobby(p2UserId, created.matchId());
        assertTrue(matches.getParticipants(created.matchId()).isEmpty());

        // A new joiner can now take the freed seat.
        MatchService.MatchSummary rejoined = matches.joinMatch(p3UserId, created.joinCode());
        assertEquals("LOBBY", rejoined.status());
    }

    @Test
    void leaveLobby_ownerLeavingDeletesTheLobby() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);

        matches.leaveLobby(p1UserId, created.matchId());

        ApiException e = assertThrows(ApiException.class, () -> matches.joinMatch(p2UserId, created.joinCode()));
        assertEquals(404, e.getStatus());
    }

    @Test
    void leaveLobby_rejectsOnceTheGameHasStarted() {
        MatchService.MatchSummary created = matches.createMatch(p1UserId, false);
        matches.joinMatch(p2UserId, created.joinCode());
        matches.startLobby(p1UserId, created.matchId());

        ApiException e = assertThrows(ApiException.class, () -> matches.leaveLobby(p2UserId, created.matchId()));
        assertEquals(409, e.getStatus());
    }

    @Test
    void listPublicLobbies_onlyShowsPublicOpenOrOngoingLobbies() {
        MatchService.MatchSummary publicOpen = matches.createMatch(p1UserId, true);
        matches.createMatch(p1UserId, false); // private - must never appear

        java.util.List<MatchService.PublicLobbySummary> before = matches.listPublicLobbies();
        assertEquals(1, before.size());
        assertEquals(publicOpen.matchId(), before.get(0).matchId());
        assertFalse(before.get(0).full());

        matches.joinMatch(p2UserId, publicOpen.joinCode());
        matches.startLobby(p1UserId, publicOpen.matchId());

        java.util.List<MatchService.PublicLobbySummary> afterStart = matches.listPublicLobbies();
        assertEquals(1, afterStart.size(), "a drafting public match should still be listed, just not joinable");
        assertEquals("DRAFTING", afterStart.get(0).status());

        matches.finishMatch(publicOpen.matchId(), p1UserId);
        assertTrue(matches.listPublicLobbies().isEmpty(), "a finished match must disappear from the browser");
    }
}
