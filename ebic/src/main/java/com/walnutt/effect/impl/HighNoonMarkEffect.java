package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Flint's High Noon - a bounty on one enemy's head that only the gunslinger who placed it can collect. */
public class HighNoonMarkEffect extends Effect {
    private final Unit source;
    private final double critMultiplier;

    public HighNoonMarkEffect(Unit source, int duration, double critMultiplier) {
        super("High Noon Mark",
            source.getName() + " has this unit marked - the next hit from them lands free and hard.",
            duration);
        this.source = source;
        this.critMultiplier = critMultiplier;
    }

    public Unit getSource() {
        return source;
    }

    @Override
    public boolean grantsFreeAttackFrom(Unit attacker) {
        return !isExpired() && attacker == source;
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        // EventBus broadcasts every DamageEvent to every unit's effects, not just the one it's
        // aimed at - event.getTarget() must be checked too, or this would wrongly consume
        // itself off any damage the source deals to ANYONE, not just to this mark's own owner.
        if (isExpired() || event.getTarget() != getOwner() || event.getSource() != source || event.getDamage() <= 0) {
            return;
        }
        event.multiplyDamage(critMultiplier);
        expireNow(state);
    }
}
