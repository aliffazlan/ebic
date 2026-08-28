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

    public BarrierEffect(String name, String description, int duration, int barrierHp) {
        super(name, description, duration);
        this.remainingBarrierHp = barrierHp;
        this.category = EffectCategory.BUFF;
    }

    public int getRemainingBarrierHp() {
        return remainingBarrierHp;
    }

    /**
     * Restores this barrier to a full pool and a full duration, for an ability that
     * refreshes rather than stacks a second instance of itself (Energy Shield). Lives here
     * rather than on the subclass because remainingBarrierHp is private to this class.
     */
    public void refresh(int barrierHp, int duration) {
        this.remainingBarrierHp = barrierHp;
        setRemainingTurns(duration);
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
            setRemainingTurns(0);
            onBarrierBroken(state);
        }
    }

    /** Fires immediately when the barrier hits 0 HP - not tied to the normal onExpire (natural-duration) path. */
    protected void onBarrierBroken(GameState state) {
    }
}
