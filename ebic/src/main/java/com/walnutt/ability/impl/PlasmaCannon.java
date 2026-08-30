package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/** Maxwell gadget - direct damage plus a disarm. */
public class PlasmaCannon extends Ability {
    private int damage;
    private int duration;

    public PlasmaCannon(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 4));
        setRange(definition.getInt("cast_range", 2));
        this.damage = definition.getInt("damage", 60);
        this.duration = definition.getInt("duration", 2);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit enemy = unitTarget.getUnit();
        return !enemy.isDead() && enemy.getTeam() != owner.getTeam() && isInRange(state, enemy.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit enemy = ((UnitTarget) target).getUnit();

        // Disarm first: a dead unit's effects are pointless, and applying it before the
        // damage means a Counterstrike provoked by that damage is already suppressed.
        enemy.addEffect(new StatusEffect("Plasma Burns",
            "Molten plasma has fused this unit's weapon - it cannot attack.",
            duration, EffectCategory.DEBUFF, StatusFlag.DISARMED));

        DamageEvent bolt = new DamageEvent(owner, enemy, damage);
        bolt.setCauseLabel(getName());
        enemy.takeDamage(state, bolt);

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    /** Upgrade: the bolt hits for `damage` rather than 60. */
    @Override
    protected void onUpgraded() {
        this.damage = statInt("damage", damage);
        this.duration = statInt("duration", duration);
    }
}
