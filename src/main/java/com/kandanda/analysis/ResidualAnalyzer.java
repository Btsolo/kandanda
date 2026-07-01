package com.kandanda.analysis;

import com.kandanda.data.MatchResult;
import com.kandanda.rating.RatingService;
import com.kandanda.rating.TeamRating;

import java.util.*;

/**
 * The residual analyzer — the "judge" of the intelligence layer.
 *
 * <p>Its job: for each team, measure how their actual output deviated from what the
 * base-rate model expected, and decompose that deviation into a SKILL component
 * (creation: xG vs expected) and a LUCK component (finishing: goals vs xG). This is the
 * mathematical substitute for "luck" — it lets us ask, per team, whether over/under-
 * performance was a persistent signal or scattered variance.
 *
 * <p>Why this is built as a judge: every Tier 2 hypothesis (talisman, chemistry, etc.)
 * ultimately claims "team X is better/worse than its base rate for reason Y". The residual
 * analyzer is how we check whether such a claim corresponds to a REAL, consistent
 * deviation, rather than a story told after a lucky game.
 *
 * <h2>Method</h2>
 * <ol>
 *   <li>Fit base ratings on the given matches (xG-based, with a prior), giving each team
 *       an expected xG per fixture.</li>
 *   <li>For each match, record per team: creation residual (actual xG − expected xG) and
 *       finishing residual (actual goals − actual xG).</li>
 *   <li>Summarise each team's residuals: mean and a consistency measure
 *       (mean ÷ floored-sd).</li>
 * </ol>
 *
 * <p>Requires xG in the data. Teams whose matches lack xG are skipped (finishing/creation
 * are undefined without it).
 */
public class ResidualAnalyzer {

    /**
     * Floor on the standard deviation used in the consistency denominator. Goals/xG per
     * game realistically vary by at least this much; flooring prevents a 2–3 game run with
     * a tiny spread from producing an absurdly large "consistency". A domain-sensible guard.
     */
    private static final double MIN_SD = 0.4;

    private final double priorStrength;

    public ResidualAnalyzer() {
        this(12.0); // a solid default prior, near the calibration sweet spot
    }

    public ResidualAnalyzer(double priorStrength) {
        this.priorStrength = priorStrength;
    }

    /**
     * Analyse residuals for all teams appearing in the given matches.
     *
     * @param matches matches to analyse (e.g. the group stage). Must carry xG.
     * @return one {@link TeamResidual} per team, sorted by creation mean (best first)
     */
    public List<TeamResidual> analyse(List<MatchResult> matches) {
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("No matches to analyse");
        }

        // Fit xG-based ratings to get each team's expected xG per fixture.
        RatingService ratingService = new RatingService(priorStrength, true);
        List<TeamRating> ratings = ratingService.fit(matches);
        double avg = ratingService.leagueAverage(matches);
        Map<String, TeamRating> byName = new HashMap<>();
        for (TeamRating r : ratings) byName.put(r.team(), r);

        // Accumulate per-team residual lists.
        Map<String, List<Double>> creation = new HashMap<>();
        Map<String, List<Double>> finishing = new HashMap<>();

        for (MatchResult m : matches) {
            if (!m.hasXg()) continue;
            String h = m.getHomeTeam().getName();
            String a = m.getAwayTeam().getName();
            TeamRating rh = byName.get(h), ra = byName.get(a);
            if (rh == null || ra == null) continue;

            double expH = rh.attack() * ra.defence() * avg;
            double expA = ra.attack() * rh.defence() * avg;

            // Creation: how much xG created vs expected.
            creation.computeIfAbsent(h, k -> new ArrayList<>()).add(m.getHomeXg() - expH);
            creation.computeIfAbsent(a, k -> new ArrayList<>()).add(m.getAwayXg() - expA);
            // Finishing: goals scored vs xG.
            finishing.computeIfAbsent(h, k -> new ArrayList<>()).add(m.getHomeGoals() - m.getHomeXg());
            finishing.computeIfAbsent(a, k -> new ArrayList<>()).add(m.getAwayGoals() - m.getAwayXg());
        }

        List<TeamResidual> out = new ArrayList<>();
        for (String team : creation.keySet()) {
            List<Double> cr = creation.get(team);
            List<Double> fn = finishing.get(team);
            double[] cSummary = summarise(cr);
            double[] fSummary = summarise(fn);
            out.add(new TeamResidual(team, cr.size(),
                    cSummary[0], cSummary[1], fSummary[0], fSummary[1]));
        }
        out.sort(Comparator.comparingDouble(TeamResidual::creationMean).reversed());
        return out;
    }

    /** @return [mean, consistency] where consistency = mean / max(sd, MIN_SD). */
    private double[] summarise(List<Double> values) {
        int n = values.size();
        double mean = 0;
        for (double v : values) mean += v;
        mean /= n;
        double var = 0;
        for (double v : values) var += (v - mean) * (v - mean);
        var /= n;
        double sd = Math.sqrt(var);
        double consistency = mean / Math.max(sd, MIN_SD);
        return new double[]{mean, consistency};
    }
}
