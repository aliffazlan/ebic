package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DeathEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Spitter's Poison Bloom: a bloom growing in an already-poisoned unit.
 *
 * Deliberately NOT a PoisonEffect and deliberately deals no damage of its own - Poison is
 * the status that hurts, and this is a separate effect that works on it. While the bloom
 * lasts it feeds the poison instead of letting it fade, each of the caster's attacks feeds
 * it further, and when the bloom finishes it bursts, spreading that poison to nearby
 * enemies. Cleansing the bloom denies the burst.
 */
public class PoisonBloomEffect extends Effect {
    private final Unit caster;
    private final int initialDuration;
    private final int growthPerTurn;
    private final int casterHitBonus;
    private final int infectRadius;
    /** Upgrade: whether a host that dies passes the bloom on, not merely the poison. */
    private final boolean bloomSpreads;
    /** True for a bloom caught from another host - it lays no fresh poison of its own. */
    private final boolean secondGeneration;
    private boolean spread;

    public PoisonBloomEffect(Unit caster, int duration, int growthPerTurn, int casterHitBonus, int infectRadius) {
        this(caster, duration, growthPerTurn, casterHitBonus, infectRadius, false, false);
    }

    public PoisonBloomEffect(Unit caster, int duration, int growthPerTurn, int casterHitBonus,
                              int infectRadius, boolean bloomSpreads, boolean secondGeneration) {
        super("Poison Bloom",
            "The poison in this unit is growing instead of fading, gaining " + growthPerTurn
                + " stack(s) a turn and " + casterHitBonus + " more each time the caster hits it. "
                + "When the bloom ends - or the host dies - it bursts, spreading the poison to nearby "
                + "enemies. Cleansing the bloom first prevents the burst.",
            duration);
        this.caster = caster;
        this.initialDuration = duration;
        this.growthPerTurn = growthPerTurn;
        this.casterHitBonus = casterHitBonus;
        this.infectRadius = infectRadius;
        this.bloomSpreads = bloomSpreads;
        this.secondGeneration = secondGeneration;
        this.category = EffectCategory.DEBUFF;
    }

    @Override
    public String getExtraInfo() {
        PoisonEffect poison = poisonOnHost();
        String stacks = poison == null ? "no poison to feed" : "feeding " + poison.getRemainingTurns() + " stacks";
        return stacks + " - bursts in " + getRemainingTurns() + " turn(s)";
    }

    /** Grows the poison rather than carrying any stacks itself. */
    @Override
    public void tick() {
        super.tick();
        PoisonEffect poison = poisonOnHost();
        if (poison != null) {
            poison.feed(growthPerTurn);
        }
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        if (event.attacker() != caster || event.defender() != getOwner()) {
            return;
        }
        PoisonEffect poison = poisonOnHost();
        if (poison != null) {
            poison.extendDuration(casterHitBonus);
        }
    }

    @Override
    public void onDeath(GameState state, DeathEvent event) {
        if (event.unit() != getOwner()) {
            return;
        }
        burst(state, true);
    }

    /**
     * The bloom ran its course - burst, unless a dispel is what ended it. Cleansing is
     * meant to deny the spread entirely, which is the counterplay to the ability.
     */
    @Override
    public void onExpire(GameState state) {
        if (wasDispelled()) {
            return;
        }
        burst(state);
    }

    /**
     * Spreads the host's poison to nearby enemies. The latch matters because a host that
     * dies under the bloom still sits in Player.getUnits() and keeps ticking, so its
     * onExpire would otherwise land a second burst a few turns after the death burst.
     */
    private void burst(GameState state) {
        burst(state, false);
    }

    private int burstDuration = 1;

    private int getRemainingTurnsAtBurst() {
        return burstDuration;
    }

    private void burst(GameState state, boolean hostDied) {
        Unit host = getOwner();
        if (spread || host == null || host.getPosition() == null) {
            return;
        }
        // A bloom that burst has already run down to 0; the copies it seeds need a life of
        // their own, taken from what this one was originally worth.
        burstDuration = Math.max(1, initialDuration);
        PoisonEffect poison = poisonOnHost();
        if (poison == null || poison.getRemainingTurns() <= 0) {
            return;
        }
        spread = true;
        int stacks = poison.getRemainingTurns();
        for (Unit victim : state.getMap().getUnitsInRadius(host.getPosition(), infectRadius)) {
            // "Nearby enemies" means enemies of the caster - i.e. other units on the host's
            // own team, not the host's allies.
            if (victim == host || victim.getTeam() == caster.getTeam() || victim.isDead()) {
                continue;
            }
            PoisonEffect.applyOrExtend(victim, caster, stacks, poison.getDamagePerTurn());
            // Upgraded, and only when the HOST DIED: the bloom itself takes root in everyone the
            // burst reached. A bloom caught this way lays down no poison of its own on arrival,
            // but will burst and spread again in its turn - hence secondGeneration.
            if (bloomSpreads && hostDied && victim.getActiveEffect(PoisonBloomEffect.class).isEmpty()) {
                victim.addEffect(new PoisonBloomEffect(caster, getRemainingTurnsAtBurst(),
                    growthPerTurn, casterHitBonus, infectRadius, true, true));
            }
        }
    }

    private PoisonEffect poisonOnHost() {
        return getOwner() == null ? null : PoisonEffect.on(getOwner());
    }

    @Override
    public Unit getSource() {
        return caster;
    }
}
