package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;

/**
 * Generic flat, stackable modifier to the OWNER's outgoing damage (positive = buff,
 * negative = debuff). Used by Discharge's Static Link, which grows this each turn
 * the link holds and lets it linger after the link itself breaks.
 */
public class DamageDealtModifierEffect extends Effect {
    private int totalBonus;

    public DamageDealtModifierEffect(String name, String description, int duration, int initialBonus) {
        super(name, description, duration);
        this.totalBonus = initialBonus;
        this.category = initialBonus < 0 ? EffectCategory.DEBUFF : EffectCategory.BUFF;
    }

    public int getTotalBonus() {
        return totalBonus;
    }

    public void addStack(int amount) {
        totalBonus += amount;
        category = totalBonus < 0 ? EffectCategory.DEBUFF : EffectCategory.BUFF;
    }

    @Override
    public String getExtraInfo() {
        if (totalBonus == 0) {
            return null;
        }
        return totalBonus > 0
            ? "Currently dealing +" + totalBonus + " damage"
            : "Currently dealing " + totalBonus + " less damage";
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        if (getOwner() == null || isExpired() || event.getSource() != getOwner() || totalBonus == 0) {
            return;
        }
        event.modifyDamage(totalBonus);
    }
}
