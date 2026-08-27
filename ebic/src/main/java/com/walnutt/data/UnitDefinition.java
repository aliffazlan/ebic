package com.walnutt.data;

import java.util.List;

import com.google.gson.annotations.SerializedName;

/** Parsed shape of design_ideas/units/*.json. */
public record UnitDefinition(
    String name,
    String type,
    @SerializedName("max_hp") int maxHp,
    int strength,
    int agility,
    int intelligence,
    @SerializedName("attack_range") int attackRange,
    List<String> abilities
) {

    /** Keeps the pre-attack_range call sites (mostly tests) constructing melee units unchanged. */
    public UnitDefinition(String name, String type, int maxHp, int strength, int agility, int intelligence,
                          List<String> abilities) {
        this(name, type, maxHp, strength, agility, intelligence, 1, abilities);
    }

    /**
     * Gson leaves an absent int at 0, and most unit JSONs omit attack_range entirely,
     * so 0 means "unspecified", not "cannot reach anything". Read range through this
     * rather than the raw accessor - the raw one is what equals/hashCode/toString use.
     */
    public int effectiveAttackRange() {
        return attackRange <= 0 ? 1 : attackRange;
    }
}
