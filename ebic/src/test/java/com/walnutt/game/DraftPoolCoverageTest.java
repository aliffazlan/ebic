package com.walnutt.game;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.data.UnitDefinition;

/**
 * Exhaustive check that every draftable hero's kit is actually wired up.
 *
 * This used to be asserted inside FullDraftMatchTest, over whichever heroes a random
 * draft happened to reveal - which quietly stopped covering the whole pool once the
 * pool grew bigger than one draft consumes (5 champions for 4 slots, 14 elites for
 * 12), and made that test fail only some of the time. Checking the pool directly is
 * both deterministic and strictly broader.
 */
class DraftPoolCoverageTest {

    @Test
    void everyDraftableHeroHasAtLeastOneImplementedAbility() {
        JsonDataLoader loader = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());
        Map<String, UnitDefinition> unitDefs = loader.loadAllUnits();
        Map<String, AbilityDefinition> abilityDefs = loader.loadAllAbilities();
        DraftService draftService = new DraftService();

        List<String> draftable = new ArrayList<>();
        draftable.addAll(draftService.getAvailableChampions(unitDefs));
        draftable.addAll(draftService.getAvailableElites(unitDefs));

        List<String> unwired = new ArrayList<>();
        for (String id : draftable) {
            UnitDefinition def = unitDefs.get(id);
            boolean anyImplemented = def.abilities().stream()
                .anyMatch(a -> abilityDefs.containsKey(a) && AbilityFactory.isImplemented(a));
            if (!anyImplemented) {
                unwired.add(id + " " + def.abilities());
            }
        }

        assertTrue(unwired.isEmpty(),
            "draftable heroes with no implemented ability (they'd enter a match as Move+Attack shells): " + unwired);
    }

    /**
     * Every ability id a draftable hero lists must at least exist as a JSON file -
     * a typo here fails silently (UnitFactory just skips the id), which has bitten
     * this repo before ("obliviion_confinement").
     */
    @Test
    void everyAbilityIdListedByADraftableHeroResolvesToADefinition() {
        JsonDataLoader loader = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());
        Map<String, UnitDefinition> unitDefs = loader.loadAllUnits();
        Map<String, AbilityDefinition> abilityDefs = loader.loadAllAbilities();
        DraftService draftService = new DraftService();

        List<String> missing = new ArrayList<>();
        List<String> draftable = new ArrayList<>();
        draftable.addAll(draftService.getAvailableChampions(unitDefs));
        draftable.addAll(draftService.getAvailableElites(unitDefs));

        for (String id : draftable) {
            for (String abilityId : unitDefs.get(id).abilities()) {
                if (!abilityDefs.containsKey(abilityId)) {
                    missing.add(id + " -> " + abilityId);
                }
            }
        }

        assertTrue(missing.isEmpty(), "ability ids with no matching JSON file: " + missing);
    }
}
