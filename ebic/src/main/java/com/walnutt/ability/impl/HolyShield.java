package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.HolyShieldBarrierEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Thaddeus - shields an ally; if the barrier breaks, it clears their debuffs and blasts nearby enemies. */
public class HolyShield extends Ability {
    private final int duration;
    private final int barrierHp;
    private final int breakDamage;
    private final int radius;

    public HolyShield(AbilityDefinition definition) {
        super(definition.name(), definition.description(), false);
        setMaxCooldown(definition.getInt("cooldown", 4));
        setRange(definition.getInt("cast_range", 2));
        this.duration = definition.getInt("duration", 3);
        this.barrierHp = definition.getInt("barrier_hp", 50);
        this.breakDamage = definition.getInt("damage", 50);
        this.radius = definition.getInt("radius", 1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !state.canSpendMoves(getMoveCost(state))) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit ally = unitTarget.getUnit();
        return !ally.isDead()
            && ally.getTeam() == owner.getTeam()
            && state.getMap().getDistance(owner.getPosition(), ally.getPosition()) <= getRange();
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit ally = ((UnitTarget) target).getUnit();
        ally.addEffect(new HolyShieldBarrierEffect(duration, barrierHp, breakDamage, radius));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
