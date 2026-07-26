package com.walnutt.effect;

import java.util.Arrays;

import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;

/** Convenience effect for the common case of "apply these flags for N turns", no subclass needed. */
public class StatusEffect extends Effect {
    public StatusEffect(String name, int duration, StatusFlag... statusFlags) {
        this(name, duration, EffectCategory.NEUTRAL, statusFlags);
    }

    public StatusEffect(String name, int duration, EffectCategory category, StatusFlag... statusFlags) {
        super(name, duration);
        this.flags.addAll(Arrays.asList(statusFlags));
        this.category = category;
    }
}
