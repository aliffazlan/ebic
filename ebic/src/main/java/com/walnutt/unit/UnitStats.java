package com.walnutt.unit;

/** Immutable base stats - the "original" values some abilities (Decay, Cripple) reference directly. */
public record UnitStats(int strength, int agility, int intelligence, int maxHealth) {
}
