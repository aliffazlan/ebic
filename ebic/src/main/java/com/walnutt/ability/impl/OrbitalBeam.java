package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;

/** Zenith - flat-damage ranged beam; can target a unit or an empty tile (a "miss" cast, e.g. to trigger Pylons). */
public class OrbitalBeam extends Ability {
    private final int damage;

    public OrbitalBeam(AbilityDefinition definition) {
        super(definition.name(), definition.description(), false);
        setMaxCooldown(definition.getInt("cooldown", 2));
        setRange(definition.getInt("cast_range", 5));
        this.damage = definition.getInt("damage", 40);
    }

    public int getDamage() {
        return damage;
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !state.canSpendMoves(getMoveCost(state))) {
            return false;
        }
        Position targetPosition = resolvePosition(target);
        return targetPosition != null && state.getMap().getDistance(owner.getPosition(), targetPosition) <= getRange();
    }

    @Override
    public void onUse(GameState state, Target target) {
        if (target instanceof UnitTarget unitTarget) {
            Unit victim = unitTarget.getUnit();
            if (!victim.isDead()) {
                victim.takeDamage(state, new DamageEvent(owner, victim, damage));
            }
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    private Position resolvePosition(Target target) {
        if (target instanceof UnitTarget unitTarget) {
            return unitTarget.getUnit().getPosition();
        }
        if (target instanceof TileTarget tileTarget) {
            return tileTarget.getTile().getPosition();
        }
        return null;
    }
}
