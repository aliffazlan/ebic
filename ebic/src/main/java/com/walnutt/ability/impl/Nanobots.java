package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.NanobotsEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Maxwell gadget - cleanses and heals an ally now, and again at the start of their turns. */
public class Nanobots extends Ability {
    private final int heal;
    private final int duration;

    public Nanobots(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 7));
        setRange(definition.getInt("cast_range", 2));
        this.heal = definition.getInt("heal", 40);
        this.duration = definition.getInt("duration", 2);
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

        NanobotsEffect effect = new NanobotsEffect(duration, heal);
        ally.addEffect(effect);
        // The first pulse lands immediately rather than waiting a turn - otherwise the
        // ability does literally nothing on the turn it is cast.
        effect.pulse(state);

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
