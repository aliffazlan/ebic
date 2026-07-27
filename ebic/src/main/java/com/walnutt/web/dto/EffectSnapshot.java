package com.walnutt.web.dto;

import java.util.List;

/**
 * remainingTurns is capped/labeled by the mapper for effectively-permanent effects
 * (see Effect.PERMANENT). extraInfo is null unless the effect has real per-instance
 * dynamic state worth surfacing beyond the static description (see Effect.getExtraInfo).
 */
public record EffectSnapshot(
    String name,
    String description,
    String category,
    boolean permanent,
    int remainingTurns,
    List<String> statusFlags,
    String extraInfo
) {
}
