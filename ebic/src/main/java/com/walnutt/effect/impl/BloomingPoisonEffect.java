package com.walnutt.effect.impl;

import com.walnutt.event.DeathEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Spitter's Poison Bloom: a PoisonEffect whose duration ticks UP instead of down for a
 * window, extends further if hit by the caster again, and bursts into a fresh poison on
 * nearby enemies.
 *
 * The burst fires when the bloom finishes - either because the host died under it, or
 * because it ran its full course - but NOT if it was cleansed off early. A `spread`
 * latch keeps the two paths from both firing: a host that dies under the effect still
 * sits in Player.getUnits() and keeps ticking, so its onExpire would otherwise land a
 * second burst a few turns after the death burst.
 */
public class BloomingPoisonEffect extends PoisonEffect {
    private int growthTurnsRemaining;
    private final Unit caster;
    private final int durationIncreaseOnCasterHit;
    private final int infectRadius;
    private boolean spread;
    private int peakStacks;

    public BloomingPoisonEffect(Unit caster, int initialStacks, int growthTurns, int damagePerTurnRemaining,
                                 int durationIncreaseOnCasterHit, int infectRadius) {
        super(caster, initialStacks, damagePerTurnRemaining);
        this.growthTurnsRemaining = growthTurns;
        this.caster = caster;
        this.durationIncreaseOnCasterHit = durationIncreaseOnCasterHit;
        this.infectRadius = infectRadius;
        this.peakStacks = initialStacks;
    }

    @Override
    public void tick() {
        if (growthTurnsRemaining > 0) {
            growthTurnsRemaining--;
            setRemainingTurns(getRemainingTurns() + 1);
        } else {
            super.tick();
        }
        peakStacks = Math.max(peakStacks, getRemainingTurns());
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
        spreadToNearbyEnemies(state);
    }

    /**
     * The bloom ran its full course - burst, unless a dispel is what ended it. Cleansing
     * the debuff off is meant to deny the spread entirely, which is the whole counterplay
     * to the ability.
     */
    @Override
    public void onExpire(GameState state) {
        if (wasDispelled()) {
            return;
        }
        spreadToNearbyEnemies(state);
    }

    private void spreadToNearbyEnemies(GameState state) {
        if (spread || getOwner() == null || getOwner().getPosition() == null) {
            return;
        }
        // Stacks still on the host if it died mid-bloom; otherwise the bloom decayed to
        // 0 to expire, so carry its peak instead - "spreads what it grew into" rather
        // than the 0 it necessarily holds at the moment it runs out.
        int stacks = getRemainingTurns() > 0 ? getRemainingTurns() : peakStacks;
        if (stacks <= 0) {
            return;
        }
        spread = true;
        for (Unit victim : state.getMap().getUnitsInRadius(getOwner().getPosition(), infectRadius)) {
            // "Nearby enemies" means enemies of the caster (Spitter) - i.e. other units on the
            // host's own team, not the host's allies.
            if (victim == getOwner() || victim.getTeam() == caster.getTeam() || victim.isDead()) {
                continue;
            }
            PoisonEffect.applyOrExtend(victim, caster, stacks, getDamagePerTurn());
        }
    }
}
