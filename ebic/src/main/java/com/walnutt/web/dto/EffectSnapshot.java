package com.walnutt.web.dto;

import java.util.List;

/**
 * remainingTurns is capped/labeled by the mapper for effectively-permanent effects
 * (see Effect.PERMANENT). extraInfo is null unless the effect has real per-instance
 * dynamic state worth surfacing beyond the static description (see Effect.getExtraInfo).
 * partnerUnitId is null except for Duel and Static Link, whose visuals need to know
 * which other unit they're paired with (see GameStateSnapshotMapper.toEffectSnapshot).
 */
public record EffectSnapshot(
    String name,
    String description,
    String category,
    boolean permanent,
    int remainingTurns,
    List<String> statusFlags,
    String extraInfo,
    String partnerUnitId
) {
}
