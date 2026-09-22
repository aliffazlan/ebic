package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BloodwakeEffect;
import com.walnutt.game.GameState;

/** Noctis - a bloodlust that only grows the longer it's left to run. */
public class Bloodwake extends Ability {
    private int duration;
    private int bonusDamage;

    public Bloodwake(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 7));
        this.duration = definition.getInt("duration", 3);
        this.bonusDamage = definition.getInt("bonus_damage", 30);
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.bonusDamage = statInt("bonus_damage", bonusDamage);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return super.canUse(state, target) && target instanceof NoTarget;
    }

    @Override
    public void onUse(GameState state, Target target) {
        BloodwakeEffect.applyOrRefresh(owner, duration, bonusDamage, isUpgraded());
        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
