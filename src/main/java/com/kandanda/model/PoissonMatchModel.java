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

    /**
     * Dixon-Coles low-score correlation parameter (rho). rho=0 is pure independent
     * Poisson (unchanged behaviour). A small negative rho (≈ -0.1) nudges the four
     * low-score cells toward how real football behaves: more 0-0 and 1-1, fewer 1-0/0-1.
     * Only those four cells are touched; the grid is then renormalised to sum to 1.
     */
    private final double rho;

    /** Pure-Poisson model (rho = 0): preserves all original behaviour. */
    public PoissonMatchModel(double leagueAverageGoals) {
        this(leagueAverageGoals, 0.0);
    }

    public PoissonMatchModel(double leagueAverageGoals, double rho) {
        if (leagueAverageGoals <= 0) {
            throw new IllegalArgumentException("leagueAverageGoals must be > 0");
        }
        this.leagueAverageGoals = leagueAverageGoals;
        this.rho = rho;
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
        double total = 0.0;
        for (int i = 0; i <= MAX_GOALS; i++) {
            for (int j = 0; j <= MAX_GOALS; j++) {
                double p = homeProbs[i] * awayProbs[j] * tau(i, j, lambdaHome, lambdaAway);
                grid[i][j] = p;
                total += p;
            }
        }
        // The tau correction shifts mass around, so the grid no longer sums to exactly
        // 1. Renormalise to restore a valid probability distribution. (No-op when rho=0,
        // since tau is then identically 1 and total is already ~1.)
        if (total > 0) {
            for (int i = 0; i <= MAX_GOALS; i++) {
                for (int j = 0; j <= MAX_GOALS; j++) {
                    grid[i][j] /= total;
                }
            }
        }
        return new ScorelineGrid(lambdaHome, lambdaAway, grid);
    }

    /**
     * Dixon-Coles tau correction factor for a cell. Equals 1 (no change) everywhere
     * except the four low-score corners. With rho=0 this is always 1.
     */
    private double tau(int i, int j, double lambdaHome, double lambdaAway) {
        if (i == 0 && j == 0) return 1.0 - lambdaHome * lambdaAway * rho;
        if (i == 0 && j == 1) return 1.0 + lambdaHome * rho;
        if (i == 1 && j == 0) return 1.0 + lambdaAway * rho;
        if (i == 1 && j == 1) return 1.0 - rho;
        return 1.0;
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