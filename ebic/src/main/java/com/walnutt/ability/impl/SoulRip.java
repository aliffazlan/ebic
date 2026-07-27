package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Dirge - damage (or heal, if targeting an ally) proportional to the strength difference. */
public class SoulRip extends Ability {
    private final double strengthMultiplier;

    public SoulRip(AbilityDefinition definition) {
        super(definition.name(), definition.description(), false);
        setMaxCooldown(definition.getInt("cooldown", 3));
        setRange(definition.getInt("cast_range", 2));
        this.strengthMultiplier = definition.getDouble("str_multiplier", 0.5);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !state.canSpendMoves(getMoveCost(state))) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit other = unitTarget.getUnit();
        return !other.isDead()
            && state.getMap().getDistance(owner.getPosition(), other.getPosition()) <= getRange();
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();
        int diff = owner.getAttributeValue(Attribute.STRENGTH) - other.getAttributeValue(Attribute.STRENGTH);
        int amount = (int) Math.round(strengthMultiplier * diff);

        if (amount > 0) {
            if (other.getTeam() == owner.getTeam()) {
                other.heal(state, amount);
            } else {
                DamageEvent event = new DamageEvent(owner, other, amount);
                event.setCauseLabel("Soul Rip");
                other.takeDamage(state, event);
            }
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
