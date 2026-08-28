package com.walnutt.web;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.JsonDataLoader;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.DraftService;
import com.walnutt.web.dto.UnitDefinitionSnapshot;

/**
 * The draftable roster, loaded from design_ideas/ once for the whole server.
 *
 * Game's match factories each build their own JsonDataLoader per match, which is fine at
 * one load per match but not at one per HTTP request - and both things this serves (the
 * unit info page, and validating a favourite unit) are pure reads of static design data.
 * Keeping it in one place is also what stops "what a player may set as a favourite" and
 * "what the info page lists" from drifting apart from each other, or from
 * DraftService.EXCLUDED.
 */
public final class UnitCatalog {
    private final Map<String, UnitDefinition> unitDefinitions;
    private final Map<String, AbilityDefinition> abilityDefinitions;
    private final List<String> draftableIds;
    private final Set<String> draftableIdSet;

    public UnitCatalog() {
        JsonDataLoader loader = new JsonDataLoader(JsonDataLoader.locateDesignIdeasRoot());
        this.unitDefinitions = loader.loadAllUnits();
        this.abilityDefinitions = loader.loadAllAbilities();

        DraftService draftService = new DraftService();
        List<String> ids = new ArrayList<>();
        ids.addAll(draftService.getAvailableChampions(unitDefinitions));
        ids.addAll(draftService.getAvailableElites(unitDefinitions));
        this.draftableIds = List.copyOf(ids);
        this.draftableIdSet = new LinkedHashSet<>(ids);
    }

    /** Champions first, then elites, each alphabetically - the order DraftService returns. */
    public List<UnitDefinitionSnapshot> draftableUnits() {
        List<UnitDefinitionSnapshot> snapshots = new ArrayList<>(draftableIds.size());
        for (String id : draftableIds) {
            snapshots.add(GameStateSnapshotMapper.toDefinitionSnapshot(unitDefinitions.get(id), abilityDefinitions));
        }
        return snapshots;
    }

    /**
     * Whether an id names a hero a player could actually be dealt. Summon prototypes and
     * designed-but-unimplemented heroes are excluded, because DraftService excludes them.
     */
    public boolean isDraftable(String definitionId) {
        return definitionId != null && draftableIdSet.contains(definitionId);
    }
}
