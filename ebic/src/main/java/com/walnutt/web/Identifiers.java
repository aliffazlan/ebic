package com.walnutt.web;

import java.util.Locale;

/**
 * Normalizes display names ("Cloak and Dagger", "Overwhelming Odds") into the
 * lowercase_underscore ids used both as design_ideas/ JSON filenames and as the
 * `definitionId`/ability `id` fields in the web DTOs, so the frontend can key
 * icon lookups off the same string the engine/JSON already uses.
 */
public final class Identifiers {
    private Identifiers() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT).trim();
        StringBuilder sb = new StringBuilder(lower.length());
        boolean lastWasUnderscore = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                lastWasUnderscore = false;
            } else if (!lastWasUnderscore && sb.length() > 0) {
                sb.append('_');
                lastWasUnderscore = true;
            }
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '_') {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }
}
