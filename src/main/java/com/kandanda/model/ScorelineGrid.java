package com.kandanda.model;

/**
 * A probability distribution over every scoreline of a match.
 *
 * <p>{@code grid[i][j]} = probability the home team scores exactly {@code i} and the
 * away team exactly {@code j}. The grid is truncated at {@link #maxGoals} per side
 * (goals beyond that are vanishingly unlikely and folded into the truncation).
 *
 * <p>This single object is the source of EVERY match-level betting market — each market
 * is just a different sum over these cells (done in S5). That is the whole reason the
 * model is built scoreline-first: one distribution, all markets.
 *
 * @param homeExpectedGoals the home team's expected goals (lambda_home)
 * @param awayExpectedGoals the away team's expected goals (lambda_away)
 * @param grid              [maxGoals+1][maxGoals+1] probabilities, summing to ~1.0
 */
public record ScorelineGrid(
        double homeExpectedGoals,
        double awayExpectedGoals,
        double[][] grid
) {
    /** Highest goal count per team the grid explicitly represents. */
    public int maxGoals() {
        return grid.length - 1;
    }

    /** Probability of an exact scoreline, e.g. p(2,1). Returns 0 outside the grid. */
    public double probabilityOf(int homeGoals, int awayGoals) {
        if (homeGoals < 0 || awayGoals < 0
                || homeGoals >= grid.length || awayGoals >= grid.length) {
            return 0.0;
        }
        return grid[homeGoals][awayGoals];
    }

    /** Total probability mass in the grid. Should be very close to 1.0 — a sanity check. */
    public double totalProbability() {
        double sum = 0.0;
        for (double[] row : grid) {
            for (double p : row) sum += p;
        }
        return sum;
    }
}
