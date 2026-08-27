package com.walnutt.effect.impl;

import com.walnutt.effect.StatusEffect;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;

/**
 * The stun Chronos's Timeless Strike leaves behind. A named subclass (rather than a
 * bare StatusEffect) exists purely so repeat procs in one chain can find the existing
 * instance via getActiveEffect and extend it, instead of piling up several overlapping
 * one-turn stuns that look like a longer stun but aren't.
 */
public class TimelessStrikeStunEffect extends StatusEffect {

    public TimelessStrikeStunEffect(int duration) {
        super("Timeless Strike", duration, EffectCategory.DEBUFF, StatusFlag.STUNNED);
    }
}
