package com.walnutt.web.dto;

import java.util.List;

public record UnitSnapshot(
    String id,
    String name,
    String definitionId,
    String team,
    String unitType,
    int q,
    int r,
    int currentHp,
    int maxHp,
    int strength,
    int agility,
    int intelligence,
    int attackRange,
    int minAttackRange,
    boolean dead,
    boolean hasMovedThisTurn,
    boolean hasAttackedThisTurn,
    List<String> statusFlags,
    List<AbilitySnapshot> abilities,
    List<EffectSnapshot> effects
) {
}
