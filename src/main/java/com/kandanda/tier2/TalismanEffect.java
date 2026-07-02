package com.kandanda.tier2;

/**
 * The talisman-absence effect (S16) — the first judgment-derived profile attribute to
 * face the judge, and the first SIGNED intelligence effect.
 *
 * <h2>The hypothesis (H3, refined by the judge)</h2>
 * The naive claim "talisman absent → team weaker" was REJECTED by the 2022 backtest:
 * penalising Portugal for Ronaldo's absence made calibration monotonically worse
 * (0.2442 → 0.2459), because Portugal won 6-1 without him. The effect must be signed by
 * ROLE FIT: a well-fitted star's absence hurts (roleFit &gt; 0); a misfitted star's absence
 * can help (roleFit &lt; 0 — the Ronaldo-in-Martinez-system case). The signed version was
 * neutral-to-marginally-better on 2022 (0.2440 vs 0.2442) — consistent with H3, though
 * only two affected matches, so direction matters more than magnitude until more data.
 */
public final class TalismanEffect {

    private TalismanEffect() { }

    /** Clamp bounds: intelligence NUDGES the math, it never seizes it (yin-yang rule). */
    static final double MIN_MULT = 0.5;
    static final double MAX_MULT = 1.5;

    /**
     * Attack-lambda multiplier applied when the talisman is ABSENT.
     *
     * <pre>multiplier = 1 − weight × starDependence × roleFit</pre>
     *
     * roleFit &gt; 0 (star fits the system): absence reduces attack.
     * roleFit &lt; 0 (star misfits): absence INCREASES attack (H3).
     * weight = 0, dependence = 0, or roleFit = 0 → exactly 1 (identity — no claim, no effect).
     * Result clamped to [{@value MIN_MULT}, {@value MAX_MULT}]: bounded adjustment, never
     * a replacement of the base rate.
     */
    public static double absenceMultiplier(double starDependence, double roleFit, double weight) {
        double m = 1.0 - weight * starDependence * roleFit;
        return Math.max(MIN_MULT, Math.min(MAX_MULT, m));
    }
}