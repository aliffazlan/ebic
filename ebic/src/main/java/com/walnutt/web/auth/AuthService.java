package com.walnutt.web.auth;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

import com.walnutt.web.ApiException;
import com.walnutt.web.db.Database;

/**
 * Register/login/logout/me per API_CONTRACT.md: validation rules, the
 * same-error-message anti-enumeration behavior on login, opaque 32-byte
 * base64url session tokens, and a login rate limiter.
 */
public final class AuthService {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();
    /** Matches WebServer.SESSION_MAX_AGE_SECONDS - keep the two in sync (server-side
     * session validity shouldn't outlive, or wildly undercut, the cookie's own lifetime). */
    private static final long SESSION_LIFETIME_MILLIS = Duration.ofDays(30).toMillis();

    private final Database db;
    private final RateLimiter loginLimiter;

    public record AuthedUser(long userId, String username) {
    }

    public AuthService(Database db) {
        this(db, new RateLimiter(10, Duration.ofMinutes(5)));
    }

    public AuthService(Database db, RateLimiter loginLimiter) {
        this.db = db;
        this.loginLimiter = loginLimiter;
    }

    /** Returns the new user plus a freshly-issued session token. */
    public SessionResult register(String username, String password) {
        validateUsername(username);
        validatePassword(password);

        PasswordHasher.HashResult hashed = PasswordHasher.hash(password.toCharArray());
        String sql = "INSERT INTO users (username, password_hash, password_salt, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hashed.hashBase64());
            ps.setString(3, hashed.saltBase64());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                long userId = keys.getLong(1);
                String token = issueSession(userId);
                return new SessionResult(new AuthedUser(userId, username), token);
            }
        } catch (SQLException e) {
            if (isUniqueViolation(e)) {
                throw new ApiException(409, "username is already taken");
            }
            throw new IllegalStateException("Failed to register user", e);
        }
    }

    public SessionResult login(String username, String password, String sourceIp) {
        if (!loginLimiter.tryAcquire(sourceIp)) {
            throw new ApiException(429, "too many login attempts, try again later");
        }

        String sql = "SELECT id, username, password_hash, password_salt FROM users WHERE username = ? COLLATE NOCASE";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, username == null ? "" : username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(401, "invalid username or password");
                }
                long userId = rs.getLong("id");
                String actualUsername = rs.getString("username");
                String hash = rs.getString("password_hash");
                String salt = rs.getString("password_salt");

                boolean ok = password != null && PasswordHasher.verify(password.toCharArray(), salt, hash);
                if (!ok) {
                    throw new ApiException(401, "invalid username or password");
                }
                String token = issueSession(userId);
                return new SessionResult(new AuthedUser(userId, actualUsername), token);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to log in", e);
        }
    }

    public void logout(String token) {
        if (token == null) {
            return;
        }
        try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM sessions WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to log out", e);
        }
    }

    public Optional<AuthedUser> resolveSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String sql = """
            SELECT u.id, u.username, s.expires_at FROM sessions s
            JOIN users u ON u.id = s.user_id
            WHERE s.token = ?
            """;
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, token);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                if (rs.getLong("expires_at") <= System.currentTimeMillis()) {
                    deleteSession(token);
                    return Optional.empty();
                }
                return Optional.of(new AuthedUser(rs.getLong("id"), rs.getString("username")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to resolve session", e);
        }
    }

    private void deleteSession(String token) {
        try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM sessions WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete expired session", e);
        }
    }

    private String issueSession(long userId) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT INTO sessions (token, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, token);
            ps.setLong(2, userId);
            ps.setLong(3, now);
            ps.setLong(4, now + SESSION_LIFETIME_MILLIS);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to issue session", e);
        }
        return token;
    }

    private void validateUsername(String username) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches()) {
            throw new ApiException(400, "username must be 3-20 characters, letters/digits/underscore only");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ApiException(400, "password must be at least 8 characters");
        }
    }

    private boolean isUniqueViolation(SQLException e) {
        String message = e.getMessage();
        return message != null && message.toUpperCase().contains("UNIQUE");
    }

    public record SessionResult(AuthedUser user, String token) {
    }
}
