package com.walnutt.web.dto;

import java.util.List;

public record UnitDefinitionSnapshot(
    String definitionId,
    String name,
    String type,
    int maxHp,
    int strength,
    int agility,
    int intelligence,
    int attackRange,
    List<AbilityPreviewSnapshot> abilities
) {
}
