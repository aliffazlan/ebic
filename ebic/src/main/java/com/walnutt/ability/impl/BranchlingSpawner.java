package com.walnutt.ability.impl;

import java.util.List;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.data.UnitDefinition;
import com.walnutt.effect.impl.BranchiggaLifespanEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.unit.HealthPool;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitFactory;
import com.walnutt.unit.UnitStats;
import com.walnutt.unit.UnitType;

/**
 * Shared construction for Branch's Branchlings, used by both Overgrowth and Sprout.
 *
 * Built directly as a SummonedUnit rather than through UnitFactory, which is what makes
 * them inert: UnitFactory attaches Move and Attack to everything it builds, and a
 * Branchling is meant to be a body that cannot act at all. Following PylonAbility's
 * pattern, they're registered as summons rather than added to the player's roster, so
 * they never appear in anyone's unit-selection list.
 *
 * Unlike Pylons they DO occupy their tile - blocking movement is the whole point.
 */
final class BranchlingSpawner {
    private static final int FALLBACK_MAX_HP = 50;
    private static final String BRANCHIGGA_DEFINITION_ID = "branchigga";

    private BranchlingSpawner() {
    }

    /**
     * The Branchigga upgraded Overgrowth grows where a Branchling was killed.
     *
     * Everything a Branchling is not: it gets Move and Attack, and it joins the player's ROSTER
     * rather than the summon registry - it is a unit that acts, so it has to be selectable, and
     * the roster is also the only place its own lifespan effect will tick (see
     * SummonLifespanEffect). Its aura comes from branchigga.json, which lists the same one.
     */
    static Unit spawnBranchigga(GameState state, Unit summoner, Tile tile, int duration) {
        UnitDefinition definition = state.getUnitDefinitions().get(BRANCHIGGA_DEFINITION_ID);
        if (definition == null) {
            return null;
        }
        Unit branchigga = UnitFactory.createFromDefinition(definition, summoner.getTeam(),
            state.getAbilityDefinitions());
        branchigga.addEffect(new BranchiggaLifespanEffect(duration));

        state.getMap().moveUnit(branchigga, tile);
        state.getPlayer(summoner.getTeam()).addUnit(branchigga);
        state.recordSummoner(branchigga, summoner);
        return branchigga;
    }

    static Unit spawn(GameState state, Unit summoner, Tile tile) {
        UnitDefinition definition = state.getUnitDefinitions().get("branchling");
        UnitStats stats = definition == null
            ? new UnitStats(0, 0, 0, FALLBACK_MAX_HP, 1)
            : new UnitStats(definition.strength(), definition.agility(), definition.intelligence(),
                definition.maxHp(), definition.effectiveAttackRange());
        String name = definition == null ? "Branchling" : definition.name();
        // branchling.json says "basic" - take it from the prototype rather than
        // inheriting Branch's ELITE, which would make these worth farming.
        UnitType type = definition == null ? UnitType.BASIC : UnitFactory.parseType(definition.type());

        Unit branchling = new SummonedUnit(name, summoner.getTeam(), type, stats,
            new HealthPool(stats.maxHealth()), summoner, false, true);

        List<String> abilityIds = definition == null ? List.of() : definition.abilities();
        for (String abilityId : abilityIds) {
            AbilityDefinition abilityDef = state.getAbilityDefinitions().get(abilityId);
            if (abilityDef != null && AbilityFactory.isImplemented(abilityId)) {
                branchling.addAbility(AbilityFactory.create(abilityId, abilityDef));
            }
        }

        state.getMap().moveUnit(branchling, tile);
        state.registerSummon(branchling);
        return branchling;
    }

    /** "If a tile is occupied, a Branchling won't spawn there" - read literally, unlike isWalkable(). */
    static boolean canSpawnOn(Tile tile) {
        return tile != null && tile.isWalkable() && tile.getOccupants().isEmpty();
    }
}
