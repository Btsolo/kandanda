package com.kandanda.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Derives individual betting-market probabilities from a {@link ScorelineGrid}.
 *
 * <h2>Design: individual methods, one shared engine</h2>
 * Every market is the same operation — sum the grid cells that satisfy a condition —
 * differing only in the condition. So each public method (the market you actually call)
 * is one line that hands its condition to the private {@link #sum} engine. You get
 * granular per-market access with zero duplicated loops; adding the Dixon-Coles
 * correction later means changing the grid in ONE place, not fifteen.
 *
 * <p>The condition is a {@link BiPredicate}: given (homeGoals, awayGoals) it returns
 * whether that scoreline counts toward the market. e.g. home win is {@code (h,a) -> h > a}.
 *
 * <p>All formulas were verified against a prototype: every partition (1X2, O/U, BTTS,
 * odd/even) sums to 1.0, confirming the markets are mutually exclusive and exhaustive.
 */
public class MarketCalculator {

    private final ScorelineGrid grid;

    public MarketCalculator(ScorelineGrid grid) {
        this.grid = grid;
    }

    // ---- the shared engine ---------------------------------------------------

    /** Sum the probability of every scoreline satisfying the given condition. */
    private double sum(BiPredicate<Integer, Integer> condition) {
        double total = 0.0;
        int max = grid.maxGoals();
        for (int h = 0; h <= max; h++) {
            for (int a = 0; a <= max; a++) {
                if (condition.test(h, a)) {
                    total += grid.probabilityOf(h, a);
                }
            }
        }
        return total;
    }

    // ---- 1X2 -----------------------------------------------------------------

    public double homeWin() { return sum((h, a) -> h > a); }
    public double draw()    { return sum((h, a) -> h.equals(a)); }
    public double awayWin() { return sum((h, a) -> h < a); }

    // ---- Double chance -------------------------------------------------------

    /** Home or draw (1X). */
    public double doubleChanceHomeOrDraw() { return sum((h, a) -> h >= a); }
    /** Draw or away (X2). */
    public double doubleChanceDrawOrAway() { return sum((h, a) -> h <= a); }
    /** Home or away (12) — i.e. not a draw. */
    public double doubleChanceHomeOrAway() { return sum((h, a) -> !h.equals(a)); }

    // ---- Draw no bet (stake refunded on draw, so renormalise without draws) --

    public double drawNoBetHome() {
        double hw = homeWin(), aw = awayWin();
        double denom = hw + aw;
        return denom > 0 ? hw / denom : 0.0;
    }
    public double drawNoBetAway() {
        double hw = homeWin(), aw = awayWin();
        double denom = hw + aw;
        return denom > 0 ? aw / denom : 0.0;
    }

    // ---- Over / Under (any line, e.g. 2.5) -----------------------------------

    public double over(double line)  { return sum((h, a) -> h + a > line); }
    public double under(double line) { return sum((h, a) -> h + a < line); }

    // ---- Both teams to score -------------------------------------------------

    public double bttsYes() { return sum((h, a) -> h > 0 && a > 0); }
    public double bttsNo()  { return sum((h, a) -> h == 0 || a == 0); }

    // ---- Correct score -------------------------------------------------------

    /** Probability of an exact scoreline. */
    public double correctScore(int homeGoals, int awayGoals) {
        return grid.probabilityOf(homeGoals, awayGoals);
    }

    // ---- Winning margin ------------------------------------------------------

    /** Home team wins by exactly {@code margin} goals (margin > 0). */
    public double homeWinByExactly(int margin) {
        return sum((h, a) -> h - a == margin);
    }
    /** Away team wins by exactly {@code margin} goals (margin > 0). */
    public double awayWinByExactly(int margin) {
        return sum((h, a) -> a - h == margin);
    }

    // ---- Odd / even total goals ----------------------------------------------

    public double oddTotalGoals()  { return sum((h, a) -> (h + a) % 2 == 1); }
    public double evenTotalGoals() { return sum((h, a) -> (h + a) % 2 == 0); }

    // ---- Convenience bundle (for scoring many markets at once, S6) -----------

    /**
     * Compute the headline markets together as a named map. Thin convenience over the
     * individual methods above — used when we want to score everything in one pass.
     */
    public Map<String, Double> headlineMarkets() {
        Map<String, Double> m = new LinkedHashMap<>();
        m.put("Home win", homeWin());
        m.put("Draw", draw());
        m.put("Away win", awayWin());
        m.put("Double chance 1X", doubleChanceHomeOrDraw());
        m.put("Double chance X2", doubleChanceDrawOrAway());
        m.put("Over 2.5", over(2.5));
        m.put("Under 2.5", under(2.5));
        m.put("BTTS yes", bttsYes());
        m.put("BTTS no", bttsNo());
        m.put("Odd goals", oddTotalGoals());
        m.put("Even goals", evenTotalGoals());
        return m;
    }
}
