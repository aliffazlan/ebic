package com.walnutt.web.dto;

/**
 * Static ability info for a not-yet-drafted unit definition (draft/opponent-options
 * cards) - unlike AbilitySnapshot, there's no live match instance yet, so no
 * ready/currentCooldown, just the nominal cooldown (or passive) a player would want
 * to see before picking.
 */
public record AbilityPreviewSnapshot(
    String id,
    String name,
    String description,
    boolean passive,
    int cooldown
) {
}
