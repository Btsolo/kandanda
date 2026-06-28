package com.kandanda.scoreboard;

import java.util.List;

/**
 * The Scoreboard. Computes calibration metrics over a set of {@link Prediction}s.
 *
 * <p>This class is built <em>first</em>, before any predictor exists, on purpose.
 * It is the judge. Per the project's Definition of Done, no predictive feature is
 * accepted unless it has been run through this scoreboard. This is what separates
 * the project from Klement: we never accept a model on a story, only on measured
 * calibration.
 *
 * <h2>The two metrics</h2>
 * <p><b>Brier score</b>: mean of (predictedProb - actual)^2 where actual is 1.0 if the
 * event happened, else 0.0. Range [0,1], lower is better. A model that always says 0.5
 * scores exactly 0.25 — that is the baseline any useful model must beat.
 *
 * <p><b>Log-loss</b>: mean of -ln(p_assigned_to_truth). If the event happened we use
 * -ln(predictedProb); if it didn't, -ln(1 - predictedProb). It is unbounded above and
 * punishes confident wrong calls savagely (predict 0.99, be wrong -> ~4.6). This is the
 * metric that keeps an overconfident model honest in a luck-heavy tournament.
 *
 * <p>Probabilities are clamped away from exactly 0 and 1 before taking logs, because
 * ln(0) is -infinity. A model should never be infinitely confident anyway (NFR5).
 */
public final class CalibrationService {

    /** Clamp epsilon: keeps log-loss finite and reflects that no claim is ever 100% certain. */
    private static final double EPS = 1e-15;

    /**
     * Score a set of predictions.
     *
     * @param predictions the recorded predictions (each with its outcome already known)
     * @return a {@link CalibrationReport} with Brier, log-loss, and the market's Brier
     *         over the comparable subset
     * @throws IllegalArgumentException if the list is null or empty
     */
    public CalibrationReport score(List<Prediction> predictions) {
        if (predictions == null || predictions.isEmpty()) {
            throw new IllegalArgumentException("Cannot score an empty set of predictions");
        }

        double brierSum = 0.0;
        double logLossSum = 0.0;

        double marketBrierSum = 0.0;
        int marketCount = 0;

        for (Prediction p : predictions) {
            double actual = p.outcome() ? 1.0 : 0.0;

            // Brier: squared error against the 0/1 outcome.
            double err = p.predictedProb() - actual;
            brierSum += err * err;

            // Log-loss: -ln(probability assigned to what actually happened).
            double pTruth = p.outcome() ? p.predictedProb() : 1.0 - p.predictedProb();
            pTruth = clamp(pTruth);
            logLossSum += -Math.log(pTruth);

            // Market comparison (only where we have a market number).
            if (p.hasMarket()) {
                double mErr = p.marketImpliedProb() - actual;
                marketBrierSum += mErr * mErr;
                marketCount++;
            }
        }

        int n = predictions.size();
        double brier = brierSum / n;
        double logLoss = logLossSum / n;
        double marketBrier = marketCount > 0 ? marketBrierSum / marketCount : Double.NaN;

        return new CalibrationReport(n, brier, logLoss, marketBrier);
    }

    private static double clamp(double p) {
        if (p < EPS) return EPS;
        if (p > 1.0 - EPS) return 1.0 - EPS;
        return p;
    }
}
