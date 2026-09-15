package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BarrierEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.FatalDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.unit.Unit;

/**
 * Harbinger - below hp_threshold health, any blow is met first with a barrier burning a
 * share of intelligence into shielding (int_to_hp HP per point consumed). Upgraded, a
 * blow that would actually kill him is answered differently: it burns everything he has
 * rather than the ordinary share, cheating death outright instead of merely shielding.
 * Both paths share one cooldown/isReady() gate, so within a single hit only one of the
 * two can fire - the barrier runs first (onIncomingDamage, before HP is finalized), and
 * going on cooldown there is what stops onFatalDamage from also firing on the same blow.
 */
public class Objurgation extends PassiveAbility {
    private double intelligenceToHealth;
    private double intelligenceConsumed;
    private double hpThreshold;
    private int barrierDuration;
    /** Upgrade: the share burned by a blow that would actually kill. 0 until upgraded. */
    private double fatalIntelligenceConsumed;

    public Objurgation(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        setMaxCooldown(definition.getInt("cooldown", 4));
        this.intelligenceToHealth = definition.getDouble("int_to_hp", 1.0);
        this.intelligenceConsumed = definition.getDouble("int_consumed", 0.5);
        this.hpThreshold = definition.getDouble("hp_threshold", 0.5);
        this.barrierDuration = definition.getInt("barrier_duration", 4);
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        Unit owner = getOwner();
        if (owner == null || event.getTarget() != owner || !isReady() || event.getDamage() <= 0) {
            return;
        }
        if ((double) owner.getHealth() / owner.getMaxHealth() > hpThreshold) {
            return;
        }
        // Upgraded, a blow that would actually kill him is answered differently (onFatalDamage,
        // below) - the barrier must not spend the shared cooldown ahead of a genuinely fatal
        // hit, or the guaranteed save could never fire on the very blow it exists for.
        if (fatalIntelligenceConsumed > 0 && event.getDamage() >= owner.getHealth()) {
            return;
        }
        int intelligence = owner.getAttributeValue(Attribute.INTELLIGENCE);
        int barrierHp = (int) Math.round(intelligenceToHealth * intelligenceConsumed * intelligence);
        if (barrierHp <= 0) {
            return;
        }
        owner.addPermanentModifier(StatModifier.percent(Stat.INTELLIGENCE, -intelligenceConsumed, this));
        // The barrier absorbs this same blow first, then whatever is left of its pool
        // stands guard for barrier_duration turns - "met first with a barrier".
        int absorbedNow = Math.min(event.getDamage(), barrierHp);
        event.modifyDamage(-absorbedNow);
        int remaining = barrierHp - absorbedNow;
        if (remaining > 0) {
            owner.addEffect(new BarrierEffect("Objurgation Barrier",
                "Absorbs up to " + remaining + " damage before it reaches this unit's health.",
                barrierDuration, remaining));
        }
        resetToMax();
    }

    @Override
    public void onFatalDamage(GameState state, FatalDamageEvent event) {
        if (event.getTarget() != getOwner() || !isReady() || fatalIntelligenceConsumed <= 0) {
            return;
        }
        // Only reachable once upgraded - the base kit's barrier (onIncomingDamage, above)
        // is a conditional shield, not a guarantee; guaranteed survival on an actually-fatal
        // blow is the upgrade's own perk.
        int intelligence = getOwner().getAttributeValue(Attribute.INTELLIGENCE);
        double consumed = fatalIntelligenceConsumed * intelligence;
        int hpGained = (int) Math.round(intelligenceToHealth * consumed);

        getOwner().addPermanentModifier(StatModifier.percent(Stat.INTELLIGENCE, -fatalIntelligenceConsumed, this));
        event.preventDeath(hpGained);
        resetToMax();
    }

    @Override
    protected void onUpgraded() {
        this.intelligenceToHealth = stat("int_to_hp", intelligenceToHealth);
        this.intelligenceConsumed = stat("int_consumed", intelligenceConsumed);
        this.barrierDuration = statInt("barrier_duration", barrierDuration);
        this.fatalIntelligenceConsumed = stat("fatal_int_consumed", 0);
    }
}
