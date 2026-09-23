package com.walnutt.effect.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.impl.Mimic;
import com.walnutt.effect.Effect;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;

/**
 * How long Joker keeps an ability he copied. When this runs out the copy is unequipped -
 * though Mimic keeps the instance, cooldown and all, in case he steals it again later.
 *
 * A BUFF on purpose, with a real consequence: Chronos's Dilation ticks buffs twice as
 * fast, so standing in his field halves how long a stolen ability is held. That is a fair
 * counter to a thief, and dodging it by declaring this NEUTRAL would be pretending the
 * copy is not a benefit.
 */
public class MimicEffect extends Effect {
    private final Mimic mimic;
    private final Ability copy;

    public MimicEffect(String name, Mimic mimic, Ability copy, int duration) {
        super(name, "Wielding an ability copied from an enemy.", duration);
        this.mimic = mimic;
        this.copy = copy;
        this.category = EffectCategory.BUFF;
    }

    public Ability getCopy() {
        return copy;
    }

    @Override
    public void onExpire(GameState state) {
        // No-op if a newer copy has already replaced this one - Mimic checks identity.
        mimic.releaseIfCurrent(copy);
    }

    @Override
    public String getExtraInfo() {
        String cooldown = copy.isReady()
            ? "ready"
            : "cooldown " + copy.getCurrentCooldown();
        return "Copied: " + copy.getName() + " (" + cooldown + ")";
    }

    /** Hands the copied ability back, exactly as a natural expiry does. */
    @Override
    public void onStripped(GameState state) {
        onExpire(state);
    }
}
