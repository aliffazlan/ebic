package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/**
 * Lucifer's Infernal Blade curse: disarms the target for a stacking duration that
 * only counts down on turns where the cursed unit ends its own turn NOT adjacent
 * to Lucifer - staying close pauses the countdown. tick() is overridden to a no-op
 * and the real decrement happens in onTurnEnd instead, since that's the only hook
 * handed the GameState the adjacency check needs.
 */
public class InfernalBladeEffect extends Effect {
    private final Unit source;

    public InfernalBladeEffect(Unit source, int duration) {
        super("Infernal Blade", duration);
        this.source = source;
        this.flags.add(StatusFlag.DISARMED);
        this.category = EffectCategory.DEBUFF;
    }

    @Override
    public void tick() {
    }

    @Override
    public void onTurnEnd(GameState state, TurnEndEvent event) {
        Unit owner = getOwner();
        if (owner == null || isExpired() || event.team() != owner.getTeam()) {
            return;
        }
        boolean pausedBySource = source != null && !source.isDead()
            && state.getMap().areAdjacent(owner.getPosition(), source.getPosition());
        if (!pausedBySource) {
            setRemainingTurns(Math.max(0, getRemainingTurns() - 1));
        }
    }

    /** If the target is already cursed, stack duration onto the existing instance instead of refreshing it. */
    public static void applyOrExtend(Unit target, Unit source, int duration) {
        for (Effect effect : target.getEffects()) {
            if (effect instanceof InfernalBladeEffect existing) {
                existing.extendDuration(duration);
                return;
            }
        }
        target.addEffect(new InfernalBladeEffect(source, duration));
    }
}
