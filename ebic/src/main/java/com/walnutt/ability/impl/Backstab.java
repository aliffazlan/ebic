package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;

/** Evayne - "Attacks deal bonus damage per point of agility." */
public class Backstab extends PassiveAbility {
    private final double damageBonusPerAgility;

    public Backstab(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.damageBonusPerAgility = definition.getDouble("dmg_bonus", 0.4);
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        if (event.getSource() != getOwner()) {
            return;
        }
        int bonus = (int) Math.round(damageBonusPerAgility * getOwner().getAttributeValue(Attribute.AGILITY));
        if (bonus != 0) {
            event.modifyDamage(bonus);
        }
    }
}
