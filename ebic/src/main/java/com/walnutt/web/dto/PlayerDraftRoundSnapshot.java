package com.walnutt.web.dto;

import java.util.List;

/**
 * One player's own draft round only - see API_CONTRACT.md's rewritten
 * DraftRoundSnapshot ({roundLabel, options}, no opponentOptions). This is the
 * shape sent by WebInputHandler.choosePick (the new ConcurrentSetupHandler
 * path, used by Game.newConcurrentFullDraftMatch). Deliberately a separate
 * record from the older DraftRoundSnapshot (yourOptions/opponentOptions),
 * which is still used by WebRenderer.renderDraftRound to satisfy the Renderer
 * interface for the untouched hotseat-terminal DraftFlow/newFullDraftMatch
 * path - same wire "type": "draft_round" tag, different payload shape
 * depending on which flow produced it, but the two Java code paths never run
 * against the same live connection in production.
 */
public record PlayerDraftRoundSnapshot(
    String roundLabel,
    List<UnitDefinitionSnapshot> options
) {
}
