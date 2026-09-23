package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Ember's hidden heat gauge, one per (victim, Ember) pair. Records how much damage that
 * Ember has dealt to this unit and reports when a threshold's worth has built up.
 *
 * Crossing the threshold always empties the gauge outright - 130 damage at a threshold of
 * 80 fires once and the other 50 is lost, whether or not Overheat is upgraded. A unit can
 * only overheat once per turn, so a large hit never cashes in several at once. That cap is
 * what keeps the loop linear instead of runaway.
 *
 * NEUTRAL and non-dispellable so a cleanse can't wipe the gauge.
 */
public class OverheatTrackerEffect extends Effect {
    private final Unit source;
    private final int threshold;
    private int accumulated;
    private boolean proccedThisTurn;

    public OverheatTrackerEffect(Unit source, int threshold) {
        super("Overheating",
            "Heat builds as " + source.getName() + " deals damage to this unit. At " + threshold
                + " accumulated damage it overheats, setting nearby enemies alight.",
            Effect.PERMANENT);
        this.source = source;
        this.threshold = threshold;
        this.category = EffectCategory.NEUTRAL;
        this.dispellable = false;
    }

    @Override
    public Unit getSource() {
        return source;
    }

    public int getAccumulated() {
        return accumulated;
    }

    public void add(int damage) {
        if (damage > 0) {
            accumulated += damage;
        }
    }

    /** True at most once per turn. Always empties the gauge outright on a successful proc. */
    public boolean consumeProc() {
        if (proccedThisTurn || accumulated < threshold) {
            return false;
        }
        accumulated = 0;
        proccedThisTurn = true;
        return true;
    }

    @Override
    public String getExtraInfo() {
        return "Heat: " + accumulated + " / " + threshold;
    }

    /**
     * Clears the once-per-turn latch. Called explicitly by Overheat at the start of this
     * unit's turn rather than from a turn hook here: the event bus fans out unit by unit,
     * so an owning ability on another unit could otherwise try to consume a proc before
     * this effect had reset itself.
     */
    public void beginTurn() {
        proccedThisTurn = false;
    }
}
