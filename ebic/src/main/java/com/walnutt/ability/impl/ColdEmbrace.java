package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.FreezeEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Auroth - freezes a unit (immune to attacks), damaging enemies or healing allies each turn. */
public class ColdEmbrace extends Ability {
    private int duration;
    private int amountPerTurn;

    public ColdEmbrace(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 6));
        setRange(definition.getInt("cast_range", 4));
        this.duration = definition.getInt("duration", 3);
        this.amountPerTurn = definition.getInt("dmg_heal", 30);
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
        return !other.isDead() && isInRange(state, other.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();
        boolean isEnemy = other.getTeam() != owner.getTeam();
        // Upgraded, an ally is silenced and disarmed rather than shut down outright - they can
        // still walk out of trouble while the ice does its work. An enemy is unaffected by
        // the upgrade and stays fully disabled.
        boolean allyMayMove = isUpgraded() && !isEnemy;
        other.addEffect(new FreezeEffect(owner, duration, amountPerTurn, isEnemy, allyMayMove));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.amountPerTurn = statInt("dmg_heal", amountPerTurn);
    }
}
