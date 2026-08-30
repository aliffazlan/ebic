package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.InfernalBladeEffect;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/** Lucifer - a successful attack curses the target with a stacking, pausable disarm. */
public class InfernalBlade extends PassiveAbility {
    private static final String STUN_NAME = "Infernal Blade Stun";

    private int duration;
    private int bonusDamage;
    private int stunDuration;

    public InfernalBlade(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.duration = definition.getInt("duration", 1);
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        if (event.attacker() != getOwner() || event.damageEvent().getDamage() <= 0) {
            return;
        }
        Unit defender = event.defender();
        if (defender.isDead()) {
            return;
        }
        // Read before the brand is applied or extended below, so the bonus is owed only to a
        // brand that was already burning - not to the one this very swing just laid down.
        boolean alreadyBranded = defender.getActiveEffect(InfernalBladeEffect.class).isPresent();
        InfernalBladeEffect.applyOrExtend(defender, getOwner(), duration);

        if (!alreadyBranded || (bonusDamage <= 0 && stunDuration <= 0)) {
            return;
        }
        if (bonusDamage > 0) {
            DamageEvent burn = new DamageEvent(getOwner(), defender, bonusDamage);
            burn.setCauseLabel("Infernal Blade");
            defender.takeDamage(state, burn);
        }
        // Refreshed rather than extended, unlike the brand itself - the JSON says so, and a
        // stacking stun off a repeatable passive would be a lock rather than a debuff. Matched
        // by name because StatusEffect is generic: several unrelated stuns share the class.
        if (stunDuration > 0 && !defender.isDead()) {
            defender.getEffects().stream()
                .filter(effect -> STUN_NAME.equals(effect.getName()) && !effect.isExpired())
                .findFirst()
                .ifPresentOrElse(
                    stun -> stun.setRemainingTurns(Math.max(stun.getRemainingTurns(), stunDuration)),
                    () -> defender.addEffect(new StatusEffect(STUN_NAME, stunDuration, StatusFlag.STUNNED)));
        }
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.bonusDamage = statInt("bonus_damage", 0);
        this.stunDuration = statInt("stun_duration", 0);
    }
}
