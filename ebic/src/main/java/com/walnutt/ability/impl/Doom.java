package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.DoomEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Lucifer - curses an enemy with silence and escalating damage until it lands a kill. */
public class Doom extends Ability {
    private final int baseDamage;
    private final int damageIncrease;

    public Doom(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 11));
        setRange(definition.getInt("cast_range", 1));
        this.baseDamage = definition.getInt("base_dmg", 20);
        this.damageIncrease = definition.getInt("dmg_increase", 20);
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
            && other.getTeam() != owner.getTeam()
            && other.getActiveEffect(DoomEffect.class).isEmpty()
            && isInRange(state, other.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();
        other.addEffect(new DoomEffect(owner, baseDamage, damageIncrease));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
