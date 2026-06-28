package com.kandanda.scoreboard;

/**
 * The output of the scoreboard: calibration metrics over a set of predictions.
 *
 * <p>All "lower is better" metrics. {@code marketBrier} is NaN when none of the
 * scored predictions carried a market-implied probability to compare against.
 *
 * @param count        number of predictions scored
 * @param brier        mean Brier score of our model (lower is better; 0.25 = coin-flip baseline)
 * @param logLoss      mean log-loss of our model (lower is better; punishes confident errors)
 * @param marketBrier  mean Brier score of the market on the subset with market data, or NaN
 */
public record CalibrationReport(
        int count,
        double brier,
        double logLoss,
        double marketBrier
) {
    /** True if we beat the market on Brier score over the comparable subset. */
    public boolean beatsMarket() {
        return !Double.isNaN(marketBrier) && brier < marketBrier;
    }

    @Override
    public String toString() {
        String market = Double.isNaN(marketBrier)
                ? "n/a"
                : String.format("%.4f (we %s)", marketBrier, beatsMarket() ? "BEAT it" : "lost");
        return String.format(
                "CalibrationReport[n=%d, brier=%.4f, logLoss=%.4f, marketBrier=%s]",
                count, brier, logLoss, market);
    }
}
