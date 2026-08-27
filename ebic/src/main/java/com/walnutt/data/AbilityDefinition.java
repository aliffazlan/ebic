package com.walnutt.data;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed shape of design_ideas/abilities/<unit>/<ability>.json. "stats" is a loose
 * map (not a fixed record) because every ability tunes different numbers - the
 * hand-written Ability/Effect subclass knows which keys it needs.
 */
public record AbilityDefinition(String name, String type, String description, Map<String, Double> stats) {

    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z_]+)(:pct)?%");

    public double getDouble(String key, double fallback) {
        if (stats == null || !stats.containsKey(key)) {
            return fallback;
        }
        return stats.get(key);
    }

    public int getInt(String key, int fallback) {
        return (int) Math.round(getDouble(key, fallback));
    }

    public boolean isPassive() {
        return "passive".equalsIgnoreCase(type);
    }

    /**
     * Resolves every {@code %stat_key%} placeholder in {@link #description} against
     * {@link #stats} (the placeholder name always matches a stats{} key verbatim).
     *
     * <p>Percent-valued stats are stored as fractions (0.2 = 20%) because that's the
     * form the Java ability code actually multiplies by, so a bare {@code %key%} next
     * to a literal "%" would render "0.2 %". Writing {@code %key:pct%} instead scales
     * by 100 for display only - the stored stat, and every {@code getDouble} caller,
     * stay untouched.
     */
    public String formattedDescription() {
        if (description == null || description.isEmpty()) {
            return description;
        }
        Matcher matcher = PLACEHOLDER.matcher(description);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            double value = getDouble(matcher.group(1), 0);
            if (matcher.group(2) != null) {
                value *= 100;
            }
            String formatted = value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(formatted));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
