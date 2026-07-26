package com.walnutt.web.dto;

/** amount is null where not applicable (e.g. ability_used with no direct damage/heal number). */
public record VfxEvent(
    String type,
    String abilityId,
    String sourceUnitId,
    String targetUnitId,
    Integer amount
) {
}
