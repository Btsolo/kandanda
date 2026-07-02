package com.kandanda.profile;

/**
 * A manager profile — minimal first slice: just the risk axis (doc 03 §3).
 *
 * <p>Judgment-derived. Tactical identity (low block, pressing, set-piece dependence)
 * is deliberately deferred until a modifier actually needs it — design deep, implement
 * minimal.
 *
 * @param name     manager name
 * @param team     team managed
 * @param riskAxis -1..+1: −1 = deeply cautious (protects a draw, late defensive subs),
 *                 +1 = gambler (chases games, early attacking subs). Affects not just
 *                 the rating split but the VARIANCE of outcomes — gamblers widen the
 *                 scoreline distribution. 0 = neutral/unknown.
 */
public record ManagerProfile(
        String name,
        String team,
        double riskAxis
) {
    public ManagerProfile {
        if (riskAxis < -1 || riskAxis > 1) {
            throw new IllegalArgumentException("riskAxis must be in [-1,1]");
        }
    }

    public static ManagerProfile neutral(String name, String team) {
        return new ManagerProfile(name, team, 0.0);
    }
}
