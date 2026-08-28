package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.data.JsonDataLoader;

/**
 * Eureka's gadget pool is declared in Java rather than in maxwell.json - these abilities
 * are not part of his starting kit, so UnitFactory must not see them. That means nothing
 * else in the build checks them: a typo in GADGET_IDS, a missing JSON file, or an
 * AbilityFactory entry someone forgot would all fail silently, and the gadget would
 * simply never be offered. This is the only thing standing between that and a player
 * wondering where an ability went.
 */
class MaxwellGadgetPoolTest {

    @Test
    void everyGadgetIdResolvesToRealJsonAndAWiredImplementation() {
        Map<String, AbilityDefinition> abilityDefs =
            new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllAbilities();

        List<String> problems = new ArrayList<>();
        for (String id : Eureka.GADGET_IDS) {
            if (!abilityDefs.containsKey(id)) {
                problems.add(id + " has no design_ideas/abilities/**/" + id + ".json");
            } else if (!AbilityFactory.isImplemented(id)) {
                problems.add(id + " has JSON but no AbilityFactory entry");
            }
        }

        assertTrue(problems.isEmpty(), "unbuildable gadgets: " + String.join("\n", problems));
    }

    @Test
    void theGadgetListHasNoDuplicates() {
        assertEquals(Eureka.GADGET_IDS.size(), new HashSet<>(Eureka.GADGET_IDS).size(),
            "a duplicate would be offered twice and could be built twice: " + Eureka.GADGET_IDS);
    }

    /**
     * The gadgets deliberately belong to no unit's JSON kit. If one ever appears in a
     * unit definition it would be granted at draft time, which is exactly what Eureka
     * exists to prevent.
     */
    @Test
    void noUnitDefinitionListsAGadgetInItsStartingKit() {
        List<String> leaked = new ArrayList<>();
        new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllUnits()
            .forEach((unitId, definition) -> definition.abilities().stream()
                .filter(Eureka.GADGET_IDS::contains)
                .forEach(gadget -> leaked.add(unitId + " starts with " + gadget)));

        assertTrue(leaked.isEmpty(), "gadgets must only ever arrive through Eureka: " + leaked);
    }
}
