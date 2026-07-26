package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.DilationEffect;
import com.walnutt.game.GameState;

/** Chronos - a self-centered field that pauses adjacent enemies' cooldowns and skews their effect tick rates. */
public class Dilation extends Ability {
    private final int duration;
    private final int radius;

    public Dilation(AbilityDefinition definition) {
        super(definition.name(), definition.description(), false);
        setMaxCooldown(definition.getInt("cooldown", 7));
        this.duration = definition.getInt("duration", 4);
        this.radius = definition.getInt("range", 1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return super.canUse(state, target)
            && state.canSpendMoves(getMoveCost(state))
            && target instanceof NoTarget;
    }

    @Override
    public void onUse(GameState state, Target target) {
        owner.addEffect(new DilationEffect(duration, radius));
        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
