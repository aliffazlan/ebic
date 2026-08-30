package com.walnutt.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Loads the real design_ideas/ content shipped in this repo. */
class JsonDataLoaderTest {

    @Test
    void loadsRealUnitAndAbilityDefinitions_recursingIntoUnitSubdirectories() {
        JsonDataLoader loader = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());

        Map<String, UnitDefinition> units = loader.loadAllUnits();
        Map<String, AbilityDefinition> abilities = loader.loadAllAbilities();

        // units/ is now split into champion/ and elite/ subdirectories - loadAllUnits()
        // must recurse into both rather than assuming a flat directory.
        assertTrue(units.containsKey("valor"), "champion/valor.json");
        assertTrue(units.containsKey("dirge"), "elite/dirge.json");
        assertTrue(units.containsKey("zenith"), "champion/zenith.json - finished, no longer a blank placeholder");
        assertTrue(units.containsKey("yuki_golem"), "units/other/yuki_golem.json - a summon prototype, not draftable, but still a valid UnitDefinition");
        assertTrue(units.containsKey("zenith_pylon"), "units/other/zenith_pylon.json - also not draftable, still loaded");
        assertTrue(units.containsKey("shawl"), "elite/shawl.json - draftable as of v0.3.0");
        // The loader keys on filename and walks units/ recursively, so moving the summon
        // prototypes into units/other/ changed none of the ids above.

        assertTrue(abilities.containsKey("soul_rip"));
        assertTrue(abilities.containsKey("backstab"));
        assertTrue(abilities.get("backstab").isPassive());
        assertFalse(abilities.get("soul_rip").isPassive());
    }
}
