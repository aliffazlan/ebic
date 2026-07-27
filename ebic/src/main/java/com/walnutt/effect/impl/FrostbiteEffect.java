package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/** Auroth's Frostbite debuff: no healing, and instantly shatters the host below the kill threshold. */
public class FrostbiteEffect extends Effect {
    private final Unit source;
    private final double killThreshold;

    public FrostbiteEffect(Unit source, int duration, double killThreshold) {
        super("Frostbite",
            "Blocks all healing on the target for the duration; if its health ever drops below "
                + Math.round(killThreshold * 100) + "% of max, it instantly shatters and dies.",
            duration);
        this.source = source;
        this.killThreshold = killThreshold;
        this.flags.add(StatusFlag.IMMUNE_TO_HEALING);
        this.category = EffectCategory.DEBUFF;
    }

    @Override
    public void onDamageTaken(GameState state, PostDamageEvent event) {
        Unit owner = getOwner();
        if (owner == null || owner.isDead() || event.damageEvent().getTarget() != owner) {
            return;
        }
        if (owner.getHealth() < killThreshold * owner.getMaxHealth()) {
            owner.instantKill(state, source);
        }
    }
}
