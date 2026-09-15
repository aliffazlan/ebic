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
        super("Infernal Blade",
            "A stacking curse that disarms the target. The countdown only ticks down on turns the "
                + "cursed unit ends away from Lucifer - staying adjacent to him pauses it.",
            duration);
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
            int remaining = Math.max(0, getRemainingTurns() - 1);
            setRemainingTurns(remaining);
            if (remaining <= 0) {
                // tick() is a no-op here (see class comment) - this onTurnEnd hook is the only
                // place the duration ever actually decrements, and it's dispatched via
                // TurnEndEvent, published AFTER Unit.endTurn's own sweep has already run for
                // this turn - so without this, the curse would linger until the owner's next
                // scheduled sweep, a full opponent turn away.
                expireNow(state);
            }
        }
    }

    /** If the target is already cursed, stack duration onto the existing instance instead of refreshing it. */
    public static void applyOrExtend(Unit target, Unit source, int duration) {
        // Nothing to refresh alongside the duration: the brand holds no tuning of its own, and
        // the upgrade's extra damage and stun are dealt by the ability at strike time rather than
        // stored here. See Effect.extendDuration for why that is worth saying out loud.
        target.getActiveEffect(InfernalBladeEffect.class).ifPresentOrElse(
            existing -> existing.extendDuration(duration),
            () -> target.addEffect(new InfernalBladeEffect(source, duration)));
    }
}
