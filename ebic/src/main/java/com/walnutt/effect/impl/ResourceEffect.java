package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.status.EffectCategory;

/**
 * A currency that accrues on a unit and is spent by one of its abilities - Maxwell's
 * Inspiration, and the shape Shawl's Insight is meant to reuse.
 *
 * Modelled as an Effect rather than a plain field on the owning ability specifically so
 * the count is visible: every Effect is already mapped into UnitSnapshot.effects and
 * rendered in the sidebar with a hover tooltip, so the player sees the running total and
 * what it is building toward without a single line of new UI. Nothing else about an
 * Effect is wanted here, hence PERMANENT/NEUTRAL/non-dispellable - it must never tick
 * away, never be cleansed, and never be sped up or slowed down by Chronos's Dilation.
 *
 * Look one up with {@code owner.getActiveEffect(ResourceEffect.class)}.
 */
public class ResourceEffect extends Effect {
    private final String unitLabel;
    private int amount;
    private int nextThreshold;

    /**
     * @param name          display name, e.g. "Inspiration"
     * @param unitLabel     what the threshold is spent on, e.g. "gadget" - only used in the tooltip
     * @param nextThreshold what the owning ability currently charges; update via setNextThreshold
     */
    public ResourceEffect(String name, String description, String unitLabel, int nextThreshold) {
        super(name, description, Effect.PERMANENT);
        this.unitLabel = unitLabel;
        this.nextThreshold = nextThreshold;
        this.category = EffectCategory.NEUTRAL;
        this.dispellable = false;
    }

    public int getAmount() {
        return amount;
    }

    public void add(int gained) {
        if (gained > 0) {
            amount += gained;
        }
    }

    /** Spends {@code cost} if it is affordable, reporting whether it was. */
    public boolean spend(int cost) {
        if (cost > amount) {
            return false;
        }
        amount -= cost;
        return true;
    }

    public boolean canAfford(int cost) {
        return amount >= cost;
    }

    public int getNextThreshold() {
        return nextThreshold;
    }

    /** Kept in step by the owning ability whenever its price changes (Eureka's rising cost). */
    public void setNextThreshold(int nextThreshold) {
        this.nextThreshold = nextThreshold;
    }

    @Override
    public String getExtraInfo() {
        if (nextThreshold <= 0) {
            return getName() + ": " + amount;
        }
        return getName() + ": " + amount + " (next " + unitLabel + " at " + nextThreshold + ")";
    }
}
