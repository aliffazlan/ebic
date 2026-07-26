package com.walnutt.effect.impl;

import com.walnutt.event.DeathEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Spitter's Poison Bloom: a PoisonEffect whose duration ticks UP instead of down
 * for a window, extends further if hit by the caster again, and bursts into a
 * fresh poison on nearby enemies (based on stacks at death) when the host dies.
 */
public class BloomingPoisonEffect extends PoisonEffect {
    private int growthTurnsRemaining;
    private final Unit caster;
    private final int durationIncreaseOnCasterHit;
    private final int infectRadius;

    public BloomingPoisonEffect(Unit caster, int initialStacks, int growthTurns, int damagePerTurnRemaining,
                                 int durationIncreaseOnCasterHit, int infectRadius) {
        super(caster, initialStacks, damagePerTurnRemaining);
        this.growthTurnsRemaining = growthTurns;
        this.caster = caster;
        this.durationIncreaseOnCasterHit = durationIncreaseOnCasterHit;
        this.infectRadius = infectRadius;
    }

    @Override
    public void tick() {
        if (growthTurnsRemaining > 0) {
            growthTurnsRemaining--;
            setRemainingTurns(getRemainingTurns() + 1);
        } else {
            super.tick();
        }
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        if (event.attacker() == caster && event.defender() == getOwner()) {
            extendDuration(durationIncreaseOnCasterHit);
        }
    }

    @Override
    public void onDeath(GameState state, DeathEvent event) {
        if (event.unit() != getOwner()) {
            return;
        }
        int stacks = getRemainingTurns();
        if (stacks <= 0) {
            return;
        }
        for (Unit victim : state.getMap().getUnitsInRadius(getOwner().getPosition(), infectRadius)) {
            // "Nearby enemies" means enemies of the caster (Spitter) - i.e. other units on the
            // dying host's own team, not the host's allies.
            if (victim == getOwner() || victim.getTeam() == caster.getTeam() || victim.isDead()) {
                continue;
            }
            PoisonEffect.applyOrExtend(victim, caster, stacks, getDamagePerTurn());
        }
    }
}
