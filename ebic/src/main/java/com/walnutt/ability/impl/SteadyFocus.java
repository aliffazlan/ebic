package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.SteadyFocusEffect;
import com.walnutt.game.GameState;

/** Artemis - trades mobility and close-range defence for reach. */
public class SteadyFocus extends Ability {
    private int duration;
    private int bonusRange;
    private int minRange;

    public SteadyFocus(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 5));
        this.duration = definition.getInt("duration", 2);
        this.bonusRange = definition.getInt("bonus_range", 3);
        this.minRange = definition.getInt("min_range", 3);
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.bonusRange = statInt("bonus_range", bonusRange);
        this.minRange = statInt("min_range", minRange);
    }

    /** True while the upgraded form is holding its aim - the only state a toggle needs. */
    public boolean isAiming() {
        return owner != null && owner.getActiveEffect(SteadyFocusEffect.class).isPresent();
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return super.canUse(state, target) && target instanceof NoTarget;
    }

    @Override
    public void onUse(GameState state, Target target) {
        // Upgraded, this is a toggle: casting it while the aim is held lowers it instead of
        // raising a second one. Either way it goes on cooldown, so a stance is locked in for a
        // couple of turns whichever direction it was changed.
        if (isUpgraded()) {
            if (isAiming()) {
                owner.getActiveEffect(SteadyFocusEffect.class).ifPresent(aim -> {
                    aim.setRemainingTurns(0);
                    owner.removeExpiredEffects(state);
                });
            } else {
                // PERMANENT rather than a duration: it ends when the player says so, or when it
                // is dispelled - not on a clock.
                owner.addEffect(new SteadyFocusEffect(Effect.PERMANENT, bonusRange, minRange));
            }
        } else {
            owner.addEffect(new SteadyFocusEffect(duration, bonusRange, minRange));
        }
        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
