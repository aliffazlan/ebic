package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.SteadyFocusEffect;
import com.walnutt.game.GameState;

/** Artemis - trades mobility and close-range defence for reach. */
public class SteadyFocus extends Ability {
    private final int duration;
    private final int bonusRange;
    private final int minRange;

    public SteadyFocus(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 5));
        this.duration = definition.getInt("duration", 2);
        this.bonusRange = definition.getInt("bonus_range", 3);
        this.minRange = definition.getInt("min_range", 3);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return super.canUse(state, target) && target instanceof NoTarget;
    }

    @Override
    public void onUse(GameState state, Target target) {
        owner.addEffect(new SteadyFocusEffect(duration, bonusRange, minRange));
        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
