package com.walnutt.game;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitFactory;

/**
 * Handles the pre-match pick phase: each player drafts 1 champion + 3 elites from
 * the shared named-hero pool (basics are generic, not drafted). Picking itself is
 * trivial/scripted for this MVP - a future InputHandler-driven pick UI would just
 * call the same draftPlayer(...) with player-chosen ids instead of the defaults.
 */
public class DraftService {
    /**
     * Not draftable heroes: yuki_golem/zenith_pylon are summon prototypes loaded as
     * UnitDefinitions purely so their kits can be constructed the same way a
     * drafted unit's is, not actual pickable champions/elites. "vex" is kept here
     * defensively in case an unfinished vex.json (a past duplicate of valor.json)
     * ever reappears - it doesn't exist in design_ideas/ right now.
     */
    private static final Set<String> EXCLUDED = Set.of("vex", "yuki_golem", "zenith_pylon");

    public List<String> getAvailableChampions(Map<String, UnitDefinition> unitDefs) {
        return unitDefs.entrySet().stream()
            .filter(e -> !EXCLUDED.contains(e.getKey()))
            .filter(e -> "champion".equalsIgnoreCase(e.getValue().type()))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    }

    public List<String> getAvailableElites(Map<String, UnitDefinition> unitDefs) {
        return unitDefs.entrySet().stream()
            .filter(e -> !EXCLUDED.contains(e.getKey()))
            .filter(e -> "elite".equalsIgnoreCase(e.getValue().type()))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    }

    public Player draftPlayer(String playerName, Team team, String championId, List<String> eliteIds,
                               Map<String, UnitDefinition> unitDefs, Map<String, AbilityDefinition> abilityDefs,
                               int basicCount) {
        if (eliteIds.size() != 3) {
            throw new IllegalArgumentException("Must draft exactly 3 elites, got " + eliteIds.size());
        }

        Player player = new Player(playerName, team);
        player.addUnit(createDrafted(championId, team, unitDefs, abilityDefs));
        for (String eliteId : eliteIds) {
            player.addUnit(createDrafted(eliteId, team, unitDefs, abilityDefs));
        }
        for (int i = 1; i <= basicCount; i++) {
            player.addUnit(UnitFactory.createBasic(playerName + " Basic " + i, team));
        }
        return player;
    }

    /** Deterministic default pick, useful for smoke tests / scripted scenarios. */
    public Player draftDefaultPlayer(String playerName, Team team, boolean pickFromEnd,
                                      Map<String, UnitDefinition> unitDefs, Map<String, AbilityDefinition> abilityDefs,
                                      int basicCount) {
        List<String> champions = getAvailableChampions(unitDefs);
        List<String> elites = getAvailableElites(unitDefs);
        if (champions.isEmpty() || elites.size() < 3) {
            throw new IllegalStateException("Not enough units in the pool to draft a full roster");
        }

        String championId = pickFromEnd ? champions.get(champions.size() - 1) : champions.get(0);
        List<String> eliteIds = pickFromEnd
            ? elites.subList(elites.size() - 3, elites.size())
            : elites.subList(0, 3);

        return draftPlayer(playerName, team, championId, eliteIds, unitDefs, abilityDefs, basicCount);
    }

    private Unit createDrafted(String id, Team team, Map<String, UnitDefinition> unitDefs,
                                Map<String, AbilityDefinition> abilityDefs) {
        UnitDefinition def = unitDefs.get(id);
        if (def == null) {
            throw new IllegalArgumentException("No unit definition found for '" + id + "'");
        }
        return UnitFactory.createFromDefinition(def, team, abilityDefs);
    }
}
