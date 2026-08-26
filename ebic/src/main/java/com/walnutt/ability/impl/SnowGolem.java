package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitFactory;

/**
 * Yuki - summons a player-controlled Snow Golem (independent HP, built straight
 * from yuki_golem.json via UnitFactory, same as a drafted unit). Only one golem
 * may exist at a time: recasting instantly kills the previous one first.
 */
public class SnowGolem extends Ability {
    private Unit activeGolem;

    public SnowGolem(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 16));
        setRange(definition.getInt("cast_range", 1));
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        Tile tile = tileTarget.getTile();
        return tile.isWalkable() && isInRange(state, tile.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        if (activeGolem != null && !activeGolem.isDead()) {
            activeGolem.instantKill(state, owner);
        }

        UnitDefinition golemDefinition = state.getUnitDefinitions().get("yuki_golem");
        Unit golem = UnitFactory.createFromDefinition(golemDefinition, owner.getTeam(), state.getAbilityDefinitions());

        Tile destination = ((TileTarget) target).getTile();
        state.getMap().moveUnit(golem, destination);
        state.getPlayer(owner.getTeam()).addUnit(golem);
        activeGolem = golem;

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
