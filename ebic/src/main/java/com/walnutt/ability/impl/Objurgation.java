package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.FatalDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;

/** Harbinger - on fatal damage, consume all intelligence and convert a portion into survival HP. */
public class Objurgation extends PassiveAbility {
    private final double intelligenceToHealth;

    public Objurgation(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        setMaxCooldown(definition.getInt("cooldown", 3));
        this.intelligenceToHealth = definition.getDouble("int_to_hp", 0.8);
    }

    @Override
    public void onFatalDamage(GameState state, FatalDamageEvent event) {
        if (event.getTarget() != getOwner() || !isReady()) {
            return;
        }
        int intelligence = getOwner().getAttributeValue(Attribute.INTELLIGENCE);
        int hpGained = (int) Math.round(intelligenceToHealth * intelligence);

        getOwner().addPermanentModifier(StatModifier.percent(Stat.INTELLIGENCE, -1.0, this));
        event.preventDeath(hpGained);
        resetToMax();
    }
}
