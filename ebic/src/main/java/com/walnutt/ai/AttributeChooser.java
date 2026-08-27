package com.walnutt.ai;

import java.util.Random;

import com.walnutt.combat.Attribute;
import com.walnutt.unit.Unit;

/**
 * Picks an attribute for one side of an encounter, by solving the encounter exactly.
 *
 * The attribute pick is a simultaneous, hidden choice, so there is no "best" pure
 * answer - any deterministic rule is exploitable by an opponent who notices it. But
 * the payoff structure is fully known to both sides (all three attribute values of
 * both units are public, and damage is a closed form), and only the attacker deals
 * damage, so the defender's payoff is exactly the negation of the attacker's. That
 * makes it a 3x3 zero-sum matrix game, which has an exactly computable Nash
 * equilibrium - see {@link MatrixGameSolver}.
 *
 * So the bot plays the equilibrium mixture. This is not a heuristic that later gets
 * improved: it is optimal now and stays optimal, and no opponent - human or future
 * search-based bot - can gain an edge against it in this sub-game.
 *
 * The payoff matrix mirrors combat.EncounterResolver's damage rule exactly. If that
 * rule ever changes, {@link #payoffMatrix} must change with it; AttributeChooserTest
 * pins the two together.
 */
public final class AttributeChooser {
    /** Which side of the encounter the chooser is picking for. The matrix is not symmetric, so this matters. */
    public enum Role { ATTACKER, DEFENDER }

    private static final Attribute[] ATTRIBUTES = Attribute.values();

    private AttributeChooser() {
    }

    /**
     * {@code [attackerAttribute][defenderAttribute] -> damage dealt}, mirroring
     * combat.EncounterResolver.calculateDamage: a winning attribute deals its own
     * value, a losing one deals nothing, and a mirror match deals the difference.
     */
    public static double[][] payoffMatrix(Unit attacker, Unit defender) {
        double[][] payoff = new double[ATTRIBUTES.length][ATTRIBUTES.length];
        for (int i = 0; i < ATTRIBUTES.length; i++) {
            for (int j = 0; j < ATTRIBUTES.length; j++) {
                Attribute attackAttr = ATTRIBUTES[i];
                Attribute defendAttr = ATTRIBUTES[j];
                if (attackAttr.beats(defendAttr)) {
                    payoff[i][j] = attacker.getAttributeValue(attackAttr);
                } else if (defendAttr.beats(attackAttr)) {
                    payoff[i][j] = 0;
                } else {
                    payoff[i][j] = Math.max(0,
                        attacker.getAttributeValue(attackAttr) - defender.getAttributeValue(defendAttr));
                }
            }
        }
        return payoff;
    }

    /**
     * Damage this encounter is worth if both sides play optimally - the value of the
     * game. {@link ActionScorer} uses it to price an attack, which means the bot values
     * attacks by what they are actually worth against a competent opponent rather than
     * by a best case it has no way to force.
     */
    public static double expectedDamage(Unit attacker, Unit defender) {
        return MatrixGameSolver.solve(payoffMatrix(attacker, defender)).value();
    }

    /** Samples this side's equilibrium mixture. */
    public static Attribute choose(Unit self, Unit opponent, Role role, Random random) {
        double[][] payoff = role == Role.ATTACKER
            ? payoffMatrix(self, opponent)
            : payoffMatrix(opponent, self);
        MatrixGameSolver.Solution solution = MatrixGameSolver.solve(payoff);
        double[] mixture = role == Role.ATTACKER ? solution.rowStrategy() : solution.columnStrategy();
        return sample(mixture, random);
    }

    private static Attribute sample(double[] mixture, Random random) {
        double roll = random.nextDouble();
        double cumulative = 0;
        for (int i = 0; i < mixture.length; i++) {
            cumulative += mixture[i];
            if (roll < cumulative) {
                return ATTRIBUTES[i];
            }
        }
        // Floating-point drift only; the mixture is normalized, so this is the last bucket.
        return ATTRIBUTES[ATTRIBUTES.length - 1];
    }
}
