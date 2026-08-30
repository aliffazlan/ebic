package com.walnutt.effect.impl;

/**
 * Shawl's Insight pool, the currency Hidden Potential spends to unlock an ally's ability.
 *
 * A marker subclass rather than a bare ResourceEffect for the same reason InspirationEffect
 * is one: Unit.getActiveEffect returns the FIRST active match, so anything that ever gives
 * Shawl a second ResourceEffect - a mimicked Eureka, a future currency - would otherwise be
 * indistinguishable from his own pool, and which one came back would depend on insertion
 * order.
 */
public class InsightEffect extends ResourceEffect {

    public InsightEffect(String description, int nextThreshold) {
        super("Insight", description, "upgrade", nextThreshold);
    }
}
