package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BloomingPoisonEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Spitter - immediately applies a large poison stack that counts up before it counts down. */
public class PoisonBloom extends Ability {
    private final int initialPoison;
    private final int growthDuration;
    private final int durationIncrease;
    private final int infectRadius;

    public PoisonBloom(AbilityDefinition definition) {
        super(definition.name(), definition.description(), false);
        setMaxCooldown(definition.getInt("cooldown", 6));
        setRange(definition.getInt("cast_range", 3));
        this.initialPoison = definition.getInt("initial_poison", 10);
        this.growthDuration = definition.getInt("duration", 3);
        this.durationIncrease = definition.getInt("duration_increase", 1);
        this.infectRadius = definition.getInt("infect_radius", 1);
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
        return !other.isDead() && state.getMap().getDistance(owner.getPosition(), other.getPosition()) <= getRange();
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit other = ((UnitTarget) target).getUnit();

        int damagePerTurn = owner.getAbilities().stream()
            .filter(a -> a instanceof PoisonSting)
            .map(a -> ((PoisonSting) a).getDamagePerTurnRemaining())
            .findFirst()
            .orElse(4);

        other.addEffect(new BloomingPoisonEffect(owner, initialPoison, growthDuration, damagePerTurn,
            durationIncrease, infectRadius));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
