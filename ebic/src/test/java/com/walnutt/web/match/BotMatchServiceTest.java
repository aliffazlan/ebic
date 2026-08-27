package com.walnutt.web.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.walnutt.ai.BotLevel;
import com.walnutt.web.ApiException;
import com.walnutt.web.auth.AuthService;
import com.walnutt.web.db.Database;

class BotMatchServiceTest {
    private Path dbFile;
    private Database db;
    private MatchService matches;
    private long humanUserId;

    @BeforeEach
    void setUp() throws IOException {
        dbFile = Files.createTempFile("ebic-bot-test-", ".db");
        db = new Database(dbFile.toString());
        db.migrate();
        matches = new MatchService(db);
        humanUserId = new AuthService(db).register("humanplayer", "password123").user().userId();
    }

    @AfterEach
    void tearDown() throws IOException {
        db.close();
        Files.deleteIfExists(dbFile);
    }

    /**
     * The bot seat is a real users row rather than a nullable player_two_id, which is what
     * lets every existing read path work untouched - getParticipants in particular refuses
     * to hand out a session until both seats are filled.
     */
    @Test
    void aBotMatchIsImmediatelyPlayableWithTheBotAlreadySeated() {
        MatchService.MatchSummary summary = matches.createBotMatch(humanUserId, BotLevel.STANDARD.name());

        assertEquals("DRAFTING", summary.status());
        assertNull(summary.joinCode(), "a bot match has nobody to share a code with");

        Optional<MatchService.MatchParticipants> participants = matches.getParticipants(summary.matchId());
        assertTrue(participants.isPresent(), "both seats must be filled or no session can start");
        assertEquals(humanUserId, participants.get().playerOneId());
        assertTrue(matches.isBot(participants.get().playerTwoId()), "player two should be the bot account");
    }

    /**
     * join_code is UNIQUE. SQLite treats NULLs as distinct, so leaving it null lets any
     * number of bot matches coexist - if that assumption were wrong, the second bot match
     * anyone started would fail with a constraint violation.
     */
    @Test
    void manyBotMatchesCanExistAtOnceDespiteTheUniqueJoinCodeColumn() {
        String first = matches.createBotMatch(humanUserId, BotLevel.STANDARD.name()).matchId();
        String second = matches.createBotMatch(humanUserId, BotLevel.STANDARD.name()).matchId();
        String third = matches.createBotMatch(humanUserId, BotLevel.STANDARD.name()).matchId();

        assertNotEquals(first, second);
        assertNotEquals(second, third);
    }

    @Test
    void theChosenLevelIsStoredAndReadBack() {
        String matchId = matches.createBotMatch(humanUserId, BotLevel.STANDARD.name()).matchId();

        assertEquals(BotLevel.STANDARD, BotLevel.parse(matches.getBotLevel(matchId)));
    }

    /** A human-versus-human match has no level, and that must not be mistaken for a bot match. */
    @Test
    void anOrdinaryMatchHasNoBotLevelAndNoBotSeat() {
        String matchId = matches.createMatch(humanUserId).matchId();

        assertNull(matches.getBotLevel(matchId));
        assertFalse(matches.isBot(humanUserId));
    }

    @Test
    void theBotAccountIsCreatedOnceAndReusedAcrossServiceInstances() {
        long first = matches.botUserId();
        long second = new MatchService(db).botUserId();

        assertEquals(first, second, "a second MatchService must not create a duplicate bot account");
    }

    /** Nobody can wander into someone's single-player game. */
    @Test
    void aBotMatchCannotBeJoinedByCode() {
        matches.createBotMatch(humanUserId, BotLevel.STANDARD.name());
        long other = new AuthService(db).register("otherplayer", "password123").user().userId();

        assertThrows(ApiException.class, () -> matches.joinMatch(other, null));
        assertThrows(ApiException.class, () -> matches.joinMatch(other, ""));
    }

    @Test
    void anUnknownLevelIsRejectedRatherThanSilentlyDefaulted() {
        assertThrows(IllegalArgumentException.class, () -> BotLevel.parse("IMPOSSIBLE"));
        // A null or blank level is an old row or an old client, not a mistake - default it.
        assertEquals(BotLevel.STANDARD, BotLevel.parse(null));
        assertEquals(BotLevel.STANDARD, BotLevel.parse("  "));
    }
}
