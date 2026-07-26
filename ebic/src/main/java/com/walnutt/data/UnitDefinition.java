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
    List<String> abilities
) {
}
