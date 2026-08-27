package com.walnutt.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.web.Identifiers;

class AbilityHintsTest {

    /**
     * A hint registered under an id no ability actually has is invisible: lookups simply
     * fall through to the generic fallback and the bot quietly plays that ability without
     * the knowledge someone wrote for it. Nothing else in the system would ever report it,
     * so this test is the only thing standing between a typo and silently dead content.
     */
    @Test
    void everyRegisteredHintMatchesARealAbility() {
        JsonDataLoader loader = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());
        Map<String, AbilityDefinition> definitions = loader.loadAllAbilities();

        Set<String> realIds = definitions.values().stream()
            .map(definition -> Identifiers.normalize(definition.name()))
            .collect(Collectors.toSet());

        Set<String> unmatched = AbilityHints.registeredIds().stream()
            .filter(id -> !realIds.contains(id))
            .collect(Collectors.toSet());

        assertTrue(unmatched.isEmpty(),
            "hints registered under ids no ability has: " + unmatched
                + " - note the key is the normalized ability NAME, not its JSON filename");
    }

    /** An ability nobody has taught the bot about must still be playable, not rejected. */
    @Test
    void anUnknownAbilityFallsBackToTheGenericHint() {
        assertSame(AbilityHints.GENERIC, AbilityHints.forAbility("Some Ability That Does Not Exist"));
        assertSame(AbilityHints.GENERIC, AbilityHints.forAbility(null));
    }

    @Test
    void aHintedAbilityResolvesToItsOwnHint() {
        assertEquals(false, AbilityHints.GENERIC == AbilityHints.forAbility("Doom"),
            "Doom should resolve to its own hint, not the fallback");
        assertEquals(false, AbilityHints.GENERIC == AbilityHints.forAbility("Blizzard"),
            "Blizzard needs its own hint - it can legally be cast on your own units");
    }
}
