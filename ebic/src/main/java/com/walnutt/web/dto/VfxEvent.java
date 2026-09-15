package com.walnutt.web.dto;

/**
 * amount is null where not applicable (e.g. ability_used with no direct damage/heal
 * number). causeLabel is only populated on "damage" events (e.g. "Attack", "Poison",
 * "Counterstrike") - the human-readable source for a combat log line; null elsewhere.
 * redirectedFromUnitId is only populated on a "damage" event whose damage was passed on
 * by a redirect mechanic (Refraction) - names the unit that redirected it, not the
 * original attacker (sourceUnitId) or the unit it landed on (targetUnitId); null elsewhere.
 */
public record VfxEvent(
    String type,
    String abilityId,
    String sourceUnitId,
    String targetUnitId,
    Integer amount,
    String causeLabel,
    String redirectedFromUnitId
) {
}
