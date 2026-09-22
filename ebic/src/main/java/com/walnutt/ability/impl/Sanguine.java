package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Noctis - the more wounded the prey, the deeper the bite. */
public class Sanguine extends PassiveAbility {
    private double bonusPctDamage;
    private double maxHpHeal;
    private double currentHpDamage;

    public Sanguine(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.bonusPctDamage = definition.getDouble("bonus_pct_damage", 0.025);
        this.maxHpHeal = definition.getDouble("max_hp_heal", 0.4);
        this.currentHpDamage = definition.getDouble("current_hp_damage", 0.2);
    }

    @Override
    protected void onUpgraded() {
        this.bonusPctDamage = stat("bonus_pct_damage", bonusPctDamage);
        this.maxHpHeal = stat("max_hp_heal", maxHpHeal);
        this.currentHpDamage = stat("current_hp_damage", currentHpDamage);
    }

    /**
     * Additive current-HP bonus (upgrade only) then the missing-HP amp, both on the
     * same event and in that order - so the amp also scales the upgrade's bonus, per
     * the confirmed design formula: (base + currentHpBonus) * (1 + missingHpPct * rate).
     */
    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        Unit noctis = getOwner();
        if (noctis == null || event.getSource() != noctis || event.getDamage() <= 0) {
            return;
        }
        Unit target = event.getTarget();
        if (target == null || target.getMaxHealth() <= 0) {
            return;
        }
        if (isUpgraded()) {
            int currentHpBonus = (int) Math.round(target.getHealth() * currentHpDamage);
            if (currentHpBonus > 0) {
                event.modifyDamage(currentHpBonus);
            }
        }
        double missingHpPct = 100.0 * (1 - (double) target.getHealth() / target.getMaxHealth());
        if (missingHpPct > 0) {
            event.multiplyDamage(1 + (missingHpPct * bonusPctDamage));
        }
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        Unit noctis = getOwner();
        if (noctis == null || event.attacker() != noctis) {
            return;
        }
        Unit victim = event.defender();
        if (victim == null || !victim.isDead()) {
            return;
        }
        int healAmount = (int) Math.round(victim.getMaxHealth() * maxHpHeal);
        if (healAmount > 0) {
            noctis.heal(state, healAmount);
        }
    }
}
