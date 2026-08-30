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
    private double intelligenceToHealth;
    private double intelligenceConsumed;
    /** Upgrade: the share burned by a blow that would actually kill. 0 until upgraded. */
    private double fatalIntelligenceConsumed;

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
        // Upgraded, a blow that would genuinely kill him is answered with everything he has
        // rather than the ordinary share - which is the difference between surviving it and not.
        double share = fatalIntelligenceConsumed > 0 ? fatalIntelligenceConsumed : intelligenceConsumed;
        int intelligence = getOwner().getAttributeValue(Attribute.INTELLIGENCE);
        double consumed = share * intelligence;
        int hpGained = (int) Math.round(intelligenceToHealth * consumed);

        getOwner().addPermanentModifier(StatModifier.percent(Stat.INTELLIGENCE, -share, this));
        event.preventDeath(hpGained);
        resetToMax();
    }

    @Override
    protected void onUpgraded() {
        this.intelligenceToHealth = stat("int_to_hp", intelligenceToHealth);
        this.intelligenceConsumed = stat("int_consumed", intelligenceConsumed);
        this.fatalIntelligenceConsumed = stat("fatal_int_consumed", 0);
    }
}
