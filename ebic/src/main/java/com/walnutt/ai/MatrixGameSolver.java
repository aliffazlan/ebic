package com.walnutt.ai;

/**
 * Exact solver for a small two-player zero-sum matrix game, by support enumeration.
 *
 * Rows are the maximizer, columns the minimizer, and {@code payoff[i][j]} is what the
 * maximizer gains. Every finite zero-sum game has a value and a pair of optimal mixed
 * strategies (von Neumann); support enumeration finds them exactly rather than
 * approaching them iteratively, which matters because the point of using it at all is
 * to be unexploitable rather than merely good.
 *
 * Sized for 3x3 (the attribute encounter - see {@link AttributeChooser}), where there
 * are only 19 support pairs to test, so the brute force is free. It is written
 * generally enough for any small n, but is not intended for large matrices.
 */
public final class MatrixGameSolver {
    private static final double EPSILON = 1e-9;

    /**
     * @param rowStrategy probability the maximizer plays each row
     * @param columnStrategy probability the minimizer plays each column
     * @param value the game's value - the payoff the maximizer secures against optimal play
     */
    public record Solution(double[] rowStrategy, double[] columnStrategy, double value) {
    }

    private MatrixGameSolver() {
    }

    public static Solution solve(double[][] payoff) {
        int rows = payoff.length;
        int cols = payoff[0].length;

        // Smallest supports first: a pure-strategy (saddle point) equilibrium is both the
        // common case and the cleanest answer, so prefer it before considering mixtures.
        for (int size = 1; size <= Math.min(rows, cols); size++) {
            for (int[] rowSupport : combinations(rows, size)) {
                for (int[] colSupport : combinations(cols, size)) {
                    Solution candidate = trySupport(payoff, rowSupport, colSupport);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        }
        // Unreachable for a well-formed finite game; degrade to uniform rather than throw,
        // because a bot that cannot pick an attribute would stall the whole match.
        return new Solution(uniform(rows), uniform(cols), 0);
    }

    /**
     * An equilibrium on these supports, or null if there isn't one. The defining
     * conditions: every strategy inside the support earns exactly the game value, every
     * probability is non-negative, and nothing outside the support does better.
     */
    private static Solution trySupport(double[][] payoff, int[] rowSupport, int[] colSupport) {
        int size = rowSupport.length;

        // Column player's mixture: makes every supported row earn the same value.
        double[][] colSystem = new double[size + 1][size + 2];
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                colSystem[r][c] = payoff[rowSupport[r]][colSupport[c]];
            }
            colSystem[r][size] = -1;             // the shared value, moved to the left side
            colSystem[r][size + 1] = 0;
        }
        for (int c = 0; c < size; c++) {
            colSystem[size][c] = 1;              // probabilities sum to one
        }
        colSystem[size][size + 1] = 1;
        double[] colSolution = solveLinearSystem(colSystem);
        if (colSolution == null) {
            return null;
        }

        // Row player's mixture: makes every supported column concede the same value.
        double[][] rowSystem = new double[size + 1][size + 2];
        for (int c = 0; c < size; c++) {
            for (int r = 0; r < size; r++) {
                rowSystem[c][r] = payoff[rowSupport[r]][colSupport[c]];
            }
            rowSystem[c][size] = -1;
            rowSystem[c][size + 1] = 0;
        }
        for (int r = 0; r < size; r++) {
            rowSystem[size][r] = 1;
        }
        rowSystem[size][size + 1] = 1;
        double[] rowSolution = solveLinearSystem(rowSystem);
        if (rowSolution == null) {
            return null;
        }

        double value = colSolution[size];
        if (Math.abs(value - rowSolution[size]) > 1e-6) {
            return null;
        }

        double[] rowStrategy = new double[payoff.length];
        for (int r = 0; r < size; r++) {
            if (rowSolution[r] < -EPSILON) {
                return null;
            }
            rowStrategy[rowSupport[r]] = Math.max(0, rowSolution[r]);
        }
        double[] columnStrategy = new double[payoff[0].length];
        for (int c = 0; c < size; c++) {
            if (colSolution[c] < -EPSILON) {
                return null;
            }
            columnStrategy[colSupport[c]] = Math.max(0, colSolution[c]);
        }

        // No unsupported row may beat the value, and no unsupported column may undercut it -
        // without this check a support can satisfy the equalities yet not be an equilibrium.
        for (int i = 0; i < payoff.length; i++) {
            double payoffAgainstColumns = 0;
            for (int j = 0; j < columnStrategy.length; j++) {
                payoffAgainstColumns += payoff[i][j] * columnStrategy[j];
            }
            if (payoffAgainstColumns > value + 1e-6) {
                return null;
            }
        }
        for (int j = 0; j < payoff[0].length; j++) {
            double payoffAgainstRows = 0;
            for (int i = 0; i < rowStrategy.length; i++) {
                payoffAgainstRows += rowStrategy[i] * payoff[i][j];
            }
            if (payoffAgainstRows < value - 1e-6) {
                return null;
            }
        }

        normalize(rowStrategy);
        normalize(columnStrategy);
        return new Solution(rowStrategy, columnStrategy, value);
    }

    /** Gaussian elimination with partial pivoting on an augmented matrix; null if singular. */
    private static double[] solveLinearSystem(double[][] augmented) {
        int n = augmented.length;
        double[][] m = new double[n][];
        for (int i = 0; i < n; i++) {
            m[i] = augmented[i].clone();
        }

        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(m[row][col]) > Math.abs(m[pivot][col])) {
                    pivot = row;
                }
            }
            if (Math.abs(m[pivot][col]) < 1e-12) {
                return null;
            }
            double[] swap = m[col];
            m[col] = m[pivot];
            m[pivot] = swap;

            for (int row = 0; row < n; row++) {
                if (row == col) {
                    continue;
                }
                double factor = m[row][col] / m[col][col];
                if (factor == 0) {
                    continue;
                }
                for (int c = col; c <= n; c++) {
                    m[row][c] -= factor * m[col][c];
                }
            }
        }

        double[] solution = new double[n];
        for (int i = 0; i < n; i++) {
            solution[i] = m[i][n] / m[i][i];
        }
        return solution;
    }

    private static void normalize(double[] distribution) {
        double total = 0;
        for (double p : distribution) {
            total += p;
        }
        if (total <= 0) {
            java.util.Arrays.fill(distribution, 1.0 / distribution.length);
            return;
        }
        for (int i = 0; i < distribution.length; i++) {
            distribution[i] /= total;
        }
    }

    private static double[] uniform(int n) {
        double[] result = new double[n];
        java.util.Arrays.fill(result, 1.0 / n);
        return result;
    }

    /** All size-k subsets of [0, n), as index arrays. */
    private static java.util.List<int[]> combinations(int n, int k) {
        java.util.List<int[]> result = new java.util.ArrayList<>();
        int[] current = new int[k];
        buildCombinations(n, k, 0, 0, current, result);
        return result;
    }

    private static void buildCombinations(int n, int k, int start, int depth, int[] current,
                                           java.util.List<int[]> result) {
        if (depth == k) {
            result.add(current.clone());
            return;
        }
        for (int i = start; i < n; i++) {
            current[depth] = i;
            buildCombinations(n, k, i + 1, depth + 1, current, result);
        }
    }
}
