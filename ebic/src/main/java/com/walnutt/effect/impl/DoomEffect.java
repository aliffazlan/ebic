package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.KillEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/**
 * Lucifer's Doom curse: silences the target and deals escalating damage at the
 * start of every one of its own turns. Has no natural duration (Effect.PERMANENT)
 * - the only way out is landing a kill, checked via onKill against the owner itself.
 */
public class DoomEffect extends Effect {
    private final Unit source;
    private final int damageIncrease;
    private int currentDamage;

    public DoomEffect(Unit source, int baseDamage, int damageIncrease) {
        super("Doom",
            "A curse that silences its target and deals " + baseDamage + " damage at the start of each "
                + "of its turns, increasing by " + damageIncrease + " every turn. Lasts until the "
                + "cursed unit lands a kill.",
            Effect.PERMANENT);
        this.source = source;
        this.currentDamage = baseDamage;
        this.damageIncrease = damageIncrease;
        this.flags.add(StatusFlag.SILENCED);
        this.category = EffectCategory.DEBUFF;
    }

    public int getCurrentDamage() {
        return currentDamage;
    }

    @Override
    public String getExtraInfo() {
        return "Next hit: " + currentDamage + " damage";
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit owner = getOwner();
        if (owner == null || isExpired() || owner.isDead() || event.team() != owner.getTeam()) {
            return;
        }
        DamageEvent damageEvent = new DamageEvent(source, owner, currentDamage);
        damageEvent.setCauseLabel("Doom");
        owner.takeDamage(state, damageEvent);
        currentDamage += damageIncrease;
    }

    @Override
    public void onKill(GameState state, KillEvent event) {
        if (getOwner() != null && event.killer() == getOwner()) {
            setRemainingTurns(0);
        }
    }
}
