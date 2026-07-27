package com.walnutt.web.dto;

import java.util.List;

/**
 * This player's own draft round, plus the opponent's same-round options shown
 * alongside for transparency (the whole pool is pre-allocated at match start -
 * see ConcurrentSetupFlow.allocate - so showing the opponent's pair needs no
 * synchronization with their actual progress; they may not have reached this
 * round yet). This is the shape sent by WebInputHandler.choosePick (the
 * ConcurrentSetupHandler path, used by Game.newConcurrentFullDraftMatch).
 * Deliberately a separate record from the older DraftRoundSnapshot
 * (yourOptions/opponentOptions), which is still used by
 * WebRenderer.renderDraftRound to satisfy the Renderer interface for the
 * untouched hotseat-terminal DraftFlow/newFullDraftMatch path - same wire
 * "type": "draft_round" tag, different payload shape depending on which flow
 * produced it, but the two Java code paths never run against the same live
 * connection in production.
 */
public record PlayerDraftRoundSnapshot(
    String roundLabel,
    List<UnitDefinitionSnapshot> options,
    List<UnitDefinitionSnapshot> opponentOptions
) {
}
