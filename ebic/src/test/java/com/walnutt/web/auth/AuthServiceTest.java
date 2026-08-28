package com.walnutt.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.walnutt.web.ApiException;
import com.walnutt.web.db.Database;

class AuthServiceTest {
    private Path dbFile;
    private Database db;
    private AuthService auth;

    @BeforeEach
    void setUp() throws IOException {
        dbFile = Files.createTempFile("ebic-test-", ".db");
        db = new Database(dbFile.toString());
        db.migrate();
        auth = new AuthService(db);
    }

    @AfterEach
    void tearDown() throws IOException {
        db.close();
        Files.deleteIfExists(dbFile);
    }

    @Test
    void favouriteUnitStartsUnsetAndRoundTrips() {
        long userId = auth.register("favouriter", "password123").user().userId();

        assertNull(auth.getFavouriteUnit(userId), "a new account has no favourite");

        auth.setFavouriteUnit(userId, "valor");
        assertEquals("valor", auth.getFavouriteUnit(userId));

        // Null is what the client's "None" option sends - it clears rather than storing "".
        auth.setFavouriteUnit(userId, null);
        assertNull(auth.getFavouriteUnit(userId));
    }

    @Test
    void registerThenResolveSession_roundTrips() {
        AuthService.SessionResult result = auth.register("alice", "hunter22");
        assertEquals("alice", result.user().username());

        var resolved = auth.resolveSession(result.token());
        assertTrue(resolved.isPresent());
        assertEquals(result.user().userId(), resolved.get().userId());
    }

    @Test
    void registerRejectsInvalidUsernameAndShortPassword() {
        assertThrows(ApiException.class, () -> auth.register("ab", "longenoughpassword"));
        assertThrows(ApiException.class, () -> auth.register("has space", "longenoughpassword"));
        assertThrows(ApiException.class, () -> auth.register("validname", "short"));
    }

    @Test
    void registerRejectsDuplicateUsernameCaseInsensitively() {
        auth.register("Bob", "password123");
        ApiException e = assertThrows(ApiException.class, () -> auth.register("bob", "differentpass"));
        assertEquals(409, e.getStatus());
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        auth.register("carol", "correcthorse");
        AuthService.SessionResult result = auth.login("carol", "correcthorse", "1.2.3.4");
        assertEquals("carol", result.user().username());
    }

    @Test
    void loginFailsWithSameMessageForUnknownUserAndWrongPassword() {
        auth.register("dave", "correctpassword");

        ApiException wrongPassword = assertThrows(ApiException.class,
            () -> auth.login("dave", "wrongpassword", "1.2.3.4"));
        ApiException unknownUser = assertThrows(ApiException.class,
            () -> auth.login("nosuchuser", "whatever1", "1.2.3.4"));

        assertEquals(401, wrongPassword.getStatus());
        assertEquals(401, unknownUser.getStatus());
        assertEquals(wrongPassword.getMessage(), unknownUser.getMessage());
    }

    @Test
    void loginIsRateLimitedPerSourceIp() {
        auth.register("erin", "correctpassword");
        AuthService limited = new AuthService(db, new RateLimiter(2, java.time.Duration.ofMinutes(5)));

        assertThrows(ApiException.class, () -> limited.login("erin", "wrong", "9.9.9.9"));
        assertThrows(ApiException.class, () -> limited.login("erin", "wrong", "9.9.9.9"));
        ApiException third = assertThrows(ApiException.class, () -> limited.login("erin", "wrong", "9.9.9.9"));
        assertEquals(429, third.getStatus());
    }

    @Test
    void logoutInvalidatesTheSession() {
        AuthService.SessionResult result = auth.register("frank", "correctpassword");
        auth.logout(result.token());
        assertFalse(auth.resolveSession(result.token()).isPresent());
    }
}
