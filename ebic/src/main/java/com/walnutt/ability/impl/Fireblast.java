package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BurnEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Ember - a direct fireball that damages and ignites a single enemy. */
public class Fireblast extends Ability {
    private final int damage;
    private final int burnStacks;

    public Fireblast(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 2));
        setRange(definition.getInt("cast_range", 3));
        this.damage = definition.getInt("damage", 24);
        this.burnStacks = definition.getInt("burn_stacks", 3);
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
            && isInRange(state, other.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();

        // Damage first so its own heat contribution lands before the new stacks do.
        DamageEvent blast = new DamageEvent(owner, other, damage);
        blast.setCauseLabel("Fireblast");
        other.takeDamage(state, blast);

        BurnEffect.apply(state, other, owner, burnStacks);

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
