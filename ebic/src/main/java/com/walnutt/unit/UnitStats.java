package com.walnutt.unit;

/** Immutable base stats - the "original" values some abilities (Decay, Cripple) reference directly. */
public record UnitStats(int strength, int agility, int intelligence, int maxHealth, int attackRange) {

    /** Melee by default - most units never specify a range, so this is the common case. */
    public UnitStats(int strength, int agility, int intelligence, int maxHealth) {
        this(strength, agility, intelligence, maxHealth, 1);
    }
}
