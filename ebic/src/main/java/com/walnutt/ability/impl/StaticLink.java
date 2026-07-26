package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.StaticLinkEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Discharge - forms a draining link with an enemy; see StaticLinkEffect for the per-turn mechanics. */
public class StaticLink extends Ability {
    private final int damageSteal;
    private final int lingerDuration;

    public StaticLink(AbilityDefinition definition) {
        super(definition.name(), definition.description(), false);
        setMaxCooldown(definition.getInt("cooldown", 7));
        setRange(definition.getInt("cast_range", 1));
        this.damageSteal = definition.getInt("dmg_steal", 5);
        this.lingerDuration = definition.getInt("buff_linger_duration", 2);
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
            && other.getTeam() != owner.getTeam()
            && state.getMap().getDistance(owner.getPosition(), other.getPosition()) <= getRange();
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();
        owner.addEffect(new StaticLinkEffect(owner, other, damageSteal, lingerDuration));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
