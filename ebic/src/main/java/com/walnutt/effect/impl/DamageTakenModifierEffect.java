package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;

/**
 * Generic flat, stackable modifier to the OWNER's incoming damage (positive =
 * vulnerability/debuff, negative = damage reduction/buff). Used by Discharge's Eye
 * of the Storm, which permanently stacks this on whoever it strikes.
 */
public class DamageTakenModifierEffect extends Effect {
    private int totalBonus;

    public DamageTakenModifierEffect(String name, String description, int duration, int initialBonus) {
        super(name, description, duration);
        this.totalBonus = initialBonus;
        updateCategory();
    }

    public int getTotalBonus() {
        return totalBonus;
    }

    public void addStack(int amount) {
        totalBonus += amount;
        updateCategory();
    }

    private void updateCategory() {
        category = totalBonus > 0 ? EffectCategory.DEBUFF : totalBonus < 0 ? EffectCategory.BUFF : EffectCategory.NEUTRAL;
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        if (getOwner() == null || isExpired() || event.getTarget() != getOwner() || totalBonus == 0) {
            return;
        }
        event.modifyDamage(totalBonus);
    }
}
