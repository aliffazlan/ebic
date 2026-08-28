package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.HomingMissileEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Maxwell gadget - a long-range missile that lands a turn later, wherever its target has gone. */
public class HomingMissile extends Ability {
    private final int delay;
    private final int damage;
    private final int aoeDamage;

    public HomingMissile(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 6));
        setRange(definition.getInt("cast_range", 8));
        this.delay = Math.max(1, definition.getInt("delay", 1));
        this.damage = definition.getInt("damage", 70);
        this.aoeDamage = definition.getInt("aoe_damage", 20);
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

        // A second lock on an already-tracked target refreshes rather than stacking - two
        // missiles chasing one unit is not what the ability describes, and the 6-turn
        // cooldown makes it a rare case anyway.
        HomingMissileEffect existing = enemy.getActiveEffect(HomingMissileEffect.class).orElse(null);
        if (existing != null) {
            existing.setRemainingTurns(delay);
        } else {
            enemy.addEffect(new HomingMissileEffect(owner, delay, damage, aoeDamage));
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
