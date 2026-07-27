package com.walnutt.ui;

import java.util.List;
import java.util.Map;

import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;

/**
 * Drives one player's draft+placement independently of the other player's pace -
 * see game/ConcurrentSetupFlow, which calls this once per player from that player's
 * own dedicated setup thread. A single implementation instance is shared across both
 * players' threads (same pattern as InputHandler); it's responsible for its own
 * internal routing/synchronization per player, same as InputHandler's implementations
 * already do via the Player parameter.
 */
public interface ConcurrentSetupHandler {
    /**
     * One draft round for this player (their own pre-allocated pair for this round -
     * see ConcurrentSetupFlow's upfront allocation), plus the opponent's pair for the
     * same round shown alongside for transparency (the whole pool is already decided
     * at match start, so showing it costs nothing and needs no synchronization with
     * the opponent's actual progress - they may not have reached this round yet).
     * Unlike the old DraftFlow/Renderer.renderDraftRound split, showing the options
     * and blocking for the pick are the same call, since there's no cross-player
     * synchronized reveal moment anymore.
     */
    UnitDefinition choosePick(GameState state, Player player, String roundLabel,
                               List<UnitDefinition> options, List<UnitDefinition> opponentOptions);

    /**
     * Shows this player their default arrangement (champion/elites/basics already
     * laid out - see game/DefaultArrangement) and blocks until they confirm a final
     * arrangement, which may differ from the default via edits (swap/move) made in
     * the meantime. The engine applies the returned positions to the map itself.
     */
    Map<Unit, Position> arrangePlacement(GameState state, Player player, Map<Unit, Position> defaultArrangement);
}
