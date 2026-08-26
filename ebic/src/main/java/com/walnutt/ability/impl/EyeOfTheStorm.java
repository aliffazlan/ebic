package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.DamageTakenModifierEffect;
import com.walnutt.effect.impl.StaticLinkEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Discharge - a random adjacent enemy is struck every turn and permanently grows more vulnerable; always also hits a Static Link target. */
public class EyeOfTheStorm extends PassiveAbility {
    private final int damage;
    private final int bonusDamage;

    public EyeOfTheStorm(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.damage = definition.getInt("damage", 2);
        this.bonusDamage = definition.getInt("bonus_damage", 2);
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit owner = getOwner();
        if (owner == null || event.team() != owner.getTeam()) {
            return;
        }

        state.getMap().randomAdjacentUnit(owner.getPosition(), u -> u.getTeam() != owner.getTeam() && !u.isDead(),
            state.getRandom()).ifPresent(target -> strike(state, target));

        owner.getActiveEffect(StaticLinkEffect.class).ifPresent(link -> {
            Unit linked = link.getTarget();
            if (linked != null && !linked.isDead()) {
                strike(state, linked);
            }
        });
    }

    private void strike(GameState state, Unit target) {
        DamageEvent strike = new DamageEvent(getOwner(), target, damage);
        strike.setCauseLabel("Eye of the Storm");
        target.takeDamage(state, strike);
        if (target.isDead()) {
            return;
        }
        DamageTakenModifierEffect vulnerability = target.getActiveEffect(DamageTakenModifierEffect.class).orElse(null);
        if (vulnerability == null) {
            vulnerability = new DamageTakenModifierEffect("Static Charge",
                "A permanently stacking vulnerability from Eye of the Storm: increases damage taken "
                    + "from all sources by " + bonusDamage + " per stack.",
                Effect.PERMANENT, 0);
            target.addEffect(vulnerability);
        }
        vulnerability.addStack(bonusDamage);
    }
}
