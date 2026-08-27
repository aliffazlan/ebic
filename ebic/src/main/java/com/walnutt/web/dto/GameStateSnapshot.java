package com.walnutt.web.dto;

import java.util.List;

public record GameStateSnapshot(
    String currentTeam,
    int remainingMoves,
    boolean gameOver,
    int mapRadius,
    int mapRowLimit,
    List<UnitSnapshot> units,
    List<TileEffectSnapshot> tileEffects
) {
}
