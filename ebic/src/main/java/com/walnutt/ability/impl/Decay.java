package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.unit.Unit;

/**
 * Dirge - at the start of every one of Dirge's own turns, permanently steals max HP
 * and strength from all units in radius (ally and enemy alike), plus direct damage.
 *
 * Note: the design JSON's description mentions a "str_steal" stat that isn't
 * actually present in stats{} (only health_steal and bonus_increase are) - this
 * reuses bonus_increase as the strength-steal amount, the closest available number.
 */
public class Decay extends PassiveAbility {
    private final int radius;
    private final int healthSteal;
    private final int strengthSteal;

    public Decay(AbilityDefinition definition) {
        super(definition.name(), definition.description());
        this.radius = definition.getInt("radius", 1);
        this.healthSteal = definition.getInt("health_steal", 5);
        this.strengthSteal = definition.getInt("str_steal", definition.getInt("bonus_increase", 2));
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() == null || event.team() != getOwner().getTeam()) {
            return;
        }
        for (Unit victim : state.getMap().getUnitsInRadius(getOwner().getPosition(), radius)) {
            if (victim == getOwner() || victim.isDead()) {
                continue;
            }
            victim.addPermanentModifier(StatModifier.flat(Stat.MAX_HEALTH, -healthSteal, this));
            getOwner().addPermanentModifier(StatModifier.flat(Stat.MAX_HEALTH, healthSteal, this));
            victim.addPermanentModifier(StatModifier.flat(Stat.STRENGTH, -strengthSteal, this));
            getOwner().addPermanentModifier(StatModifier.flat(Stat.STRENGTH, strengthSteal, this));
            DamageEvent decayDamage = new DamageEvent(getOwner(), victim, healthSteal);
            decayDamage.setCauseLabel("Decay");
            victim.takeDamage(state, decayDamage);
        }
    }
}
