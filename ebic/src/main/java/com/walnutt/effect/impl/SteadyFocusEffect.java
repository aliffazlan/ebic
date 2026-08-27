package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.status.StatusFlag;

/**
 * Artemis taking aim: rooted, longer reach, and unable to shoot anything close.
 *
 * The bonus range rides on the ordinary stat-modifier machinery, but the minimum is its
 * own hook - minimums have to resolve to the strictest value rather than summing the way
 * StatModifiers do. NEUTRAL rather than BUFF so a Chronos dilation field doesn't tick the
 * root away faster, and so it isn't swept up by a debuff cleanse either.
 */
public class SteadyFocusEffect extends Effect {
    private final int minAttackRange;

    public SteadyFocusEffect(int duration, int bonusRange, int minAttackRange) {
        super("Steady Focus",
            "Rooted in place, with " + bonusRange + " bonus attack range - but unable to attack "
                + "anything closer than " + minAttackRange + " tiles.",
            duration);
        this.minAttackRange = minAttackRange;
        this.category = EffectCategory.NEUTRAL;
        this.flags.add(StatusFlag.ROOTED);
        this.modifiers.add(StatModifier.flat(Stat.ATTACK_RANGE, bonusRange, this));
    }

    @Override
    public int getMinAttackRange() {
        return minAttackRange;
    }

    @Override
    public String getExtraInfo() {
        return "Cannot attack within " + minAttackRange + " tiles";
    }
}
