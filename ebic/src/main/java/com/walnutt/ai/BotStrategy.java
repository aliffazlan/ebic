package com.walnutt.ai;

import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.ui.ActionChoice;

/**
 * How the bot picks its next action. The seam that lets the bot get smarter without
 * anything around it changing.
 *
 * {@link GreedyStrategy} scores each candidate in isolation, which is as far as the
 * engine currently allows: judging a *sequence* of actions requires simulating them,
 * and GameState has no deep copy yet. Once it does, a search-based implementation
 * slots in here using the same {@link PositionEvaluator} at its leaves, and greedy
 * survives as the easier difficulty.
 */
public interface BotStrategy {
    /** The next action for this player, or {@link ActionChoice#endTurn()} when nothing is worth doing. */
    ActionChoice decide(GameState state, Player player);
}
