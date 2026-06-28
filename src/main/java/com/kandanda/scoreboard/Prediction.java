package com.kandanda.scoreboard;

/**
 * A single recorded prediction about a binary event (e.g. "Argentina beats Saudi Arabia",
 * "over 2.5 goals", "both teams score").
 *
 * <p>This is the atomic unit the whole project is judged on. Every market we predict
 * decomposes into binary events like this, so one calibration engine scores everything.
 *
 * <p>Design notes (senior trail):
 * <ul>
 *   <li>It is an immutable {@code record} with no framework annotations. The domain core
 *       must not depend on Spring/JPA — persistence is added as an outer layer later
 *       (ports-and-adapters). This keeps the maths testable in isolation.</li>
 *   <li>{@code marketImpliedProbability} is stored alongside our own number so the
 *       scoreboard can always answer the real question: did we beat the market? (NFR4)</li>
 *   <li>{@code outcome} is the ground truth: {@code true} if the event happened. We only
 *       ever set this <em>after</em> the event — enforcing the point-in-time / no-leakage
 *       rule the developer flagged (hindsight bias guard).</li>
 * </ul>
 *
 * @param eventLabel        human-readable description, e.g. "ARG win vs KSA"
 * @param predictedProb     our model's probability in [0,1] that the event happens
 * @param marketImpliedProb the bookmaker-implied probability in [0,1], or NaN if unknown
 * @param outcome           ground truth: true if the event actually happened
 */
public record Prediction(
        String eventLabel,
        double predictedProb,
        double marketImpliedProb,
        boolean outcome
) {
    public Prediction {
        if (predictedProb < 0.0 || predictedProb > 1.0) {
            throw new IllegalArgumentException(
                    "predictedProb must be in [0,1], got " + predictedProb);
        }
        // marketImpliedProb may be NaN (unknown) but if present must be a valid probability
        if (!Double.isNaN(marketImpliedProb)
                && (marketImpliedProb < 0.0 || marketImpliedProb > 1.0)) {
            throw new IllegalArgumentException(
                    "marketImpliedProb must be in [0,1] or NaN, got " + marketImpliedProb);
        }
    }

    /** Convenience factory for when we have no market comparison (market prob unknown). */
    public static Prediction withoutMarket(String eventLabel, double predictedProb, boolean outcome) {
        return new Prediction(eventLabel, predictedProb, Double.NaN, outcome);
    }

    /** True if a market-implied probability is available for comparison. */
    public boolean hasMarket() {
        return !Double.isNaN(marketImpliedProb);
    }
}
