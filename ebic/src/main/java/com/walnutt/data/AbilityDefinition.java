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

    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z_]+)%");

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
     * {@link #stats} (every ability description in design_ideas/ is written this way -
     * the placeholder name always matches a stats{} key verbatim). This is a faithful,
     * mechanical substitution, not a smart one: a handful of hand-written descriptions
     * assume a fraction like 0.5 will read as "50" next to a literal "%" the author
     * typed themselves, and this deliberately doesn't guess at that - per CLAUDE.md,
     * design_ideas/ is hand-written content with occasional rough edges, and this is
     * exactly the kind of inconsistency to tolerate rather than paper over with a
     * fragile heuristic that would be wrong just as often as it's right.
     */
    public String formattedDescription() {
        if (description == null || description.isEmpty()) {
            return description;
        }
        Matcher matcher = PLACEHOLDER.matcher(description);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            double value = getDouble(matcher.group(1), 0);
            String formatted = value == Math.rint(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(formatted));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
