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
    private final int growthPerTurn;
    private final int casterHitBonus;
    private final int infectRadius;
    private boolean spread;

    public PoisonBloomEffect(Unit caster, int duration, int growthPerTurn, int casterHitBonus, int infectRadius) {
        super("Poison Bloom",
            "The poison in this unit is growing instead of fading, gaining " + growthPerTurn
                + " stack(s) a turn and " + casterHitBonus + " more each time the caster hits it. "
                + "When the bloom ends - or the host dies - it bursts, spreading the poison to nearby "
                + "enemies. Cleansing the bloom first prevents the burst.",
            duration);
        this.caster = caster;
        this.growthPerTurn = growthPerTurn;
        this.casterHitBonus = casterHitBonus;
        this.infectRadius = infectRadius;
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
        burst(state);
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
        Unit host = getOwner();
        if (spread || host == null || host.getPosition() == null) {
            return;
        }
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
        }
    }

    private PoisonEffect poisonOnHost() {
        return getOwner() == null ? null : PoisonEffect.on(getOwner());
    }
}
