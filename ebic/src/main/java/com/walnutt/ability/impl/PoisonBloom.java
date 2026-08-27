package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.PoisonBloomEffect;
import com.walnutt.effect.impl.PoisonEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Spitter - poisons the target and plants a bloom in it.
 *
 * Applies two distinct effects: a normal PoisonEffect carrying the initial stacks (the
 * only thing that deals damage) and a PoisonBloomEffect that feeds it while the bloom
 * lasts and bursts when the bloom ends. The damage rate comes from Spitter's own Poison
 * Sting, since it is the same venom and poison_bloom.json carries no damage stat.
 */
public class PoisonBloom extends Ability {
    /** Net stacks the bloom adds to the poison each turn it is active. */
    private static final int GROWTH_PER_TURN = 1;

    private final int initialPoison;
    private final int growthDuration;
    private final int durationIncrease;
    private final int infectRadius;

    public PoisonBloom(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 6));
        setRange(definition.getInt("cast_range", 3));
        this.initialPoison = definition.getInt("initial_poison", 10);
        this.growthDuration = definition.getInt("duration", 3);
        this.durationIncrease = definition.getInt("duration_increase", 1);
        this.infectRadius = definition.getInt("infect_radius", 1);
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

        int damagePerTurn = owner.getAbilities().stream()
            .filter(a -> a instanceof PoisonSting)
            .map(a -> ((PoisonSting) a).getDamagePerTurnRemaining())
            .findFirst()
            .orElse(4);

        // Two separate effects: the Poison does the damage, the Bloom feeds it and bursts.
        PoisonEffect.applyOrExtend(other, owner, initialPoison, damagePerTurn);
        other.addEffect(new PoisonBloomEffect(owner, growthDuration, GROWTH_PER_TURN, durationIncrease,
            infectRadius));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
