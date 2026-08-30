package com.walnutt.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
                               List<String> tags, List<String> details, UpgradeDefinition upgrade) {

    private static final Pattern PLACEHOLDER = Pattern.compile("%([a-zA-Z_]+)(:pct)?%");

    /**
     * Marks an ability that must never be copied onto another unit by Joker's Mimic (or
     * any future copy mechanic). Reserved for abilities whose machinery assumes it stays
     * on the unit it was built for - see the tagged files in design_ideas/ for the
     * specific reason each one carries it.
     */
    public static final String NO_COPY = "no_copy";

    /**
     * Marks an ability Shawl's Hidden Potential must never offer. Such a file also simply
     * omits its "upgrade" block, so {@link #isUpgradeable()} already returns false - the
     * tag is the deliberate, greppable half of that pair, saying "this has no upgrade on
     * purpose" rather than "nobody has written one yet". Carried by summons' own kits
     * (a Branchling's aura, a Pylon's beam) and by burn.json, which is an effect
     * definition parked among the abilities rather than an ability at all.
     */
    public static final String NO_UPGRADE = "no_upgrade";

    /** Tags are optional in the JSON; every definition written before they existed has none. */
    public AbilityDefinition(String name, String type, String description, Map<String, Double> stats) {
        this(name, type, description, stats, List.of(), List.of(), null);
    }

    /**
     * Details are optional too - only the abilities whose text was long enough to be worth
     * splitting carry them, and a short one-line ability is expected to have none at all.
     */
    public AbilityDefinition(String name, String type, String description, Map<String, Double> stats,
                              List<String> tags) {
        this(name, type, description, stats, tags, List.of(), null);
    }

    /**
     * Keeps the pre-upgrade call sites (the whole test suite) building un-upgradeable
     * definitions unchanged - an ability with no upgrade block is exactly what every one
     * of them means.
     */
    public AbilityDefinition(String name, String type, String description, Map<String, Double> stats,
                              List<String> tags, List<String> details) {
        this(name, type, description, stats, tags, details, null);
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

    // ---- upgrades ----

    /**
     * Whether Shawl's Hidden Potential may unlock this at all. Note this is about the
     * DESIGN existing, not about the engine implementing it: an ability can be upgradeable
     * here and still be withheld from the dialogue because its behaviour has not been
     * written yet - see AbilityFactory.isUpgradeImplemented.
     */
    public boolean isUpgradeable() {
        return upgrade != null && !hasTag(NO_UPGRADE);
    }

    /** True for the one ability that may be unlocked more than once (Hidden Potential). */
    public boolean isUpgradeRepeatable() {
        return upgrade != null && upgrade.repeatable();
    }

    /**
     * Base stats with the upgrade's sparse overrides applied on top. The one map every
     * upgraded ability and every piece of upgraded text resolves against, so a stat the
     * upgrade never mentions keeps its base value rather than vanishing.
     *
     * LinkedHashMap rather than Map.copyOf because a stat may legitimately be negative
     * (Translocation's upgraded cast_range is -1, Ability.UNLIMITED_RANGE) and because
     * insertion order keeps the verbose tooltip's stat table stable.
     */
    public Map<String, Double> mergedStats() {
        Map<String, Double> merged = new LinkedHashMap<>(stats == null ? Map.of() : stats);
        if (upgrade != null) {
            merged.putAll(upgrade.statsOrEmpty());
        }
        return merged;
    }

    /** The upgraded form's type, falling back to the base - only Dilation and Dispersion differ. */
    public String upgradedType() {
        return upgrade == null || upgrade.type() == null ? type : upgrade.type();
    }

    public boolean isUpgradedPassive() {
        return "passive".equalsIgnoreCase(upgradedType());
    }

    /**
     * The upgraded form's description. Falls back to the BASE sentence re-resolved against
     * merged stats, which is correct for most upgrades - "deals %damage% damage" simply
     * starts reading 120 instead of 60 - and is why only the dozen abilities whose base
     * sentence stops being true had to write their own.
     */
    public String upgradedDescription() {
        String text = upgrade == null || upgrade.description() == null ? description : upgrade.description();
        return resolvePlaceholders(text, mergedStats());
    }

    /** As {@link #upgradedDescription()}, for the verbose bullet list. */
    public List<String> upgradedDetails() {
        List<String> source = upgrade != null && upgrade.details() != null ? upgrade.details() : details;
        return resolveAll(source, mergedStats());
    }

    /**
     * The one-line "what unlocking this buys you", resolved against merged stats so it
     * quotes the upgraded numbers ("3 bolts fall each turn", not 1). Empty for anything
     * that cannot be upgraded.
     *
     * Shown ONLY in Hidden Potential's dialogue - deliberately absent from AbilitySnapshot
     * so there is nowhere for it to leak into a tooltip.
     */
    public String formattedUpgradeSummary() {
        if (upgrade == null || upgrade.summary() == null) {
            return "";
        }
        return resolvePlaceholders(upgrade.summary(), mergedStats());
    }

    // ---- text rendering ----

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
        return resolvePlaceholders(description, stats);
    }

    /**
     * The long-form bullet points behind {@link #formattedDescription()}, placeholders
     * resolved the same way. The client shows these only on request (holding the expand
     * key over an ability), which is the whole reason the short description can stay
     * short. Empty for any ability that never needed splitting up.
     */
    public List<String> formattedDetails() {
        return resolveAll(details, stats);
    }

    private static List<String> resolveAll(List<String> texts, Map<String, Double> against) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<String> resolved = new ArrayList<>(texts.size());
        for (String text : texts) {
            resolved.add(resolvePlaceholders(text, against));
        }
        return List.copyOf(resolved);
    }

    /**
     * Static and explicitly given the stats to resolve against, rather than reading the
     * record's own: upgraded text has to resolve against {@link #mergedStats()}, and
     * having one renderer take the map is what stops base and upgraded text formatting
     * numbers two different ways.
     */
    private static String resolvePlaceholders(String text, Map<String, Double> against) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            Double stat = against == null ? null : against.get(matcher.group(1));
            double value = stat == null ? 0 : stat;
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
