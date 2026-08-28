package com.walnutt.web.dto;

import java.util.List;
import java.util.Map;

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
    /** See AbilitySnapshot.details/stats - the same verbose-tooltip payload, before the match. */
    List<String> details,
    Map<String, Double> stats,
    boolean passive,
    int cooldown
) {
}
