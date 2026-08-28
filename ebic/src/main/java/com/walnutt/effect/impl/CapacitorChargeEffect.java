package com.walnutt.effect.impl;

/**
 * Maxwell's Capacitor Bank, holding charges that pay for an ability in place of an action.
 *
 * A ResourceEffect for the same reason Inspiration is one - being an Effect is what puts
 * the running count in the sidebar with no new UI - but with a ceiling, which the base
 * class deliberately has no notion of. See InspirationEffect for why both are marker
 * subclasses rather than two bare ResourceEffects.
 */
public class CapacitorChargeEffect extends ResourceEffect {
    private final int maxCharges;

    public CapacitorChargeEffect(String description, int maxCharges) {
        super("Charge", description, "free cast", 1);
        this.maxCharges = maxCharges;
    }

    public int getMaxCharges() {
        return maxCharges;
    }

    public boolean isFull() {
        return getAmount() >= maxCharges;
    }

    /** Clamped at the bank's capacity - charges past it are simply not stored. */
    @Override
    public void add(int gained) {
        if (gained <= 0) {
            return;
        }
        super.add(Math.min(gained, Math.max(0, maxCharges - getAmount())));
    }

    /** One charge is one free cast, so the count IS the number of free casts available. */
    @Override
    public int freeCastCharges() {
        return getAmount();
    }

    @Override
    public String getExtraInfo() {
        return "Charges: " + getAmount() + " / " + maxCharges
            + (getAmount() > 0 ? " - next ability costs no action" : "");
    }
}
