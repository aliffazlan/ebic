package com.walnutt.status;

public enum Stat {
    STRENGTH,
    AGILITY,
    INTELLIGENCE,
    MAX_HEALTH,
    /**
     * How far away a unit's basic Attack can reach, in hex tiles. Modelled as a Stat
     * (rather than a plain field) so a temporary buff is just a StatModifier on an
     * Effect, inheriting stacking, expiry and dispel for free - see Steady Focus.
     * A minimum range is deliberately NOT a Stat: minimums aggregate by max, not by
     * sum, so that lives on Effect.getMinAttackRange() instead.
     */
    ATTACK_RANGE,
    /**
     * Bonus tiles added to every one of a unit's ability ranges (Maxwell's Gyroscope).
     * Base 0 for everyone - unlike the other stats there is no per-unit value in
     * UnitStats, since "how far does this ability reach" belongs to the ability, not the
     * unit. Ability.getRange() adds this on top, so a unit-level bonus lifts every
     * ability it owns at once, including any gained later in the match.
     *
     * A basic Attack is deliberately unaffected: Attack overrides getRange() to read
     * ATTACK_RANGE instead, so the two ranges stay separately tunable.
     */
    CAST_RANGE
}
