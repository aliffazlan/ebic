package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;

/**
 * Generic damage-absorption shield: incoming damage is subtracted from the
 * barrier's own HP pool first, and only the remainder (if any) reaches the
 * owner's real health. Subclasses can override onBarrierBroken(state) to react
 * the moment the barrier is fully depleted (Thaddeus's Holy Shield clears debuffs
 * and deals AOE damage; a plain barrier like Zenith's Dislocation does nothing extra).
 */
public class BarrierEffect extends Effect {
    private int remainingBarrierHp;
    private int maxBarrierHp;

    public BarrierEffect(String name, String description, int duration, int barrierHp) {
        this(name, description, duration, barrierHp, barrierHp);
    }

    /**
     * Lets a barrier start below its own cap (Energy Shield's passive plating begins empty
     * and mends up to maxBarrierHp) while still recording that cap as the barrier's max.
     */
    public BarrierEffect(String name, String description, int duration, int barrierHp, int maxBarrierHp) {
        super(name, description, duration);
        this.remainingBarrierHp = barrierHp;
        this.maxBarrierHp = maxBarrierHp;
        this.category = EffectCategory.BUFF;
    }

    public int getRemainingBarrierHp() {
        return remainingBarrierHp;
    }

    /** The barrier's own cap - the original amount as applied (or as last refreshed/grown). */
    public int getMaxBarrierHp() {
        return maxBarrierHp;
    }

    /**
     * Restores this barrier to a full pool and a full duration, for an ability that
     * refreshes rather than stacks a second instance of itself (Energy Shield). Lives here
     * rather than on the subclass because remainingBarrierHp is private to this class.
     */
    public void refresh(int barrierHp, int duration) {
        this.remainingBarrierHp = barrierHp;
        this.maxBarrierHp = barrierHp;
        setRemainingTurns(duration);
    }

    /**
     * Tops the pool back up toward {@code max} without touching the duration, for a barrier
     * that mends itself over time (upgraded Holy Shield, upgraded Energy Shield's passive).
     * Distinct from {@link #refresh}, which is a whole new barrier and resets the clock.
     * Never grows maxBarrierHp - see {@link #addBarrier} for that.
     */
    public void restore(int amount, int max) {
        if (amount <= 0 || remainingBarrierHp >= max) {
            return;
        }
        remainingBarrierHp = Math.min(max, remainingBarrierHp + amount);
    }

    /**
     * Adds fresh barrier on top of what remains, growing maxBarrierHp by the same amount -
     * unlike {@link #restore}, which only tops up toward an existing cap - for reapplications
     * that grant a genuinely bigger shield (Counterstrike proccing again).
     */
    public void addBarrier(int amount) {
        if (amount <= 0) {
            return;
        }
        maxBarrierHp += amount;
        remainingBarrierHp += amount;
    }

    @Override
    public String getExtraInfo() {
        return "Barrier: " + remainingBarrierHp + " HP remaining";
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        if (getOwner() == null || isExpired() || event.getTarget() != getOwner() || remainingBarrierHp <= 0) {
            return;
        }
        int damage = event.getDamage();
        if (damage <= 0) {
            return;
        }

        int absorbed = Math.min(damage, remainingBarrierHp);
        remainingBarrierHp -= absorbed;
        event.modifyDamage(-absorbed);

        if (remainingBarrierHp <= 0) {
            // Remove now rather than leaving this visible to the frontend until the owner's
            // next scheduled sweep - a barrier can be depleted on either team's turn.
            expireNow(state);
            onBarrierBroken(state);
        }
    }

    /** Fires immediately when the barrier hits 0 HP - not tied to the normal onExpire (natural-duration) path. */
    protected void onBarrierBroken(GameState state) {
    }
}
