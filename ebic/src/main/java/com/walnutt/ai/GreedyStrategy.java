package com.walnutt.ai;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.ui.ActionChoice;

/**
 * Takes the single best-scoring action available right now, re-evaluated from scratch
 * after every action.
 *
 * The one piece of real strategy it encodes is ordering: cost-free actions are taken
 * first, before any of the turn's three move points are committed. BASIC attacks cost
 * nothing (Attack.getMoveCost returns 0 for them), so any of them worth making is
 * strictly free value and can never compete with a move point. Human players routinely
 * leave these on the table; the bot never will, and that alone accounts for much of its
 * strength.
 *
 * No lookahead - see {@link BotStrategy} for why, and what replaces this later.
 */
public final class GreedyStrategy implements BotStrategy {
    private final BotConfig config;
    private final ActionScorer scorer;

    public GreedyStrategy(BotConfig config) {
        this.config = config;
        this.scorer = new ActionScorer(config, new PositionEvaluator(config));
    }

    @Override
    public ActionChoice decide(GameState state, Player player) {
        List<Candidate> candidates = LegalActionEnumerator.enumerate(state, player);
        if (candidates.isEmpty()) {
            return ActionChoice.endTurn();
        }

        if (config.takeFreeAttacks()) {
            Candidate free = best(state, candidates, true);
            if (free != null) {
                return free.toActionChoice();
            }
        }

        Candidate paid = best(state, candidates, false);
        return paid == null ? ActionChoice.endTurn() : paid.toActionChoice();
    }

    /**
     * Highest-scoring affordable candidate, or null if none clears the bar. Free actions
     * only need to be worth *something*; paid ones must beat minimumActionScore, since
     * spending a move point on a marginal action costs the chance to do better with it.
     */
    private Candidate best(GameState state, List<Candidate> candidates, boolean freeOnly) {
        double threshold = freeOnly ? 0 : config.minimumActionScore();
        List<Candidate> affordable = new ArrayList<>();
        Candidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Candidate candidate : candidates) {
            int cost = candidate.moveCost(state);
            if (freeOnly ? cost != 0 : cost == 0) {
                continue;
            }
            if (!state.canSpendMoves(cost)) {
                continue;
            }
            // Legality is re-confirmed here, not assumed from enumeration: TurnManager
            // silently re-prompts on an illegal action, so a bot that returned one would
            // spin forever rather than fail.
            if (!candidate.ability().canUse(state, candidate.target())) {
                continue;
            }
            double score = scorer.score(state, candidate);
            // A hint returns -infinity to mean "never, under any circumstances" - friendly
            // fire, mainly, since several abilities (Blizzard, Soul Rip) will happily
            // target your own units. That veto has to hold even when blundering, so these
            // are excluded from the random pool too, not merely from the best-scoring one.
            if (score == Double.NEGATIVE_INFINITY || Double.isNaN(score)) {
                continue;
            }
            affordable.add(candidate);

            if (score <= threshold) {
                continue;
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        // Drawn from everything legal rather than from what scored well, so that
        // blunderRate 1.0 is genuinely random play - the baseline the scoring has to beat
        // for any of it to be worth keeping. Sampling only good moves would have the
        // "random" opponent quietly using the very scoring under test.
        if (!affordable.isEmpty() && config.blunderRate() > 0
            && state.getRandom().nextDouble() < config.blunderRate()) {
            return affordable.get(state.getRandom().nextInt(affordable.size()));
        }
        return best;
    }
}
