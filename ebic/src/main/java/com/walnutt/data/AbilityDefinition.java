package com.walnutt.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsed shape of design_ideas/abilities/<unit>/<ability>.json. "stats" is a loose
 * map (not a fixed record) because every ability tunes different numbers - the
 * hand-written Ability/Effect subclass knows which keys it needs.
 */
public record AbilityDefinition(String name, String type, String description, Map<String, Double> stats,
                               List<String> tags, List<String> details) {

    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z_]+)(:pct)?%");

    /**
     * Marks an ability that must never be copied onto another unit by Joker's Mimic (or
     * any future copy mechanic). Reserved for abilities whose machinery assumes it stays
     * on the unit it was built for - see the tagged files in design_ideas/ for the
     * specific reason each one carries it.
     */
    public static final String NO_COPY = "no_copy";

    /** Tags are optional in the JSON; every definition written before they existed has none. */
    public AbilityDefinition(String name, String type, String description, Map<String, Double> stats) {
        this(name, type, description, stats, List.of(), List.of());
    }

    /**
     * Details are optional too - only the abilities whose text was long enough to be worth
     * splitting carry them, and a short one-line ability is expected to have none at all.
     */
    public AbilityDefinition(String name, String type, String description, Map<String, Double> stats,
                              List<String> tags) {
        this(name, type, description, stats, tags, List.of());
    }

    /**
     * Null-tolerant: Gson builds records through the canonical constructor, so a
     * definition whose JSON simply omits "tags" arrives here with null rather than an
     * empty list.
     */
    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag);
    }

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
        return resolvePlaceholders(description);
    }

    /**
     * The long-form bullet points behind {@link #formattedDescription()}, placeholders
     * resolved the same way. The client shows these only on request (holding the expand
     * key over an ability), which is the whole reason the short description can stay
     * short. Empty for any ability that never needed splitting up.
     */
    public List<String> formattedDetails() {
        if (details == null || details.isEmpty()) {
            return List.of();
        }
        List<String> resolved = new ArrayList<>(details.size());
        for (String detail : details) {
            resolved.add(resolvePlaceholders(detail));
        }
        return List.copyOf(resolved);
    }

    private String resolvePlaceholders(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
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
