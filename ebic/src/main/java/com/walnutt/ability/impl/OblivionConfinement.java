package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.ImprisonmentEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Harbinger - imprisons (stun + invulnerable) an enemy, stealing intelligence on cast and on escape. */
public class OblivionConfinement extends Ability {
    private final int duration;
    private final double intStealPercent;

    public OblivionConfinement(AbilityDefinition definition) {
        super(definition.name(), definition.description(), false);
        setMaxCooldown(definition.getInt("cooldown", 3));
        setRange(definition.getInt("cast_range", 2));
        this.duration = definition.getInt("duration", 1);
        this.intStealPercent = definition.getDouble("int_steal", 0.2);
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

        ImprisonmentEffect effect = new ImprisonmentEffect(owner, duration, intStealPercent);
        other.addEffect(effect);
        effect.stealIntelligence();

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
