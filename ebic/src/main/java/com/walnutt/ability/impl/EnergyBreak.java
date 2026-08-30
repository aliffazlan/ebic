package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Wei - every encounter he's in burns the opponent's cooldowns; more if he lands a hit as attacker. */
public class EnergyBreak extends PassiveAbility {
    private int cooldownIncrease;
    private int bonusIncrease;
    private int bonusDamagePerCooldown;
    private int healPerCooldown;

    public EnergyBreak(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.cooldownIncrease = definition.getInt("cooldown_increase", 1);
        this.bonusIncrease = definition.getInt("bonus_increase", 3);
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        // Once per attack cast, not once per hit: Chronos's Timeless Strike chains up to
        // nine attacks off a single click, which sent cooldowns to absurd numbers.
        if (event.chained()) {
            return;
        }
        if (event.attacker() == getOwner()) {
            boolean landed = event.damageEvent().getDamage() > 0;
            // Read BEFORE the drain below, so a hit is priced on the cooldowns the target was
            // already carrying rather than on the ones this very attack just added.
            if (landed) {
                feedOn(state, event.defender());
            }
            increaseCooldowns(event.defender(), landed ? bonusIncrease : cooldownIncrease);
        } else if (event.defender() == getOwner()) {
            increaseCooldowns(event.attacker(), cooldownIncrease);
        }
    }

    private void increaseCooldowns(Unit unit, int amount) {
        for (Ability ability : unit.getAbilities()) {
            if (!ability.isPassive() && !(ability instanceof Move) && !(ability instanceof Attack)) {
                ability.increaseCooldown(amount);
            }
        }
    }

    @Override
    protected void onUpgraded() {
        this.cooldownIncrease = statInt("cooldown_increase", cooldownIncrease);
        this.bonusIncrease = statInt("bonus_increase", bonusIncrease);
        this.bonusDamagePerCooldown = statInt("bonus_damage_per_cooldown", 0);
        this.healPerCooldown = statInt("heal_per_cooldown", 0);
    }

    /**
     * Upgrade: extra damage and a heal, both scaling with how exhausted the victim already is.
     *
     * Counts the same cooldowns increaseCooldowns drives up - a unit's real abilities, never
     * its Move or its Attack - so the two halves of this ability compound on each other.
     */
    private void feedOn(GameState state, Unit victim) {
        if (victim == null || victim.isDead() || (bonusDamagePerCooldown <= 0 && healPerCooldown <= 0)) {
            return;
        }
        int cooldowns = 0;
        for (Ability ability : victim.getAbilities()) {
            if (!ability.isPassive() && !(ability instanceof Move) && !(ability instanceof Attack)) {
                cooldowns += ability.getCurrentCooldown();
            }
        }
        if (cooldowns <= 0) {
            return;
        }
        int extra = bonusDamagePerCooldown * cooldowns;
        if (extra > 0) {
            DamageEvent drain = new DamageEvent(getOwner(), victim, extra);
            drain.setCauseLabel("Energy Break");
            victim.takeDamage(state, drain);
        }
        int healed = healPerCooldown * cooldowns;
        if (healed > 0 && !getOwner().isDead()) {
            getOwner().heal(state, healed);
        }
    }
}
