package com.kandanda.experiment;

import com.kandanda.data.MatchResult;
import com.kandanda.model.MarketCalculator;
import com.kandanda.model.PoissonMatchModel;
import com.kandanda.rating.RatingService;
import com.kandanda.rating.TeamRating;
import com.kandanda.scoreboard.CalibrationReport;
import com.kandanda.scoreboard.CalibrationService;
import com.kandanda.scoreboard.Prediction;

import java.util.*;

/**
 * Story S6 — the backtest experiment.
 *
 * <p>Splits a tournament into a training set (group stage) and a test set (knockouts),
 * fits two models, and scores both on the knockout test set across every market:
 * <ul>
 *   <li><b>Group-only model</b> — fit on group games only. It has NEVER seen the
 *       knockout games it predicts. This is the fair, leakage-free number, and the one
 *       that embodies the "tournament-only, no prior, group teaches / knockouts test"
 *       design. This is the number we actually trust.</li>
 *   <li><b>All-data model</b> — fit on all matches, including the knockouts it then
 *       "predicts". This is DATA LEAKAGE: it has already seen the answers, so its score
 *       is optimistically biased and must NOT be read as a fair result. We compute it
 *       only to make the size of the leakage effect visible.</li>
 * </ul>
 *
 * <p>The honest takeaway is always the group-only number, compared against the 0.25
 * Brier coin-flip baseline.
 */
public class BacktestService {

    private static final Set<String> GROUP_ROUNDS =
            Set.of("Matchday 1", "Matchday 2", "Matchday 3");

    private final CalibrationService scoreboard = new CalibrationService();

    public record BacktestResult(
            int groupMatches,
            int knockoutMatches,
            CalibrationReport groupOnly,   // fair (no leakage)
            CalibrationReport allData      // leakage — optimistic, do not trust
    ) { }

    public BacktestResult run(List<MatchResult> tournament) {
        List<MatchResult> group = new ArrayList<>();
        List<MatchResult> knockout = new ArrayList<>();
        for (MatchResult m : tournament) {
            if (GROUP_ROUNDS.contains(m.getRound())) group.add(m);
            else knockout.add(m);
        }
        if (group.isEmpty() || knockout.isEmpty()) {
            throw new IllegalStateException(
                    "Need both group and knockout matches; got "
                            + group.size() + " group, " + knockout.size() + " knockout");
        }

        CalibrationReport groupOnly = fitAndScore(group, knockout, 0.0);
        CalibrationReport allData = fitAndScore(tournament, knockout, 0.0);

        return new BacktestResult(group.size(), knockout.size(), groupOnly, allData);
    }

    /**
     * Sweep the prior strength k over the given values, fitting group-only each time and
     * scoring on the knockouts. Returns k -> Brier, so the caller can see where the
     * honest model's calibration bottoms out. This is the S7 experiment.
     */
    public java.util.LinkedHashMap<Double, Double> sweepPrior(
            List<MatchResult> tournament, double[] kValues) {
        List<MatchResult> group = new ArrayList<>();
        List<MatchResult> knockout = new ArrayList<>();
        for (MatchResult m : tournament) {
            if (GROUP_ROUNDS.contains(m.getRound())) group.add(m);
            else knockout.add(m);
        }
        var out = new java.util.LinkedHashMap<Double, Double>();
        for (double k : kValues) {
            out.put(k, fitAndScore(group, knockout, k).brier());
        }
        return out;
    }

    /**
     * Compare pure Poisson vs Dixon-Coles at a fixed prior, scored on knockouts.
     * Returns [brierNoDC, brierWithDC] so the caller can see if the correction helps here.
     */
    public double[] compareDixonColes(List<MatchResult> tournament, double k, double rho) {
        List<MatchResult> group = new ArrayList<>();
        List<MatchResult> knockout = new ArrayList<>();
        for (MatchResult m : tournament) {
            if (GROUP_ROUNDS.contains(m.getRound())) group.add(m);
            else knockout.add(m);
        }
        double noDC = fitAndScore(group, knockout, k, 0.0).brier();
        double withDC = fitAndScore(group, knockout, k, rho).brier();
        return new double[]{noDC, withDC};
    }

    /**
     * Fit ratings on {@code trainSet} (with prior strength k), predict every market for
     * each match in {@code testSet}, and score those against the actual 90-minute outcomes.
     */
    /**
     * Sweep the Tier 2 form-modifier weight at fixed prior k and rho, scored on knockouts.
     * Returns weight -> Brier. weight=0 is the base rate (no form); the sweep shows whether
     * group-stage form adds real signal and where its calibration sweet spot is.
     */
    public java.util.LinkedHashMap<Double, Double> sweepForm(
            List<MatchResult> tournament, double k, double rho, double[] weights) {
        List<MatchResult> group = new ArrayList<>();
        List<MatchResult> knockout = new ArrayList<>();
        for (MatchResult m : tournament) {
            if (GROUP_ROUNDS.contains(m.getRound())) group.add(m);
            else knockout.add(m);
        }
        var out = new java.util.LinkedHashMap<Double, Double>();
        for (double w : weights) {
            var pipeline = new com.kandanda.tier2.ModifierPipeline(
                    java.util.List.of(new com.kandanda.tier2.FormModifier(w)));
            out.put(w, fitAndScore(group, knockout, k, rho, pipeline).brier());
        }
        return out;
    }

    private CalibrationReport fitAndScore(List<MatchResult> trainSet, List<MatchResult> testSet, double k) {
        return fitAndScore(trainSet, testSet, k, 0.0);
    }

    private CalibrationReport fitAndScore(List<MatchResult> trainSet, List<MatchResult> testSet,
                                          double k, double rho) {
        return fitAndScore(trainSet, testSet, k, rho, com.kandanda.tier2.ModifierPipeline.empty());
    }

    private CalibrationReport fitAndScore(List<MatchResult> trainSet, List<MatchResult> testSet,
                                          double k, double rho,
                                          com.kandanda.tier2.ModifierPipeline pipeline) {
        List<TeamRating> ratings = new RatingService(k).fit(trainSet);
        // Tier 2: apply rating modifiers (form, etc.) using ONLY the training (group) data.
        ratings = pipeline.apply(ratings, trainSet);
        double leagueAvg = RatingService.leagueAverageGoals(trainSet);
        PoissonMatchModel model = new PoissonMatchModel(leagueAvg, rho);

        Map<String, TeamRating> byName = new HashMap<>();
        for (TeamRating r : ratings) byName.put(r.team(), r);

        List<Prediction> predictions = new ArrayList<>();
        for (MatchResult m : testSet) {
            TeamRating home = byName.get(m.getHomeTeam().getName());
            TeamRating away = byName.get(m.getAwayTeam().getName());
            // A team in the test set might not appear in the train set (can't rate it).
            if (home == null || away == null) continue;

            MarketCalculator calc = new MarketCalculator(model.predict(home, away));
            Map<String, Double> predicted = calc.headlineMarkets();
            Map<String, Boolean> actual = MatchOutcomes.of(m);

            for (Map.Entry<String, Double> e : predicted.entrySet()) {
                String marketName = e.getKey();
                double p = e.getValue();
                boolean happened = actual.get(marketName);
                predictions.add(Prediction.withoutMarket(
                        m.getHomeTeam().getName() + " v " + m.getAwayTeam().getName()
                                + " : " + marketName,
                        p, happened));
            }
        }
        return scoreboard.score(predictions);
    }
}