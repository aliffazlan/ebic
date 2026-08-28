package com.walnutt.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class AbilityDefinitionTest {

    private static AbilityDefinition withStats(String description, Map<String, Double> stats) {
        return new AbilityDefinition("Test", "active", description, stats);
    }

    @Test
    void substitutesPlainPlaceholders_trimmingTrailingZeroesOnWholeNumbers() {
        AbilityDefinition def = withStats("Deals %damage% damage over %duration% turns.",
            Map.of("damage", 24.0, "duration", 3.0));

        assertEquals("Deals 24 damage over 3 turns.", def.formattedDescription());
    }

    @Test
    void pctSuffixScalesFractionsToWholePercentages() {
        AbilityDefinition def = withStats("Counters for %damage_reduction:pct%% less damage.",
            Map.of("damage_reduction", 0.2));

        // Without the :pct suffix this would read "0.2% less damage".
        assertEquals("Counters for 20% less damage.", def.formattedDescription());
    }

    @Test
    void pctSuffixKeepsAFractionalResultWhenItIsNotAWholePercent() {
        AbilityDefinition def = withStats("Steals %rate:pct%%.", Map.of("rate", 0.125));

        assertEquals("Steals 12.5%.", def.formattedDescription());
    }

    @Test
    void theSameStatCanAppearBothWays() {
        AbilityDefinition def = withStats("raw=%rate% pct=%rate:pct%", Map.of("rate", 0.25));

        assertEquals("raw=0.25 pct=25", def.formattedDescription());
    }

    @Test
    void unknownPlaceholderFallsBackToZeroRatherThanThrowing() {
        AbilityDefinition def = withStats("Deals %nonexistent% damage.", Map.of());

        assertEquals("Deals 0 damage.", def.formattedDescription());
    }

    @Test
    void detailsResolvePlaceholdersToo_andAreEmptyWhenTheJsonOmitsThem() {
        AbilityDefinition withDetails = new AbilityDefinition("Test", "active", "Short.",
            Map.of("radius", 2.0), List.of(), List.of("Hits everything within %radius% tiles."));

        assertEquals(List.of("Hits everything within 2 tiles."), withDetails.formattedDetails());
        assertEquals(List.of(), withStats("Short.", Map.of()).formattedDetails());
    }

    /**
     * The silent-failure guard: a placeholder naming a key that isn't in stats{}
     * renders as "0" with no error anywhere, so a typo in a hand-written description
     * only ever surfaces as a wrong number in a tooltip. This walks the real
     * design_ideas/ content so any such typo fails the build instead.
     *
     * Details are checked alongside descriptions: they are the same hand-written text with
     * the same placeholders, just shown on request rather than up front.
     */
    @Test
    void everyPlaceholderInEveryRealAbilityJsonResolvesToAnActualStatKey() {
        Pattern placeholder = Pattern.compile("%([a-zA-Z_]+)(:pct)?%");
        Map<String, AbilityDefinition> abilities =
            new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllAbilities();

        assertTrue(abilities.size() > 30, "expected the real ability set to be loaded, got " + abilities.size());

        List<String> problems = new ArrayList<>();
        abilities.forEach((id, def) -> {
            List<String> texts = new ArrayList<>();
            if (def.description() != null) {
                texts.add(def.description());
            }
            if (def.details() != null) {
                texts.addAll(def.details());
            }
            for (String text : texts) {
                Matcher matcher = placeholder.matcher(text);
                while (matcher.find()) {
                    String key = matcher.group(1);
                    if (def.stats() == null || !def.stats().containsKey(key)) {
                        problems.add(id + ".json references %" + key + "% but has no such stats{} key");
                    }
                }
            }
        });

        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }
}
