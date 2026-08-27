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
    ATTACK_RANGE
}
