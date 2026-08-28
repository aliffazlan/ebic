package com.walnutt.effect.impl;

/**
 * Maxwell's Inspiration pool, the currency Eureka spends to construct gadgets.
 *
 * A marker subclass rather than a bare ResourceEffect purely so it can be looked up
 * unambiguously: Unit.getActiveEffect returns the FIRST active match, and Maxwell now
 * carries a second ResourceEffect once he builds the Capacitor Bank. Relying on insertion
 * order to keep those apart would work today and break the moment anything reorders, which
 * is exactly why EnergyShieldEffect exists alongside BarrierEffect.
 */
public class InspirationEffect extends ResourceEffect {

    public InspirationEffect(String description, int nextThreshold) {
        super("Inspiration", description, "gadget", nextThreshold);
    }
}
