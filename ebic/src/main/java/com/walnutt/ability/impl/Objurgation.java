package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.FatalDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;

/**
 * Harbinger - on fatal damage, consume a portion of intelligence and convert it into
 * survival HP. Consumes int_consumed of the current value (not all of it) and returns
 * int_to_hp HP per point consumed, so at a 1:1 ratio the HP gained is exactly the
 * intelligence lost.
 */
public class Objurgation extends PassiveAbility {
    private final double intelligenceToHealth;
    private final double intelligenceConsumed;

    public Objurgation(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        setMaxCooldown(definition.getInt("cooldown", 4));
        this.intelligenceToHealth = definition.getDouble("int_to_hp", 1.0);
        this.intelligenceConsumed = definition.getDouble("int_consumed", 0.5);
    }

    @Override
    public void onFatalDamage(GameState state, FatalDamageEvent event) {
        if (event.getTarget() != getOwner() || !isReady()) {
            return;
        }
        int intelligence = getOwner().getAttributeValue(Attribute.INTELLIGENCE);
        double consumed = intelligenceConsumed * intelligence;
        int hpGained = (int) Math.round(intelligenceToHealth * consumed);

        getOwner().addPermanentModifier(StatModifier.percent(Stat.INTELLIGENCE, -intelligenceConsumed, this));
        event.preventDeath(hpGained);
        resetToMax();
    }
}
