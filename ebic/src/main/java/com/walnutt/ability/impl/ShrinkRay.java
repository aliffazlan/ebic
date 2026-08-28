package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.ShrinkRayEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Maxwell gadget - shrinks an enemy's attributes and health for a few turns. */
public class ShrinkRay extends Ability {
    private final int duration;
    private final double statReduction;
    private final double hpReduction;

    public ShrinkRay(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 6));
        setRange(definition.getInt("cast_range", 2));
        this.duration = definition.getInt("duration", 4);
        this.statReduction = definition.getDouble("stat_reduction", 0.2);
        this.hpReduction = definition.getDouble("hp_reduction", 0.1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit enemy = unitTarget.getUnit();
        return !enemy.isDead() && enemy.getTeam() != owner.getTeam() && isInRange(state, enemy.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit enemy = ((UnitTarget) target).getUnit();
        ShrinkRayEffect.applyOrStack(enemy, duration, statReduction, hpReduction);

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
