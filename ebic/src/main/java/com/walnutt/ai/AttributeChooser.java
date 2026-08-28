package com.walnutt.ai;

import java.util.Comparator;
import java.util.List;
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

    private AttributeChooser() {
    }

    /**
     * {@code [attackerAttribute][defenderAttribute] -> damage dealt}, mirroring
     * combat.EncounterResolver.calculateDamage: a winning attribute deals its own
     * value, a losing one deals nothing, and a mirror match deals the difference.
     *
     * Rows and columns cover only each side's USABLE attributes (see
     * Unit.getUsableAttributes), not all three - an attribute a unit has none of is not a
     * strategy available to it, and including it as a zero-value row would let the solver
     * hand back a mixture that plays it. Use {@link #attackerOptions}/{@link
     * #defenderOptions} to map an index back to an attribute.
     */
    public static double[][] payoffMatrix(Unit attacker, Unit defender) {
        List<Attribute> rows = attacker.getUsableAttributes();
        List<Attribute> cols = defender.getUsableAttributes();
        double[][] payoff = new double[rows.size()][cols.size()];
        for (int i = 0; i < rows.size(); i++) {
            for (int j = 0; j < cols.size(); j++) {
                Attribute attackAttr = rows.get(i);
                Attribute defendAttr = cols.get(j);
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

    /** The attributes the payoff matrix's rows stand for, in order. */
    public static List<Attribute> attackerOptions(Unit attacker) {
        return attacker.getUsableAttributes();
    }

    /** The attributes the payoff matrix's columns stand for, in order. */
    public static List<Attribute> defenderOptions(Unit defender) {
        return defender.getUsableAttributes();
    }

    /**
     * Damage this encounter is worth if both sides play optimally - the value of the
     * game. {@link ActionScorer} uses it to price an attack, which means the bot values
     * attacks by what they are actually worth against a competent opponent rather than
     * by a best case it has no way to force.
     */
    public static double expectedDamage(Unit attacker, Unit defender) {
        List<Attribute> attackerUsable = attacker.getUsableAttributes();
        if (attackerUsable.isEmpty()) {
            return 0; // nothing to swing with
        }
        if (!defender.hasUsableAttribute()) {
            // Undefendable: whichever attribute the attacker picks lands in full, so the
            // encounter is worth its best one. Solving a zero-column matrix is undefined.
            return attackerUsable.stream().mapToDouble(attacker::getAttributeValue).max().orElse(0);
        }
        return MatrixGameSolver.solve(payoffMatrix(attacker, defender)).value();
    }

    /** Samples this side's equilibrium mixture, restricted to attributes it can actually use. */
    public static Attribute choose(Unit self, Unit opponent, Role role, Random random) {
        List<Attribute> own = self.getUsableAttributes();
        if (own.isEmpty()) {
            return null; // cannot bring anything - see combat.EncounterResolver
        }
        if (!opponent.hasUsableAttribute()) {
            // The opponent has no strategy to solve against. As attacker, just take the
            // biggest hit available; as defender, nothing can be blocked anyway.
            return role == Role.ATTACKER
                ? own.stream().max(Comparator.comparingInt(self::getAttributeValue)).orElse(own.get(0))
                : own.get(0);
        }

        double[][] payoff = role == Role.ATTACKER
            ? payoffMatrix(self, opponent)
            : payoffMatrix(opponent, self);
        MatrixGameSolver.Solution solution = MatrixGameSolver.solve(payoff);
        double[] mixture = role == Role.ATTACKER ? solution.rowStrategy() : solution.columnStrategy();
        return sample(own, mixture, random);
    }

    private static Attribute sample(List<Attribute> options, double[] mixture, Random random) {
        double roll = random.nextDouble();
        double cumulative = 0;
        for (int i = 0; i < mixture.length && i < options.size(); i++) {
            cumulative += mixture[i];
            if (roll < cumulative) {
                return options.get(i);
            }
        }
        // Floating-point drift only; the mixture is normalized, so this is the last bucket.
        return options.get(options.size() - 1);
    }
}
