package com.walnutt.data;

import java.util.Map;

/**
 * Parsed shape of design_ideas/abilities/<unit>/<ability>.json. "stats" is a loose
 * map (not a fixed record) because every ability tunes different numbers - the
 * hand-written Ability/Effect subclass knows which keys it needs.
 */
public record AbilityDefinition(String name, String type, String description, Map<String, Double> stats) {

    public double getDouble(String key, double fallback) {
        if (stats == null || !stats.containsKey(key)) {
            return fallback;
        }
        return stats.get(key);
    }

    public int getInt(String key, int fallback) {
        return (int) Math.round(getDouble(key, fallback));
    }

    public boolean isPassive() {
        return "passive".equalsIgnoreCase(type);
    }
}
