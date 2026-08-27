package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * The damage-dealing half of Spitter's kit: deals damage_per_turn_remaining * turns-left
 * at the start of the poisoned unit's controller's turn.
 *
 * Poison Bloom is a separate effect that feeds this one (see PoisonBloomEffect) rather
 * than a kind of poison in its own right - this class is the only thing that ever deals
 * poison damage.
 */
public class PoisonEffect extends Effect {
    private final Unit source;
    private final int damagePerTurnRemaining;

    public PoisonEffect(Unit source, int duration, int damagePerTurnRemaining) {
        super("Poison",
            "Deals poison damage at the start of each of the target's turns, scaling with the turns "
                + "still remaining on the debuff (" + damagePerTurnRemaining + " damage x turns left). "
                + "Reapplying while already poisoned extends the duration instead of stacking a new "
                + "instance.",
            duration);
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

    /**
     * Adds `stacks` of net growth, for something that is actively feeding this poison
     * (Poison Bloom).
     *
     * It adds one more than asked because the poison also takes its own decay tick during
     * the same Unit.endTurn pass. The order of the two within that pass isn't guaranteed,
     * but both always run, so the net gain is exactly `stacks`.
     */
    public void feed(int stacks) {
        if (stacks > 0) {
            extendDuration(stacks + 1);
        }
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

    /** First active poison on this unit, if any. */
    public static PoisonEffect on(Unit unit) {
        return unit.getActiveEffect(PoisonEffect.class).orElse(null);
    }

    /** If the target is already poisoned, stack duration onto the existing instance instead of refreshing it. */
    public static void applyOrExtend(Unit target, Unit source, int duration, int damagePerTurn) {
        PoisonEffect existing = on(target);
        if (existing != null) {
            existing.extendDuration(duration);
        } else {
            target.addEffect(new PoisonEffect(source, duration, damagePerTurn));
        }
    }
}
