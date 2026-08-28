package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.unit.Unit;

/**
 * Maxwell gadget - permanent bonus attack and cast range.
 *
 * The bonuses are permanent modifiers on the OWNER, not adjustments to any particular
 * ability, which is what makes this work in either order: Ability.getRange() adds
 * Stat.CAST_RANGE on the fly, so a gadget constructed after this one is lifted too, with
 * no bookkeeping when it arrives.
 */
public class Gyroscope extends PassiveAbility {
    private final int attackRangeBoost;
    private final int castRangeBoost;

    public Gyroscope(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.attackRangeBoost = definition.getInt("attack_range_boost", 1);
        this.castRangeBoost = definition.getInt("cast_range_boost", 1);
    }

    /** Applied on attach rather than in the constructor - this ability is granted mid-match. */
    @Override
    protected void onAttached(Unit newOwner) {
        if (attackRangeBoost != 0) {
            newOwner.addPermanentModifier(StatModifier.flat(Stat.ATTACK_RANGE, attackRangeBoost, this));
        }
        if (castRangeBoost != 0) {
            newOwner.addPermanentModifier(StatModifier.flat(Stat.CAST_RANGE, castRangeBoost, this));
        }
    }
}
