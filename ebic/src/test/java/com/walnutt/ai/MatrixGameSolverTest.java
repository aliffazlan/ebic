package com.walnutt.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

/**
 * The solver's whole reason to exist is exactness, so these assert the defining property
 * of an equilibrium - neither side can profit by deviating - rather than comparing
 * against precomputed numbers. A test that only pinned expected outputs would pass just
 * as happily for a solver that was subtly wrong in a way the examples didn't reach.
 */
class MatrixGameSolverTest {
    private static final double TOLERANCE = 1e-6;

    @Test
    void rockPaperScissorsSolvesToTheUniformMixture() {
        double[][] rps = {{0, -1, 1}, {1, 0, -1}, {-1, 1, 0}};
        MatrixGameSolver.Solution solution = MatrixGameSolver.solve(rps);

        assertEquals(0, solution.value(), TOLERANCE);
        for (int i = 0; i < 3; i++) {
            assertEquals(1.0 / 3, solution.rowStrategy()[i], TOLERANCE);
            assertEquals(1.0 / 3, solution.columnStrategy()[i], TOLERANCE);
        }
    }

    @Test
    void aGameWithASaddlePointSolvesToPureStrategies() {
        // Row 1 / column 0 is a saddle: the row minimum of row 1 is also the column maximum of column 0.
        double[][] payoff = {{1, 2, 3}, {4, 5, 6}, {2, 3, 4}};
        MatrixGameSolver.Solution solution = MatrixGameSolver.solve(payoff);

        assertEquals(4, solution.value(), TOLERANCE);
        assertEquals(1.0, solution.rowStrategy()[1], TOLERANCE);
        assertEquals(1.0, solution.columnStrategy()[0], TOLERANCE);
    }

    @Test
    void anAllZeroGameIsStillSolvable() {
        // A unit with no attribute values at all - degenerate, but it must not fall over.
        assertEquals(0, MatrixGameSolver.solve(new double[3][3]).value(), TOLERANCE);
        assertIsEquilibrium(new double[3][3]);
    }

    @Test
    void everyRandomlyGeneratedGameSolvesToAGenuineEquilibrium() {
        Random random = new Random(20260827L);
        for (int trial = 0; trial < 5000; trial++) {
            double[][] payoff = new double[3][3];
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    payoff[i][j] = random.nextInt(80);
                }
            }
            assertIsEquilibrium(payoff);
        }
    }

    /** Probabilities are valid, and no pure deviation by either side beats the value. */
    private void assertIsEquilibrium(double[][] payoff) {
        MatrixGameSolver.Solution solution = MatrixGameSolver.solve(payoff);
        double[] row = solution.rowStrategy();
        double[] column = solution.columnStrategy();

        assertEquals(1.0, sum(row), TOLERANCE, "row strategy must be a distribution");
        assertEquals(1.0, sum(column), TOLERANCE, "column strategy must be a distribution");
        for (double p : row) {
            assertTrue(p >= -TOLERANCE, "negative probability in row strategy");
        }
        for (double p : column) {
            assertTrue(p >= -TOLERANCE, "negative probability in column strategy");
        }

        for (int i = 0; i < payoff.length; i++) {
            double deviation = 0;
            for (int j = 0; j < payoff[0].length; j++) {
                deviation += payoff[i][j] * column[j];
            }
            assertTrue(deviation <= solution.value() + TOLERANCE,
                "maximizer could beat the value by switching to row " + i);
        }
        for (int j = 0; j < payoff[0].length; j++) {
            double deviation = 0;
            for (int i = 0; i < payoff.length; i++) {
                deviation += row[i] * payoff[i][j];
            }
            assertTrue(deviation >= solution.value() - TOLERANCE,
                "minimizer could undercut the value by switching to column " + j);
        }
    }

    private double sum(double[] values) {
        double total = 0;
        for (double v : values) {
            total += v;
        }
        return total;
    }
}
