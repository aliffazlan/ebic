package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.unit.HealthPool;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * Zenith - calls down a Pylon anywhere on the map (no range limit). Pylons are
 * auto-piloted (never player-selectable, just a registry entry for
 * targeting/events) and don't occupy their tile, but are fully attackable.
 */
public class PylonAbility extends Ability {
    private final int deathDamage;
    private final int deathDuration;

    public PylonAbility(AbilityDefinition definition) {
        super(definition.name(), definition.description(), false);
        setMaxCooldown(definition.getInt("cooldown", 4));
        this.deathDamage = definition.getInt("death_damage", 50);
        this.deathDuration = definition.getInt("death_duration", 1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return super.canUse(state, target)
            && state.canSpendMoves(getMoveCost(state))
            && target instanceof TileTarget;
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile destination = ((TileTarget) target).getTile();
        UnitDefinition pylonDefinition = state.getUnitDefinitions().get("zenith_pylon");
        UnitStats stats = new UnitStats(pylonDefinition.strength(), pylonDefinition.agility(),
            pylonDefinition.intelligence(), pylonDefinition.maxHp());

        Unit pylon = new SummonedUnit(pylonDefinition.name(), owner.getTeam(), stats,
            new HealthPool(pylonDefinition.maxHp()), owner, false, false);

        for (String abilityId : pylonDefinition.abilities()) {
            AbilityDefinition abilityDef = state.getAbilityDefinitions().get(abilityId);
            if (abilityDef != null && AbilityFactory.isImplemented(abilityId)) {
                pylon.addAbility(AbilityFactory.create(abilityId, abilityDef));
            }
        }
        pylon.addAbility(new PylonDeathBurst(deathDamage, deathDuration));

        state.getMap().moveUnit(pylon, destination);
        state.registerSummon(pylon);

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
