package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.unit.Unit;

/** Dirge - damage (or heal, if targeting an ally) proportional to the strength difference. */
public class SoulRip extends Ability {
    private double strengthMultiplier;
    private int strengthSteal;

    public SoulRip(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 3));
        setRange(definition.getInt("cast_range", 2));
        this.strengthMultiplier = definition.getDouble("str_multiplier", 0.5);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit other = unitTarget.getUnit();
        return !other.isDead()
            && isInRange(state, other.getPosition());
    }

    @Override
    protected void onUpgraded() {
        this.strengthMultiplier = stat("str_multiplier", strengthMultiplier);
        this.strengthSteal = statInt("str_steal", 0);
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();
        boolean ally = other.getTeam() == owner.getTeam();

        // Upgraded, against an enemy: the strength is torn away BEFORE the damage is worked
        // out, so the damage is reckoned from the wider gap rather than the original one.
        if (strengthSteal > 0 && !ally) {
            other.addPermanentModifier(StatModifier.flat(Stat.STRENGTH, -strengthSteal, this));
            owner.addPermanentModifier(StatModifier.flat(Stat.STRENGTH, strengthSteal, this));
        }

        int diff = owner.getAttributeValue(Attribute.STRENGTH) - other.getAttributeValue(Attribute.STRENGTH);
        int amount = (int) Math.round(strengthMultiplier * diff);

        if (amount > 0) {
            if (ally) {
                other.heal(state, amount);
            } else {
                DamageEvent event = new DamageEvent(owner, other, amount);
                event.setCauseLabel("Soul Rip");
                other.takeDamage(state, event);
            }
        }

        // On an ally it is a gift rather than a transfer, and it lands AFTER the heal so it
        // cannot shrink the strength gap the heal was sized from.
        if (strengthSteal > 0 && ally) {
            other.addPermanentModifier(StatModifier.flat(Stat.STRENGTH, strengthSteal, this));
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
