package com.walnutt.web.match;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import com.walnutt.web.ApiException;
import com.walnutt.web.auth.PasswordHasher;
import com.walnutt.web.db.Database;

/**
 * Match lobby: create / join-by-code / status lookup, per API_CONTRACT.md.
 * `matchId` is a UUID (used verbatim in the WS URL); `joinCode` is a short
 * human-typeable 6-char code, distinct from matchId on purpose.
 */
public final class MatchService {
    private static final String JOIN_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I/L
    private static final int JOIN_CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    public enum Status { LOBBY, DRAFTING, IN_PROGRESS, FINISHED }

    public record MatchSummary(String matchId, String joinCode, String status, boolean isPublic) {
    }

    public record MatchStatusView(String matchId, String status, String playerOneName, String playerTwoName,
                                   String yourTeam, String winnerName, boolean isPublic) {
    }

    /** One row in the public lobby browser. */
    public record PublicLobbySummary(String matchId, String joinCode, String status, String playerOneName,
                                      boolean full) {
    }

    /** Row needed by GameSessionManager to spin up a match's engine session. */
    public record MatchParticipants(String matchId, long playerOneId, long playerTwoId) {
    }

    /**
     * The account a bot match's second seat belongs to. A real users row, rather than a
     * nullable player_two_id, so that every existing read path - getParticipants,
     * GameSession.teamFor, finishMatch's winner, the WS upgrade's participant check -
     * keeps working untouched. It is never logged into: its password is a throwaway
     * random string that is hashed and immediately discarded.
     */
    public static final String BOT_USERNAME = "EBIC_Bot";

    private final Database db;
    private final long botUserId;

    public MatchService(Database db) {
        this.db = db;
        this.botUserId = ensureBotUser();
    }

    /** The reserved bot account's id, creating the account on first use. */
    public long botUserId() {
        return botUserId;
    }

    public boolean isBot(long userId) {
        return userId == botUserId;
    }

    private long ensureBotUser() {
        Long existing = findBotUser();
        if (existing != null) {
            return existing;
        }

        // Hashed like any other password so login()'s verify path behaves normally; the
        // plaintext is never kept, so there is nothing to log in with.
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        PasswordHasher.HashResult hashed = PasswordHasher.hash(
            java.util.Base64.getEncoder().encodeToString(secret).toCharArray());

        try (PreparedStatement ps = db.connection().prepareStatement(
                "INSERT INTO users (username, password_hash, password_salt, created_at) VALUES (?, ?, ?, ?)",
                java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, BOT_USERNAME);
            ps.setString(2, hashed.hashBase64());
            ps.setString(3, hashed.saltBase64());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            // username is UNIQUE, so losing a race with another MatchService constructed
            // at the same time surfaces here; the row it created is the one we wanted.
            Long raced = findBotUser();
            if (raced != null) {
                return raced;
            }
            throw new IllegalStateException("Failed to create the bot account", e);
        }
    }

    private Long findBotUser() {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT id FROM users WHERE username = ? COLLATE NOCASE")) {
            ps.setString(1, BOT_USERNAME);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to look up the bot account", e);
        }
    }

    /**
     * A match against the bot: opponent already seated, so it starts at DRAFTING with no
     * join code. A null join_code is deliberate - the column is UNIQUE but nullable, and
     * SQLite treats NULLs as distinct, so any number of bot matches can coexist. It also
     * means a bot match can never be joined by a person typing a code.
     */
    public MatchSummary createBotMatch(long callerUserId, String botLevel) {
        String matchId = UUID.randomUUID().toString();
        String sql = """
            INSERT INTO matches (id, join_code, status, player_one_id, player_two_id, bot_level, created_at)
            VALUES (?, NULL, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, matchId);
            ps.setString(2, Status.DRAFTING.name());
            ps.setLong(3, callerUserId);
            ps.setLong(4, botUserId);
            ps.setString(5, botLevel);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create bot match", e);
        }
        return new MatchSummary(matchId, null, Status.DRAFTING.name(), false);
    }

    /** The difficulty this match was created with, or null for a human-versus-human match. */
    public String getBotLevel(String matchId) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "SELECT bot_level FROM matches WHERE id = ?")) {
            ps.setString(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("bot_level") : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load the match's bot level", e);
        }
    }

    public MatchSummary createMatch(long callerUserId, boolean isPublic) {
        String matchId = UUID.randomUUID().toString();
        String joinCode = generateUniqueJoinCode();
        String sql = """
            INSERT INTO matches (id, join_code, status, player_one_id, is_public, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, matchId);
            ps.setString(2, joinCode);
            ps.setString(3, Status.LOBBY.name());
            ps.setLong(4, callerUserId);
            ps.setBoolean(5, isPublic);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create match", e);
        }
        return new MatchSummary(matchId, joinCode, Status.LOBBY.name(), isPublic);
    }

    /**
     * Seats the caller as player two, but leaves status at LOBBY - joining no longer starts
     * the game. The lobby owner explicitly starts it via startLobby() once both seats are
     * filled. A public lobby's join code stays valid even after it's listed in the browser;
     * this method doesn't distinguish how the caller found the code.
     */
    public MatchSummary joinMatch(long callerUserId, String joinCode) {
        if (joinCode == null || joinCode.isBlank()) {
            throw new ApiException(404, "no such match");
        }
        String selectSql = "SELECT id, status, player_one_id, player_two_id, is_public FROM matches WHERE join_code = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(selectSql)) {
            ps.setString(1, joinCode.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "no such match");
                }
                String matchId = rs.getString("id");
                String status = rs.getString("status");
                long playerOneId = rs.getLong("player_one_id");
                Long playerTwoId = rs.getObject("player_two_id") == null ? null : rs.getLong("player_two_id");
                boolean isPublic = rs.getBoolean("is_public");

                if (playerOneId == callerUserId || (playerTwoId != null && playerTwoId == callerUserId)) {
                    throw new ApiException(409, "you are already in this match");
                }
                if (!Status.LOBBY.name().equals(status) || playerTwoId != null) {
                    throw new ApiException(409, "lobby is full");
                }

                try (PreparedStatement update = db.connection().prepareStatement(
                        "UPDATE matches SET player_two_id = ? WHERE id = ? AND player_two_id IS NULL")) {
                    update.setLong(1, callerUserId);
                    update.setString(2, matchId);
                    int rows = update.executeUpdate();
                    if (rows == 0) {
                        // Lost a race against another joiner between SELECT and UPDATE.
                        throw new ApiException(409, "lobby is full");
                    }
                }
                return new MatchSummary(matchId, joinCode, Status.LOBBY.name(), isPublic);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to join match", e);
        }
    }

    /**
     * Joining from the public lobby browser rather than by typed code. The browser's list can
     * go stale (the owner flips it back to private, or someone else fills the seat, between
     * the last refresh and this click), so this re-validates everything at the moment of the
     * actual join rather than trusting what the client last saw - one "not available" message
     * covers every reason, since the list UI doesn't need to distinguish them. Unlike
     * joinMatch, a private lobby is deliberately rejected here even though it still exists.
     */
    public MatchSummary joinPublicLobby(long callerUserId, String matchId) {
        String selectSql = "SELECT join_code, status, player_one_id, player_two_id, is_public FROM matches WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(selectSql)) {
            ps.setString(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "no such match");
                }
                String joinCode = rs.getString("join_code");
                String status = rs.getString("status");
                long playerOneId = rs.getLong("player_one_id");
                Long playerTwoId = rs.getObject("player_two_id") == null ? null : rs.getLong("player_two_id");
                boolean isPublic = rs.getBoolean("is_public");

                if (playerOneId == callerUserId || (playerTwoId != null && playerTwoId == callerUserId)) {
                    throw new ApiException(409, "you are already in this match");
                }
                if (!isPublic || !Status.LOBBY.name().equals(status) || playerTwoId != null) {
                    throw new ApiException(409, "this lobby is not available");
                }

                try (PreparedStatement update = db.connection().prepareStatement(
                        "UPDATE matches SET player_two_id = ? WHERE id = ? AND player_two_id IS NULL")) {
                    update.setLong(1, callerUserId);
                    update.setString(2, matchId);
                    int rows = update.executeUpdate();
                    if (rows == 0) {
                        // Lost a race against another joiner between SELECT and UPDATE.
                        throw new ApiException(409, "this lobby is not available");
                    }
                }
                return new MatchSummary(matchId, joinCode, Status.LOBBY.name(), isPublic);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to join public lobby", e);
        }
    }

    /**
     * Owner-only "START GAME": flips LOBBY -> DRAFTING once both seats are filled. This is
     * now the only place a human match ever writes DRAFTING - GameSession.runMatch() picks
     * up from there and writes IN_PROGRESS itself once draft+placement actually finish.
     */
    public MatchSummary startLobby(long callerUserId, String matchId) {
        String selectSql = "SELECT status, player_one_id, player_two_id, join_code, is_public FROM matches WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(selectSql)) {
            ps.setString(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "no such match");
                }
                String status = rs.getString("status");
                long playerOneId = rs.getLong("player_one_id");
                Long playerTwoId = rs.getObject("player_two_id") == null ? null : rs.getLong("player_two_id");
                String joinCode = rs.getString("join_code");
                boolean isPublic = rs.getBoolean("is_public");

                boolean isParticipant = playerOneId == callerUserId || (playerTwoId != null && playerTwoId == callerUserId);
                if (!isParticipant) {
                    throw new ApiException(404, "no such match");
                }
                if (playerOneId != callerUserId) {
                    throw new ApiException(403, "only the lobby owner can start the game");
                }
                if (!Status.LOBBY.name().equals(status) || playerTwoId == null) {
                    throw new ApiException(409, "lobby is not ready to start");
                }

                try (PreparedStatement update = db.connection().prepareStatement(
                        "UPDATE matches SET status = ? WHERE id = ? AND status = ? AND player_two_id IS NOT NULL")) {
                    update.setString(1, Status.DRAFTING.name());
                    update.setString(2, matchId);
                    update.setString(3, Status.LOBBY.name());
                    int rows = update.executeUpdate();
                    if (rows == 0) {
                        throw new ApiException(409, "lobby is not ready to start");
                    }
                }
                return new MatchSummary(matchId, joinCode, Status.DRAFTING.name(), isPublic);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to start lobby", e);
        }
    }

    /**
     * Owner-only: flips a lobby's public/private flag while it's still waiting to start.
     * Locked once drafting/playing begins - changing who can find a match mid-game makes
     * no sense, and the public browser only ever lists LOBBY/DRAFTING/IN_PROGRESS anyway.
     */
    public void setVisibility(long callerUserId, String matchId, boolean isPublic) {
        String selectSql = "SELECT status, player_one_id, player_two_id FROM matches WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(selectSql)) {
            ps.setString(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "no such match");
                }
                String status = rs.getString("status");
                long playerOneId = rs.getLong("player_one_id");
                Long playerTwoId = rs.getObject("player_two_id") == null ? null : rs.getLong("player_two_id");
                boolean isParticipant = playerOneId == callerUserId || (playerTwoId != null && playerTwoId == callerUserId);
                if (!isParticipant) {
                    throw new ApiException(404, "no such match");
                }
                if (playerOneId != callerUserId) {
                    throw new ApiException(403, "only the lobby owner can change visibility");
                }
                if (!Status.LOBBY.name().equals(status)) {
                    throw new ApiException(409, "cannot change visibility once the game has started");
                }
            }
            try (PreparedStatement update = db.connection().prepareStatement(
                    "UPDATE matches SET is_public = ? WHERE id = ? AND status = ?")) {
                update.setBoolean(1, isPublic);
                update.setString(2, matchId);
                update.setString(3, Status.LOBBY.name());
                update.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to change match visibility", e);
        }
    }

    /**
     * Leaving before the game starts. A joiner leaving just frees their seat so someone
     * else can take it. An owner leaving abandons a lobby nobody has started yet - there's
     * nothing worth keeping, so the row is deleted outright rather than left to rot in the
     * public browser with no way for anyone to close it.
     */
    public void leaveLobby(long callerUserId, String matchId) {
        String selectSql = "SELECT status, player_one_id, player_two_id FROM matches WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(selectSql)) {
            ps.setString(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "no such match");
                }
                String status = rs.getString("status");
                long playerOneId = rs.getLong("player_one_id");
                Long playerTwoId = rs.getObject("player_two_id") == null ? null : rs.getLong("player_two_id");
                boolean isParticipant = playerOneId == callerUserId || (playerTwoId != null && playerTwoId == callerUserId);
                if (!isParticipant) {
                    throw new ApiException(404, "no such match");
                }
                if (!Status.LOBBY.name().equals(status)) {
                    throw new ApiException(409, "cannot leave once the game has started");
                }

                if (playerOneId == callerUserId) {
                    try (PreparedStatement delete = db.connection().prepareStatement(
                            "DELETE FROM matches WHERE id = ? AND status = ?")) {
                        delete.setString(1, matchId);
                        delete.setString(2, Status.LOBBY.name());
                        delete.executeUpdate();
                    }
                } else {
                    try (PreparedStatement update = db.connection().prepareStatement(
                            "UPDATE matches SET player_two_id = NULL WHERE id = ? AND player_two_id = ?")) {
                        update.setString(1, matchId);
                        update.setLong(2, callerUserId);
                        update.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to leave lobby", e);
        }
    }

    /**
     * Open public lobbies for the Join Match browser. Deliberately includes DRAFTING and
     * IN_PROGRESS (not just LOBBY) - a public match already underway should still be
     * visible (as not-joinable) until it's FINISHED and cleaned up, matching the brief's
     * "LOBBY or IN PROGRESS" status display. Display-status collapsing happens in the REST
     * handler; this returns ground truth plus a `full` flag for the seat count.
     */
    public java.util.List<PublicLobbySummary> listPublicLobbies() {
        String sql = """
            SELECT m.id, m.join_code, m.status, m.player_two_id, p1.username AS p1name
            FROM matches m
            JOIN users p1 ON p1.id = m.player_one_id
            WHERE m.is_public = 1 AND m.status IN (?, ?, ?)
            ORDER BY m.created_at DESC
            """;
        java.util.List<PublicLobbySummary> lobbies = new java.util.ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, Status.LOBBY.name());
            ps.setString(2, Status.DRAFTING.name());
            ps.setString(3, Status.IN_PROGRESS.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    boolean full = rs.getObject("player_two_id") != null;
                    lobbies.add(new PublicLobbySummary(
                        rs.getString("id"), rs.getString("join_code"), rs.getString("status"),
                        rs.getString("p1name"), full));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list public lobbies", e);
        }
        return lobbies;
    }

    public MatchStatusView getStatus(long callerUserId, String matchId) {
        String sql = """
            SELECT m.status, m.player_one_id, m.player_two_id, m.winner_id, m.is_public,
                   p1.username AS p1name, p2.username AS p2name, w.username AS wname
            FROM matches m
            JOIN users p1 ON p1.id = m.player_one_id
            LEFT JOIN users p2 ON p2.id = m.player_two_id
            LEFT JOIN users w ON w.id = m.winner_id
            WHERE m.id = ?
            """;
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(404, "no such match");
                }
                long playerOneId = rs.getLong("player_one_id");
                Long playerTwoId = rs.getObject("player_two_id") == null ? null : rs.getLong("player_two_id");
                if (playerOneId != callerUserId && (playerTwoId == null || playerTwoId != callerUserId)) {
                    throw new ApiException(404, "no such match");
                }
                String yourTeam = playerOneId == callerUserId ? "PLAYER_ONE" : "PLAYER_TWO";
                return new MatchStatusView(
                    matchId,
                    rs.getString("status"),
                    rs.getString("p1name"),
                    rs.getString("p2name"),
                    yourTeam,
                    rs.getString("wname"),
                    rs.getBoolean("is_public")
                );
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load match status", e);
        }
    }

    /** For GameSessionManager: which two users this match belongs to, or empty if not both present yet. */
    public Optional<MatchParticipants> getParticipants(String matchId) {
        String sql = "SELECT player_one_id, player_two_id FROM matches WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                long p1 = rs.getLong("player_one_id");
                Object p2Obj = rs.getObject("player_two_id");
                if (p2Obj == null) {
                    return Optional.empty();
                }
                return Optional.of(new MatchParticipants(matchId, p1, rs.getLong("player_two_id")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load match participants", e);
        }
    }

    /** For GameSessionManager's zombie-session guard: the match's current status, or null if it doesn't exist. */
    public Status getRawStatus(String matchId) {
        try (PreparedStatement ps = db.connection().prepareStatement("SELECT status FROM matches WHERE id = ?")) {
            ps.setString(1, matchId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Status.valueOf(rs.getString("status")) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load match status", e);
        }
    }

    public void setStatus(String matchId, Status status) {
        try (PreparedStatement ps = db.connection().prepareStatement("UPDATE matches SET status = ? WHERE id = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, matchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update match status", e);
        }
    }

    public void finishMatch(String matchId, Long winnerUserId) {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "UPDATE matches SET status = ?, winner_id = ? WHERE id = ?")) {
            ps.setString(1, Status.FINISHED.name());
            if (winnerUserId == null) {
                ps.setNull(2, java.sql.Types.BIGINT);
            } else {
                ps.setLong(2, winnerUserId);
            }
            ps.setString(3, matchId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to finish match", e);
        }
    }

    /**
     * Called once at real server startup (see WebServer's constructor) - any non-FINISHED
     * row is a lobby/match from a previous server process that can never be resumed, since
     * all in-memory GameSession state died with that process. FINISHED rows are left alone;
     * history is still worth keeping.
     */
    public void purgeStaleMatches() {
        try (PreparedStatement ps = db.connection().prepareStatement(
                "DELETE FROM matches WHERE status != ?")) {
            ps.setString(1, Status.FINISHED.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to purge stale matches", e);
        }
    }

    private String generateUniqueJoinCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String candidate = randomJoinCode();
            try (PreparedStatement ps = db.connection().prepareStatement(
                    "SELECT 1 FROM matches WHERE join_code = ?")) {
                ps.setString(1, candidate);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return candidate;
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to generate join code", e);
            }
        }
        throw new IllegalStateException("Could not generate a unique join code after 20 attempts");
    }

    private String randomJoinCode() {
        StringBuilder sb = new StringBuilder(JOIN_CODE_LENGTH);
        for (int i = 0; i < JOIN_CODE_LENGTH; i++) {
            sb.append(JOIN_CODE_ALPHABET.charAt(RANDOM.nextInt(JOIN_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
