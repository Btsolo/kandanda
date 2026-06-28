package com.kandanda.experiment;

import com.kandanda.data.MatchResult;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a played match's 90-minute score into the actual true/false outcome of each
 * market — the ground truth the Scoreboard grades predictions against.
 *
 * <p>Scoring rule (load-bearing): we grade against the <b>90-minute</b> result, the
 * same number the model predicts. A knockout that was level after 90 minutes counts as
 * a DRAW here even though one side advanced on penalties — because the model never
 * tried to predict the shootout (penalties were excluded from the goal data on purpose).
 * Grading the model on what it actually attempts keeps the comparison fair.
 *
 * <p>The keys here MUST match the keys produced by the prediction side so the Scoreboard
 * lines up prediction with outcome market-by-market.
 */
public final class MatchOutcomes {

    private MatchOutcomes() { }

    public static Map<String, Boolean> of(MatchResult m) {
        int h = m.getHomeGoals();
        int a = m.getAwayGoals();
        int t = h + a;

        Map<String, Boolean> o = new LinkedHashMap<>();
        o.put("Home win", h > a);
        o.put("Draw", h == a);
        o.put("Away win", h < a);
        o.put("Double chance 1X", h >= a);
        o.put("Double chance X2", h <= a);
        o.put("Over 2.5", t > 2.5);
        o.put("Under 2.5", t < 2.5);
        o.put("BTTS yes", h > 0 && a > 0);
        o.put("BTTS no", h == 0 || a == 0);
        o.put("Odd goals", t % 2 == 1);
        o.put("Even goals", t % 2 == 0);
        return o;
    }
}
