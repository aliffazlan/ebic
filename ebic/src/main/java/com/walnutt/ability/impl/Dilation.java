package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.DilationEffect;
import com.walnutt.game.GameState;

/** Chronos - a self-centered field that pauses adjacent enemies' cooldowns and skews their effect tick rates. */
public class Dilation extends Ability {
    private int duration;
    private int radius;

    public Dilation(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 7));
        this.duration = definition.getInt("duration", 4);
        this.radius = definition.getInt("range", 1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return super.canUse(state, target) && target instanceof NoTarget;
    }

    @Override
    public void onUse(GameState state, Target target) {
        owner.addEffect(new DilationEffect(duration, radius));
        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    /**
     * Upgrade: the field stops being a spell and becomes something Chronos carries.
     *
     * Ability.canUse begins with !isPassive, and the base class flips that flag from the JSON's
     * upgrade.type - so nothing here has to refuse the cast; there simply is no cast any more.
     * All this does is put the field up permanently, once.
     */
    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.radius = statInt("range", radius);
        if (owner != null && owner.getActiveEffect(DilationEffect.class).isEmpty()) {
            owner.addEffect(new DilationEffect(Effect.PERMANENT, radius));
        }
    }
}
