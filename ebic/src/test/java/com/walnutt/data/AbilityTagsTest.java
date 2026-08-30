package com.walnutt.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Build guard over the no_copy tag in the real design_ideas/ JSON, playing the same role
 * MaxwellGadgetPoolTest plays for Eureka's gadget list: nothing else would notice if an
 * edit dropped one. A missing tag is silent and dangerous - Joker's Mimic would simply
 * start copying an ability whose machinery assumes it never leaves the unit it was built
 * for, and the damage shows up as a confusing bug several turns later.
 */
class AbilityTagsTest {

    /**
     * Every ability that must never be copied, and the concrete reason:
     *
     * - psychic_projection: its effect calls Player.removeUnit on the clone (the one
     *   documented source of the mid-iteration CME), and a Joker copy would clone a
     *   champion rather than Lanaya.
     * - eureka: onAttached creates a permanent Inspiration pool that outlives the copy,
     *   and any gadget it builds lands in getAbilities() where Mimic never tracks it, so
     *   it would stay on the thief forever.
     * - hidden_potential: Shawl's unbuilt kit is the same currency-plus-dialogue shape as
     *   Eureka; tagged now so the trap is already shut when he lands.
     * - mimic: copying the copy ability with itself is degenerate.
     */
    private static final Set<String> MUST_NOT_BE_COPYABLE =
        Set.of("psychic_projection", "eureka", "hidden_potential", "mimic");

    private static Map<String, AbilityDefinition> abilities() {
        return new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllAbilities();
    }

    @Test
    void everyAbilityThatBreaksWhenCopiedCarriesTheNoCopyTag() {
        Map<String, AbilityDefinition> abilities = abilities();
        List<String> untagged = new ArrayList<>();
        for (String id : MUST_NOT_BE_COPYABLE) {
            AbilityDefinition definition = abilities.get(id);
            assertTrue(definition != null, id + ".json is missing from design_ideas/");
            if (!definition.hasTag(AbilityDefinition.NO_COPY)) {
                untagged.add(id);
            }
        }
        assertEquals(List.of(), untagged, "these must carry the no_copy tag - see this test's javadoc");
    }

    /**
     * The other direction: the tag is a deliberate exception, so it should not be
     * spreading. A new entry here is fine, but it should arrive alongside its reason in
     * MUST_NOT_BE_COPYABLE rather than quietly.
     */
    @Test
    void nothingElseIsTaggedNoCopy() {
        List<String> unexpected = new ArrayList<>();
        for (Map.Entry<String, AbilityDefinition> entry : abilities().entrySet()) {
            if (entry.getValue().hasTag(AbilityDefinition.NO_COPY)
                && !MUST_NOT_BE_COPYABLE.contains(entry.getKey())) {
                unexpected.add(entry.getKey());
            }
        }
        assertEquals(List.of(), unexpected);
    }

    /** Tags are optional, and Gson leaves an absent "tags" key null - hasTag must cope. */
    @Test
    void anUntaggedDefinitionIsSimplyNotTagged() {
        AbilityDefinition noTagsKey = new AbilityDefinition("X", "active", "desc", Map.of(), null);
        AbilityDefinition legacyConstructor = new AbilityDefinition("X", "active", "desc", Map.of());

        assertFalse(noTagsKey.hasTag(AbilityDefinition.NO_COPY));
        assertFalse(legacyConstructor.hasTag(AbilityDefinition.NO_COPY));
    }

    /** Real content check: an ordinary ability stays copyable. */
    @Test
    void ordinaryAbilitiesAreCopyable() {
        Map<String, AbilityDefinition> abilities = abilities();

        assertFalse(abilities.get("fireblast").hasTag(AbilityDefinition.NO_COPY));
        assertFalse(abilities.get("soul_rip").hasTag(AbilityDefinition.NO_COPY));
    }

    /**
     * The no_upgrade counterpart. An ability with no upgrade block is already un-upgradeable,
     * so the tag adds no rule - it says the omission is deliberate rather than unfinished,
     * which is the difference between "a Branchling's aura has no second form" and "nobody has
     * written Static Link's yet".
     */
    private static final Set<String> MUST_NOT_BE_UPGRADEABLE =
        Set.of("branchling_aura", "blizzard_fist", "snow_blast", "pylon_beam", "recall", "burn");

    @Test
    void summonKitsAndEffectDefinitionsCarryTheNoUpgradeTagAndNoUpgradeBlock() {
        Map<String, AbilityDefinition> abilities = abilities();
        List<String> problems = new ArrayList<>();
        for (String id : MUST_NOT_BE_UPGRADEABLE) {
            AbilityDefinition definition = abilities.get(id);
            assertTrue(definition != null, id + ".json is missing from design_ideas/");
            if (!definition.hasTag(AbilityDefinition.NO_UPGRADE)) {
                problems.add(id + " is missing the no_upgrade tag");
            }
            if (definition.upgrade() != null) {
                problems.add(id + " carries an upgrade block it should not have");
            }
        }
        assertEquals(List.of(), problems, "see this test's javadoc");
    }

    /**
     * The other direction, and the one that actually catches a mistake: anything a hero
     * brings to the field is meant to have a designed upgrade, so a missing block is an
     * oversight rather than a decision. The tag is how a deliberate exception opts out.
     */
    @Test
    void everyOtherAbilityHasAnUpgradeDesignedForIt() {
        List<String> missing = new ArrayList<>();
        abilities().forEach((id, definition) -> {
            if (!definition.hasTag(AbilityDefinition.NO_UPGRADE) && definition.upgrade() == null) {
                missing.add(id);
            }
        });
        assertEquals(List.of(), missing,
            "these have neither an upgrade nor a no_upgrade tag - one or the other is required");
    }
}
