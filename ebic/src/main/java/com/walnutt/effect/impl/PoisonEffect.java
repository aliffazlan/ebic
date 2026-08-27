package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/** Deals damage_per_turn_remaining * turns-left at the start of the poisoned unit's controller's turn. */
public class PoisonEffect extends Effect {
    private final Unit source;
    private final int damagePerTurnRemaining;

    public PoisonEffect(Unit source, int duration, int damagePerTurnRemaining) {
        this("Poison",
            "Deals poison damage at the start of each of the target's turns, scaling with the turns "
                + "still remaining on the debuff (" + damagePerTurnRemaining + " damage x turns left). "
                + "Reapplying while already poisoned extends the duration instead of stacking a new "
                + "instance.",
            source, duration, damagePerTurnRemaining);
    }

    /** Lets a subclass (Poison Bloom) present itself under its own name in the effects sidebar. */
    protected PoisonEffect(String name, String description, Unit source, int duration, int damagePerTurnRemaining) {
        super(name, description, duration);
        this.source = source;
        this.damagePerTurnRemaining = damagePerTurnRemaining;
        this.category = EffectCategory.DEBUFF;
    }

    public Unit getSource() {
        return source;
    }

    public int getDamagePerTurn() {
        return damagePerTurnRemaining;
    }

    @Override
    public String getExtraInfo() {
        int nextTick = damagePerTurnRemaining * getRemainingTurns();
        return "Next tick: " + nextTick + " damage";
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() == null || isExpired() || event.team() != getOwner().getTeam()) {
            return;
        }
        int damage = damagePerTurnRemaining * getRemainingTurns();
        if (damage <= 0) {
            return;
        }
        DamageEvent damageEvent = new DamageEvent(source, getOwner(), damage);
        damageEvent.setCauseLabel("Poison");
        getOwner().takeDamage(state, damageEvent);
    }

    /**
     * If the target is already poisoned, stack duration onto the existing instance instead
     * of refreshing it.
     *
     * A target already carrying a Poison Bloom is left completely alone: the bloom runs
     * its own caster-hit rule (duration_increase) from its onPostAttack hook, so extending
     * it here as well made one Spitter attack add both amounts, and adding a separate
     * poison beside it would put two near-identical chips on the same unit.
     */
    public static void applyOrExtend(Unit target, Unit source, int duration, int damagePerTurn) {
        PoisonEffect existing = null;
        for (Effect effect : target.getEffects()) {
            if (effect.isExpired() || !(effect instanceof PoisonEffect poison)) {
                continue;
            }
            if (poison instanceof BloomingPoisonEffect) {
                return;
            }
            if (existing == null) {
                existing = poison;
            }
        }
        if (existing != null) {
            existing.extendDuration(duration);
        } else {
            target.addEffect(new PoisonEffect(source, duration, damagePerTurn));
        }
    }
}
