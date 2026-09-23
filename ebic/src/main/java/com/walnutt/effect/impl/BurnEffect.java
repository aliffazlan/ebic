package com.walnutt.effect.impl;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Ember's shared stacking burn. Not an ability in its own right - Fireblast, Eruption
 * and Overheat all funnel through {@link #apply}, and its numbers come from
 * design_ideas/abilities/ember/burn.json, which the loader already parks in the ability
 * definition map under "burn" even though nothing constructs an Ability from it.
 *
 * Damage is attributed to the caster, which is what lets Overheat count burn ticks
 * toward its own threshold.
 */
public class BurnEffect extends Effect {
    public static final String CAUSE_LABEL = "Burn";
    private static final int DEFAULT_DAMAGE = 10;
    private static final int DEFAULT_DURATION = 3;

    private final Unit source;
    private final int damagePerStack;
    private final int baseDuration;
    private int stacks;

    public BurnEffect(Unit source, int damagePerStack, int baseDuration) {
        super("Burn", "Deals " + damagePerStack + " damage per stack at the start of this unit's turn. "
            + "Fresh stacks refresh the timer rather than extending it.", baseDuration);
        this.source = source;
        this.damagePerStack = damagePerStack;
        this.baseDuration = baseDuration;
        this.category = EffectCategory.DEBUFF;
    }

    @Override
    public Unit getSource() {
        return source;
    }

    public int getStacks() {
        return stacks;
    }

    /** Note this REFRESHES the duration rather than extending it, unlike PoisonEffect. */
    public void addStacks(int amount) {
        if (amount <= 0) {
            return;
        }
        stacks += amount;
        setRemainingTurns(baseDuration);
    }

    @Override
    public String getExtraInfo() {
        return "Burn x" + stacks + " (" + (stacks * damagePerStack) + " damage next turn)";
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit owner = getOwner();
        if (owner == null || owner.isDead() || isExpired() || event.team() != owner.getTeam()) {
            return;
        }
        int damage = stacks * damagePerStack;
        if (damage <= 0) {
            return;
        }
        DamageEvent burn = new DamageEvent(source, owner, damage);
        burn.setCauseLabel(CAUSE_LABEL);
        owner.takeDamage(state, burn);
    }

    /**
     * Adds stacks from `source`, creating the effect if the target isn't burning yet.
     * Matched on source so two Embers can't share one stack counter, and tolerant of a
     * GameState with no ability definitions loaded so unit tests need no JSON wiring.
     */
    public static void apply(GameState state, Unit target, Unit source, int stacks) {
        if (target == null || target.isDead() || stacks <= 0) {
            return;
        }
        AbilityDefinition definition = state.getAbilityDefinitions().get("burn");
        int damage = definition == null ? DEFAULT_DAMAGE : definition.getInt("damage", DEFAULT_DAMAGE);
        int duration = definition == null ? DEFAULT_DURATION : definition.getInt("duration", DEFAULT_DURATION);

        BurnEffect existing = null;
        for (Effect effect : target.getEffects()) {
            if (!effect.isExpired() && effect instanceof BurnEffect burn && burn.source == source) {
                existing = burn;
                break;
            }
        }
        if (existing == null) {
            existing = new BurnEffect(source, damage, duration);
            target.addEffect(existing);
        }
        existing.addStacks(stacks);
    }
}
