package com.walnutt.ai;

import java.util.Locale;

/**
 * The difficulty a bot match was created with, as it travels over the API and sits in
 * the database.
 *
 * Only one level exists today. It is an enum rather than a bare boolean so that adding
 * "easy" or "hard" later is a constant here plus a {@link BotConfig} factory method -
 * not a change to the HTTP contract, the matches table, or the client. That is why the
 * API takes and validates a level from the very first version.
 */
public enum BotLevel {
    STANDARD(BotConfig.standard());

    private final BotConfig config;

    BotLevel(BotConfig config) {
        this.config = config;
    }

    public BotConfig config() {
        return config;
    }

    /** Parses a wire/database value. Null or blank means the default, so old rows keep working. */
    public static BotLevel parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return STANDARD;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown bot level: " + raw);
        }
    }
}
