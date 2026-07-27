package com.walnutt.web.dto;

import java.util.List;

/** remainingTurns is capped/labeled by the mapper for effectively-permanent effects (see Effect.PERMANENT). */
public record EffectSnapshot(
    String name,
    String description,
    String category,
    boolean permanent,
    int remainingTurns,
    List<String> statusFlags
) {
}
