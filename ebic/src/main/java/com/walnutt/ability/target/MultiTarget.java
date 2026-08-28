package com.walnutt.ability.target;

/**
 * Two targets picked together for a single cast - Maxwell's Translocation names a unit
 * (primary) and a destination tile (secondary).
 *
 * Composing existing Target types rather than adding a bespoke unit-plus-tile record
 * keeps this open to other combinations later, and means nothing that merely passes a
 * Target around needs to change.
 *
 * An ability using this must override {@link com.walnutt.ability.Ability#getLegalTargets}:
 * the default enumeration only ever builds single-shape candidates, so it would report a
 * multi-target ability as having no legal targets at all.
 */
public record MultiTarget(Target primary, Target secondary) implements Target {
}
