package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;

/** Mercurial's Manifestation debuff: silenced + reduced damage output. */
public class ManifestationDebuffEffect extends Effect {
    private final double damageReduction;

    public ManifestationDebuffEffect(int duration, double damageReduction) {
        super("Manifestation", duration);
        this.damageReduction = damageReduction;
        this.flags.add(StatusFlag.SILENCED);
        this.category = EffectCategory.DEBUFF;
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        if (getOwner() == null || isExpired() || event.getSource() != getOwner()) {
            return;
        }
        event.multiplyDamage(1 - damageReduction);
    }
}
