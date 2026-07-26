package com.walnutt.web.match;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import com.walnutt.web.ApiException;
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

    public enum Status { WAITING, DRAFTING, IN_PROGRESS, FINISHED }

    public record MatchSummary(String matchId, String joinCode, String status) {
    }

    public record MatchStatusView(String matchId, String status, String playerOneName, String playerTwoName,
                                   String yourTeam, String winnerName) {
    }

    /** Row needed by GameSessionManager to spin up a match's engine session. */
    public record MatchParticipants(String matchId, long playerOneId, long playerTwoId) {
    }

    private final Database db;

    public MatchService(Database db) {
        this.db = db;
    }

    public MatchSummary createMatch(long callerUserId) {
        String matchId = UUID.randomUUID().toString();
        String joinCode = generateUniqueJoinCode();
        String sql = """
            INSERT INTO matches (id, join_code, status, player_one_id, created_at)
            VALUES (?, ?, ?, ?, ?)
            """;
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, matchId);
            ps.setString(2, joinCode);
            ps.setString(3, Status.WAITING.name());
            ps.setLong(4, callerUserId);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create match", e);
        }
        return new MatchSummary(matchId, joinCode, Status.WAITING.name());
    }

    public MatchSummary joinMatch(long callerUserId, String joinCode) {
        if (joinCode == null || joinCode.isBlank()) {
            throw new ApiException(404, "no such match");
        }
        String selectSql = "SELECT id, status, player_one_id, player_two_id FROM matches WHERE join_code = ?";
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

                if (playerOneId == callerUserId || (playerTwoId != null && playerTwoId == callerUserId)) {
                    throw new ApiException(409, "you are already in this match");
                }
                if (!Status.WAITING.name().equals(status) || playerTwoId != null) {
                    throw new ApiException(409, "match is no longer joinable");
                }

                try (PreparedStatement update = db.connection().prepareStatement(
                        "UPDATE matches SET player_two_id = ?, status = ? WHERE id = ? AND player_two_id IS NULL")) {
                    update.setLong(1, callerUserId);
                    update.setString(2, Status.DRAFTING.name());
                    update.setString(3, matchId);
                    int rows = update.executeUpdate();
                    if (rows == 0) {
                        // Lost a race against another joiner between SELECT and UPDATE.
                        throw new ApiException(409, "match is no longer joinable");
                    }
                }
                return new MatchSummary(matchId, joinCode, Status.DRAFTING.name());
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to join match", e);
        }
    }

    public MatchStatusView getStatus(long callerUserId, String matchId) {
        String sql = """
            SELECT m.status, m.player_one_id, m.player_two_id, m.winner_id,
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
                    rs.getString("wname")
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
