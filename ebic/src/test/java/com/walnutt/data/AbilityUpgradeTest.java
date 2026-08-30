package com.walnutt.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;

/**
 * The shared half of the upgrade system: what Ability.upgrade re-applies on its own, and the
 * standing promise that every id in AbilityFactory's UPGRADE_IMPLEMENTED set actually does
 * something when upgraded.
 */
class AbilityUpgradeTest {

    private static Map<String, AbilityDefinition> realAbilities() {
        return new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot()).loadAllAbilities();
    }

    private static Ability build(String id) {
        AbilityDefinition definition = realAbilities().get(id);
        assertTrue(definition != null, id + ".json is missing from design_ideas/");
        return AbilityFactory.create(id, definition);
    }

    @Test
    void upgradingReAppliesTheTextAndStatsWithNoPerAbilityCode() {
        Ability plasmaCannon = build("plasma_cannon");
        assertEquals(60, plasmaCannon.getStats().get("damage").intValue());
        String before = plasmaCannon.getDescription();

        plasmaCannon.upgrade();

        assertTrue(plasmaCannon.isUpgraded());
        assertEquals(120, plasmaCannon.getStats().get("damage").intValue(),
            "merged stats, so the client's stat table shows the upgraded number too");
        assertNotEquals(before, plasmaCannon.getDescription(),
            "the base sentence re-renders against merged stats - 60 becomes 120");
        assertTrue(plasmaCannon.getDescription().contains("120"));
    }

    @Test
    void anUpgradeThatNamesCooldownOrRangeMovesThemWithoutTheAbilityKnowing() {
        Ability fireblast = build("fireblast");
        assertEquals(3, fireblast.getMaxCooldown());
        assertEquals(2, fireblast.getRange());

        fireblast.upgrade();

        assertEquals(2, fireblast.getMaxCooldown());
        assertEquals(3, fireblast.getRange());
    }

    @Test
    void anUpgradeThatNamesNeitherLeavesCooldownAndRangeAlone() {
        // Sprout is cast anywhere on the map, and its range comes from a constant rather than
        // from any stat - guessing a range key would silently break exactly this case.
        Ability sprout = build("sprout");
        assertEquals(Ability.UNLIMITED_RANGE, sprout.getRange());

        sprout.upgrade();

        assertEquals(1, sprout.getMaxCooldown(), "its upgrade does name cooldown");
        assertEquals(Ability.UNLIMITED_RANGE, sprout.getRange(), "but never touches the range");
    }

    @Test
    void aSecondUpgradeIsANoOpUnlessTheAbilityIsRepeatable() {
        Ability perplexingShot = build("perplexing_shot");
        perplexingShot.upgrade();
        assertEquals(1, perplexingShot.getUpgradeCount());
        assertFalse(perplexingShot.canUpgrade());

        perplexingShot.upgrade();

        assertEquals(1, perplexingShot.getUpgradeCount());
    }

    @Test
    void hiddenPotentialIsTheOneAbilityThatMayBeUpgradedTwice() {
        Ability hiddenPotential = build("hidden_potential");

        hiddenPotential.upgrade();
        assertTrue(hiddenPotential.canUpgrade(), "repeatable: true in hidden_potential.json");
        hiddenPotential.upgrade();

        assertEquals(2, hiddenPotential.getUpgradeCount());
    }

    @Test
    void anAbilityBuiltInJavaRatherThanFromJsonCannotBeUpgraded() {
        // Move and Attack carry no definition at all, which is what makes them un-upgradeable
        // with no list to maintain - the same trick that keeps them uncopyable by Mimic.
        for (Ability ability : List.of(new Move(), new Attack())) {
            assertFalse(ability.canUpgrade(), ability.getName() + " has no definition to upgrade from");
            ability.upgrade();
            assertFalse(ability.isUpgraded());
        }
    }

    @Test
    void anAbilityTaggedNoUpgradeIsNeverUpgradeable() {
        Ability pylonBeam = build("pylon_beam");

        assertFalse(pylonBeam.getDefinition().isUpgradeable());
        pylonBeam.upgrade();

        assertFalse(pylonBeam.isUpgraded());
    }

    /**
     * The guard behind AbilityFactory.UPGRADE_IMPLEMENTED: an id in that set must actually
     * have an upgrade to give, and where the upgrade moves numbers, those numbers must reach
     * the instance.
     *
     * What this deliberately does NOT claim is that the ability's own rules changed - many
     * upgrades are pure behaviour (Cripple stealing from a failed attack, Reload surviving an
     * ability cast) and move no numbers at all. There is no generic way to see that from out
     * here, so the per-ability tests carry it; this catches the cheaper mistakes, which are an
     * id added with no upgrade block behind it, or one whose stats never make it through.
     */
    @Test
    void everyImplementedUpgradeHasSomethingToGive() {
        Map<String, AbilityDefinition> abilities = realAbilities();
        List<String> problems = new ArrayList<>();

        for (String id : AbilityFactory.implementedUpgradeIds()) {
            AbilityDefinition definition = abilities.get(id);
            if (definition == null) {
                problems.add(id + " has no design_ideas/ definition at all");
                continue;
            }
            if (!definition.isUpgradeable()) {
                problems.add(id + " is listed as implemented but has no upgrade block");
                continue;
            }
            UpgradeDefinition upgrade = definition.upgrade();
            boolean saysSomething = !upgrade.statsOrEmpty().isEmpty()
                || upgrade.description() != null
                || (upgrade.details() != null && !upgrade.details().isEmpty())
                || upgrade.type() != null;
            if (!saysSomething) {
                problems.add(id + " has an upgrade block that changes nothing at all");
                continue;
            }
            Ability ability = AbilityFactory.create(id, definition);
            Map<String, Double> before = Map.copyOf(ability.getStats());
            ability.upgrade();
            if (!upgrade.statsOrEmpty().isEmpty() && before.equals(ability.getStats())) {
                problems.add(id + "'s upgrade names stats that never reached the instance");
            }
            if (!ability.isUpgraded()) {
                problems.add(id + " refused to upgrade at all");
            }
        }

        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    /** The other direction: an id can only be promised as implemented if it is registered at all. */
    @Test
    void everyImplementedUpgradeIdIsARealRegisteredAbility() {
        for (String id : AbilityFactory.implementedUpgradeIds()) {
            assertTrue(AbilityFactory.isImplemented(id), id + " has no AbilityFactory entry");
            assertTrue(AbilityFactory.isUpgradeImplemented(id));
        }
    }

    /**
     * The coverage promise: as of v0.3.0 every ability with a designed upgrade has that upgrade
     * built, so Shawl's dialogue offers all of them and its "Not yet available" state is
     * unreachable in the shipped roster.
     *
     * The disabled path is kept deliberately - a hero added later with an upgrade block and no
     * implementation must be withheld rather than sold half-working. This is what turns letting
     * one slip through into a build failure instead of a silent regression.
     */
    @Test
    void everyUpgradeDesignedInJsonIsActuallyImplemented() {
        List<String> unbuilt = new ArrayList<>();
        realAbilities().forEach((id, definition) -> {
            if (definition.isUpgradeable() && !AbilityFactory.isUpgradeImplemented(id)) {
                unbuilt.add(id);
            }
        });

        assertEquals(List.of(), unbuilt,
            "these have an upgrade block but no implementation - either build it, or accept that "
                + "Shawl's dialogue will list it greyed out");
    }
}
