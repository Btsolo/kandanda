package com.kandanda.model;

import com.kandanda.rating.TeamRating;

/**
 * Turns two teams' strength ratings into a {@link ScorelineGrid} using the
 * independent-Poisson model — the foundation Dixon-Coles is built on.
 *
 * <h2>The two steps</h2>
 * <ol>
 *   <li><b>Expected goals.</b> For each side:
 *       {@code expected = own_attack × opponent_defence × leagueAverageGoals}.
 *       (No home advantage: World Cup venues are neutral. For club football this
 *       gains a home factor.)</li>
 *   <li><b>Poisson.</b> Given a side's expected goals λ, the probability of exactly
 *       k goals is {@code λ^k · e^−λ / k!}. The home and away counts are treated as
 *       independent, so the probability of scoreline (i, j) is P(home=i) · P(away=j).</li>
 * </ol>
 *
 * <p><b>Independence assumption.</b> Plain Poisson assumes the two teams' goal counts
 * are independent. They are not perfectly so (low-scoring results are slightly more
 * correlated than this predicts). The Dixon-Coles refinement (next story) applies a
 * small correction to the 0-0/1-0/0-1/1-1 cells to fix exactly this. We build the
 * plain version first to see it work, then measure whether the correction helps.
 *
 * <p>Verified against textbook Poisson values and symmetry (equal teams ⇒ equal
 * win/loss) in a prototype before this port.
 */
public class PoissonMatchModel {

    /** Goals-per-team-per-game baseline, learned from the fitted dataset. */
    private final double leagueAverageGoals;

    /** Grid truncation: 0..MAX goals per side. 10 captures essentially all the mass. */
    private static final int MAX_GOALS = 10;

    public PoissonMatchModel(double leagueAverageGoals) {
        if (leagueAverageGoals <= 0) {
            throw new IllegalArgumentException("leagueAverageGoals must be > 0");
        }
        this.leagueAverageGoals = leagueAverageGoals;
    }

    /**
     * Build the scoreline grid for a fixture.
     *
     * @param home rating of the home (or, at neutral venue, nominal first) team
     * @param away rating of the away team
     */
    public ScorelineGrid predict(TeamRating home, TeamRating away) {
        double lambdaHome = home.attack() * away.defence() * leagueAverageGoals;
        double lambdaAway = away.attack() * home.defence() * leagueAverageGoals;
        return buildGrid(lambdaHome, lambdaAway);
    }

    /** Build a grid directly from two expected-goals values (used in tests). */
    public ScorelineGrid buildGrid(double lambdaHome, double lambdaAway) {
        double[] homeProbs = poissonVector(lambdaHome);
        double[] awayProbs = poissonVector(lambdaAway);

        double[][] grid = new double[MAX_GOALS + 1][MAX_GOALS + 1];
        for (int i = 0; i <= MAX_GOALS; i++) {
            for (int j = 0; j <= MAX_GOALS; j++) {
                grid[i][j] = homeProbs[i] * awayProbs[j];
            }
        }
        return new ScorelineGrid(lambdaHome, lambdaAway, grid);
    }

    /** P(0..MAX goals) for a given expected-goals rate. */
    private double[] poissonVector(double lambda) {
        double[] p = new double[MAX_GOALS + 1];
        for (int k = 0; k <= MAX_GOALS; k++) {
            p[k] = poisson(k, lambda);
        }
        return p;
    }

    /** Poisson PMF: P(k) = lambda^k · e^(-lambda) / k!  — computed stably without overflow. */
    static double poisson(int k, double lambda) {
        // Compute via logs to avoid lambda^k overflowing for large k.
        double logP = k * Math.log(lambda) - lambda - logFactorial(k);
        return Math.exp(logP);
    }

    private static double logFactorial(int n) {
        double sum = 0.0;
        for (int i = 2; i <= n; i++) sum += Math.log(i);
        return sum;
    }
}
