package com.walnutt.web.dto;

public record PlacementUnitSnapshot(
    String unitId,
    String name,
    String definitionId,
    String unitType,
    int q,
    int r
) {
}
