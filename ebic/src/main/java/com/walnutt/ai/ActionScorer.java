package com.walnutt.ai;

import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;

/**
 * Prices one candidate action, in "expected damage" units so every kind of action is
 * comparable against every other.
 *
 * Three cases, in descending confidence: an attack can be priced almost exactly (the
 * encounter is a solvable game - see {@link AttributeChooser}), a move can be priced
 * geometrically, and an ability is priced by whatever {@link AbilityHints} knows about
 * it. Anything scoring {@link Double#NEGATIVE_INFINITY} is a move the bot must never
 * make - a hint uses that to veto, e.g. rooting its own teammate.
 */
public final class ActionScorer {
    private final BotConfig config;
    private final PositionEvaluator evaluator;

    public ActionScorer(BotConfig config, PositionEvaluator evaluator) {
        this.config = config;
        this.evaluator = evaluator;
    }

    public double score(GameState state, Candidate candidate) {
        if (candidate.ability() instanceof Attack) {
            return scoreAttack(state, candidate);
        }
        if (candidate.ability() instanceof Move) {
            return scoreMove(state, candidate);
        }
        return AbilityHints.forAbility(candidate.ability().getName())
            .score(new HintContext(state, candidate.unit(), candidate.ability(), candidate.target(),
                config, evaluator));
    }

    /**
     * Expected damage against optimal defence, weighted by how badly this particular
     * enemy needs removing, plus a large bonus when the hit is actually expected to
     * finish them - and a far larger one when finishing them wins the match.
     */
    private double scoreAttack(GameState state, Candidate candidate) {
        if (!(candidate.target() instanceof UnitTarget unitTarget)) {
            return Double.NEGATIVE_INFINITY;
        }
        Unit defender = unitTarget.getUnit();
        double expected = AttributeChooser.expectedDamage(candidate.unit(), defender);
        HintContext context = context(state, candidate);

        double score = expected * config.damageWeight() * context.targetPriority(defender);
        if (expected >= defender.getHealth() && defender.getHealth() > 0) {
            score += config.killBonus();
            if (context.isChampion(defender)) {
                score += config.killBonus() * config.championKillMultiplier();
            }
        }
        return score;
    }

    /**
     * Movement is the one action with no immediate payoff, so it is priced by what it
     * sets up: closing on the enemy the mover cares about, and especially stepping into
     * range of something it could not previously hit. Without the range term a bot
     * shuffles toward the enemy forever without ever choosing the step that lets it
     * actually swing next turn.
     */
    private double scoreMove(GameState state, Candidate candidate) {
        if (!(candidate.target() instanceof TileTarget tileTarget)) {
            return Double.NEGATIVE_INFINITY;
        }
        Unit mover = candidate.unit();
        Position from = mover.getPosition();
        Position to = tileTarget.getTile().getPosition();
        if (from == null) {
            return Double.NEGATIVE_INFINITY;
        }

        Unit focus = focusTarget(state, mover);
        if (focus == null || focus.getPosition() == null) {
            return 0;
        }

        int before = state.getMap().getDistance(from, focus.getPosition());
        int after = state.getMap().getDistance(to, focus.getPosition());
        double score = (before - after) * config.approachWeight();

        boolean couldReachBefore = false;
        boolean couldReachAfter = false;
        for (Unit enemy : state.getAllActiveUnits()) {
            if (enemy.getTeam() == mover.getTeam() || enemy.isDead()) {
                continue;
            }
            couldReachBefore |= Attack.canReachFrom(state, mover, from, enemy);
            couldReachAfter |= Attack.canReachFrom(state, mover, to, enemy);
        }
        if (couldReachAfter && !couldReachBefore) {
            score += config.enterRangeBonus();
        }
        // Stepping out of a position that was already working is usually a mistake.
        if (couldReachBefore && !couldReachAfter) {
            score -= config.enterRangeBonus();
        }

        // Walking a nearly-dead unit deeper into the enemy is how armies get picked apart.
        double wounded = context(state, candidate).woundedFraction(mover);
        int enemiesAdjacentAfter = state.getMap().getAdjacentUnits(to, u -> u.getTeam() != mover.getTeam() && !u.isDead()).size();
        score -= wounded * enemiesAdjacentAfter * config.exposurePenalty();

        return score;
    }

    /**
     * Which enemy this unit should walk toward: the most worth attacking, discounted by
     * how far away it is, so units engage what is actually in front of them instead of
     * all trekking across the map toward the champion.
     */
    private Unit focusTarget(GameState state, Unit mover) {
        Unit best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Unit enemy : state.getAllActiveUnits()) {
            if (enemy.getTeam() == mover.getTeam() || enemy.isDead() || enemy.getPosition() == null) {
                continue;
            }
            int distance = state.getMap().getDistance(mover.getPosition(), enemy.getPosition());
            double typeWeight = switch (enemy.getUnitType()) {
                case CHAMPION -> config.championTargetWeight();
                case ELITE -> config.eliteTargetWeight();
                case BASIC -> config.basicTargetWeight();
            };
            double score = (typeWeight * 10 + evaluator.threat(enemy)) / (1 + distance);
            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        return best;
    }

    private HintContext context(GameState state, Candidate candidate) {
        return new HintContext(state, candidate.unit(), candidate.ability(), candidate.target(),
            config, evaluator);
    }
}
