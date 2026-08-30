package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.EnergyShieldEffect;
import com.walnutt.effect.impl.EnergyShieldPassiveEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Maxwell gadget - a plain damage-absorbing barrier on itself or a nearby ally. */
public class EnergyShield extends Ability {
    private int barrierHp;
    private int duration;

    public EnergyShield(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 4));
        setRange(definition.getInt("cast_range", 3));
        this.barrierHp = definition.getInt("barrier", 80);
        this.duration = definition.getInt("duration", 4);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit ally = unitTarget.getUnit();
        return !ally.isDead() && ally.getTeam() == owner.getTeam() && isInRange(state, ally.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit ally = ((UnitTarget) target).getUnit();

        // Never two Energy Shields on one unit - recasting tops the existing one back up
        // to a full pool and a full duration. Other barriers (Holy Shield, Dislocation)
        // are separate effects and still stack alongside it, absorbing in turn.
        ally.getActiveEffect(EnergyShieldEffect.class).ifPresentOrElse(
            existing -> existing.refresh(barrierHp, duration),
            () -> ally.addEffect(new EnergyShieldEffect(getName(), barrierHp, duration)));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    /**
     * Upgrade: Maxwell gains a second barrier of his own, permanently. Granted here rather
     * than on the next cast so it starts mending immediately, and guarded so a repeat call
     * could never leave him wearing two of them.
     */
    @Override
    protected void onUpgraded() {
        this.barrierHp = statInt("barrier", barrierHp);
        this.duration = statInt("duration", duration);
        int regen = statInt("passive_regen", 0);
        int max = statInt("passive_barrier_max", 0);
        if (owner != null && regen > 0 && max > 0
            && owner.getActiveEffect(EnergyShieldPassiveEffect.class).isEmpty()) {
            owner.addEffect(new EnergyShieldPassiveEffect(regen, max));
        }
    }
}
