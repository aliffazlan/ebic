package com.walnutt.status;

public record StatModifier(Stat stat, ModifierType type, double amount, Object source) {

    public enum ModifierType {
        FLAT,
        PERCENT
    }

    public static StatModifier flat(Stat stat, double amount, Object source) {
        return new StatModifier(stat, ModifierType.FLAT, amount, source);
    }

    public static StatModifier percent(Stat stat, double amount, Object source) {
        return new StatModifier(stat, ModifierType.PERCENT, amount, source);
    }
}
