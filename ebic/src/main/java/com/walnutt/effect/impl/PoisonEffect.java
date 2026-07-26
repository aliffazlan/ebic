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
        super("Poison", duration);
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
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() == null || isExpired() || event.team() != getOwner().getTeam()) {
            return;
        }
        int damage = damagePerTurnRemaining * getRemainingTurns();
        if (damage <= 0) {
            return;
        }
        getOwner().takeDamage(state, new DamageEvent(source, getOwner(), damage));
    }

    /** If the target is already poisoned, stack duration onto the existing instance instead of refreshing it. */
    public static void applyOrExtend(Unit target, Unit source, int duration, int damagePerTurn) {
        for (Effect effect : target.getEffects()) {
            if (effect instanceof PoisonEffect existing) {
                existing.extendDuration(duration);
                return;
            }
        }
        target.addEffect(new PoisonEffect(source, duration, damagePerTurn));
    }
}
