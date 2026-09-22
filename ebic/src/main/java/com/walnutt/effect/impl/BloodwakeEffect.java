package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.KillEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Noctis's Bloodwake: a bloodlust that hits harder the longer it's left to run.
 * Refreshing it - a kill during the effect, or recasting it while it's already
 * active - resets the clock in the base form and extends it in the upgraded one;
 * either way the accumulated bonus damage is never touched.
 */
public class BloodwakeEffect extends Effect {
    private final int refreshDuration;
    private final int bonusDamagePerTurn;
    private boolean upgraded;
    private int accumulatedBonusDamage;

    public BloodwakeEffect(int refreshDuration, int bonusDamagePerTurn, boolean upgraded) {
        super("Bloodwake", refreshDuration);
        this.refreshDuration = refreshDuration;
        this.bonusDamagePerTurn = bonusDamagePerTurn;
        this.upgraded = upgraded;
        this.accumulatedBonusDamage = bonusDamagePerTurn;
        this.category = EffectCategory.BUFF;
    }

    /**
     * Applies a fresh Bloodwake, or refreshes the one already running - Bloodwake.onUse's
     * only caller, so the reset-vs-extend choice on recast lives in one place alongside
     * onKill's own call to {@link #refresh}.
     */
    public static void applyOrRefresh(Unit target, int duration, int bonusDamage, boolean upgraded) {
        target.getActiveEffect(BloodwakeEffect.class).ifPresentOrElse(
            existing -> existing.refresh(upgraded),
            () -> target.addEffect(new BloodwakeEffect(duration, bonusDamage, upgraded)));
    }

    /**
     * A kill during the effect, or recasting it while it's already active, both call
     * this. The base form resets the clock back to refreshDuration; the upgraded form
     * adds refreshDuration onto whatever is left instead. The accumulated bonus damage
     * carries over unchanged either way.
     */
    public void refresh(boolean upgradedNow) {
        this.upgraded = upgradedNow;
        if (upgradedNow) {
            extendDuration(refreshDuration);
        } else {
            setRemainingTurns(refreshDuration);
        }
    }

    @Override
    public String getExtraInfo() {
        return "+" + accumulatedBonusDamage + " damage";
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit noctis = getOwner();
        if (noctis == null || isExpired() || event.team() != noctis.getTeam()) {
            return;
        }
        accumulatedBonusDamage += bonusDamagePerTurn;
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        Unit noctis = getOwner();
        if (noctis == null || isExpired() || event.getSource() != noctis || event.getDamage() <= 0) {
            return;
        }
        event.modifyDamage(accumulatedBonusDamage);
    }

    @Override
    public void onKill(GameState state, KillEvent event) {
        if (getOwner() != null && !isExpired() && event.killer() == getOwner()) {
            refresh(upgraded);
        }
    }

    @Override
    public boolean grantsFreeMove() {
        return !isExpired();
    }

    @Override
    public int bonusMoveActions() {
        return isExpired() ? 0 : 1;
    }

    @Override
    public boolean grantsFreeAttack() {
        return !isExpired() && upgraded;
    }

    @Override
    public int bonusAttackActions() {
        return !isExpired() && upgraded ? 1 : 0;
    }
}
