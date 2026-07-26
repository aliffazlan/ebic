package com.walnutt.web.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Thin SQLite wrapper. One JVM-wide connection is fine at this scale (SQLite
 * serializes writers internally); every query still goes through
 * PreparedStatement, never string concatenation. Schema is created idempotently
 * on startup via {@link #migrate()} - no separate migration tool needed for a
 * project this size.
 */
public final class Database {
    private final Connection connection;

    public Database(String path) {
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
            }
        } catch (ClassNotFoundException | SQLException e) {
            throw new IllegalStateException("Failed to open SQLite database at " + path, e);
        }
    }

    public Connection connection() {
        return connection;
    }

    public void migrate() {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    username TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    password_hash TEXT NOT NULL,
                    password_salt TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    token TEXT PRIMARY KEY,
                    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER NOT NULL
                )
                """);
            st.execute("""
                CREATE TABLE IF NOT EXISTS matches (
                    id TEXT PRIMARY KEY,
                    join_code TEXT UNIQUE,
                    status TEXT NOT NULL,
                    player_one_id INTEGER NOT NULL REFERENCES users(id),
                    player_two_id INTEGER REFERENCES users(id),
                    winner_id INTEGER REFERENCES users(id),
                    created_at INTEGER NOT NULL
                )
                """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_matches_join_code ON matches(join_code)");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to run schema migration", e);
        }
    }

    public void close() {
        try {
            connection.close();
        } catch (SQLException ignored) {
            // best-effort on shutdown
        }
    }
}
