package com.walnutt.web.dto;

/**
 * A persistent effect attached to a board tile rather than to a unit - Ember's Eruption
 * leaves burning ground behind. Engine-side these live as an effect on the caster that
 * remembers a position; this flattens them into something the client can paint directly.
 */
public record TileEffectSnapshot(
    int q,
    int r,
    String kind,
    String name,
    int remainingTurns
) {
}
