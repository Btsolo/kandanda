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

    private final RatingService ratingService = new RatingService();
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

        CalibrationReport groupOnly = fitAndScore(group, knockout);
        CalibrationReport allData = fitAndScore(tournament, knockout);

        return new BacktestResult(group.size(), knockout.size(), groupOnly, allData);
    }

    /**
     * Fit ratings on {@code trainSet}, predict every market for each match in
     * {@code testSet}, and score those predictions against the actual 90-minute outcomes.
     */
    private CalibrationReport fitAndScore(List<MatchResult> trainSet, List<MatchResult> testSet) {
        List<TeamRating> ratings = ratingService.fit(trainSet);
        double leagueAvg = RatingService.leagueAverageGoals(trainSet);
        PoissonMatchModel model = new PoissonMatchModel(leagueAvg);

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
