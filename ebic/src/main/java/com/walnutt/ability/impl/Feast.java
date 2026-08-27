package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.FeastEffect;
import com.walnutt.game.GameState;

/**
 * Grivath - enters a feeding frenzy: no leap, no self-root, just a free automatic
 * attack each turn against an adjacent enemy, healing off the damage and rooting
 * whoever gets bitten.
 */
public class Feast extends Ability {
    private final int duration;
    private final int attacks;
    private final double lifesteal;
    private final int rootDuration;

    public Feast(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 7));
        this.duration = definition.getInt("duration", 3);
        this.attacks = definition.getInt("attacks", 1);
        this.lifesteal = definition.getDouble("lifesteal", 0.25);
        this.rootDuration = definition.getInt("root_duration", 1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return super.canUse(state, target) && target instanceof NoTarget;
    }

    @Override
    public void onUse(GameState state, Target target) {
        owner.addEffect(new FeastEffect(duration, attacks, lifesteal, rootDuration));
        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
