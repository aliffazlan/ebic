package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BlizzardEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Yuki - roots and damages a unit over time; reapplying stacks the duration instead of refreshing it. */
public class Blizzard extends Ability {
    private final int duration;
    private final int damage;

    public Blizzard(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 4));
        setRange(definition.getInt("cast_range", 3));
        this.duration = definition.getInt("duration", 2);
        this.damage = definition.getInt("damage", 25);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit other = unitTarget.getUnit();
        return !other.isDead() && isInRange(state, other.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();
        BlizzardEffect.applyOrExtend(other, owner, duration, damage);

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
