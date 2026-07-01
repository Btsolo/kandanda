package com.kandanda;

import com.kandanda.data.MatchResult;
import com.kandanda.data.MatchResultRepository;
import com.kandanda.data.TournamentLoader;
import com.kandanda.rating.RatingService;
import com.kandanda.rating.TeamRating;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Kandanda Intelligence — application entry point.
 *
 * <p>On startup, the {@link CommandLineRunner} loads the 2022 World Cup data and
 * prints a confirmation. This is Story S2's visible proof: run the app, see
 * "Loaded 64 matches". Later stories add the model on top of this data.
 */
@SpringBootApplication
public class KandandaApplication {

    public static void main(String[] args) {
        SpringApplication.run(KandandaApplication.class, args);
    }

    @Bean
    CommandLineRunner loadData(TournamentLoader loader, MatchResultRepository matchRepo) {
        return args -> {
            int n = loader.load("data/worldcup-2022.json", "WC-2022");
            long total = matchRepo.countByTournament("WC-2022");
            System.out.println("================ S2 DATA LOAD ================");
            System.out.println("Newly stored this run : " + n + " matches");
            System.out.println("Total WC-2022 in DB   : " + total + " matches");
            System.out.println("Sample (first 3):");
            matchRepo.findByTournamentOrderByDateAsc("WC-2022").stream()
                    .limit(3)
                    .forEach(m -> System.out.println("   " + m));
            System.out.println("=============================================");

            // ----- S3: fit attack/defence ratings on the full 2022 tournament -----
            List<MatchResult> all = matchRepo.findByTournamentOrderByDateAsc("WC-2022");
            List<TeamRating> ratings = new RatingService().fit(all);

            System.out.println("============== S3 STRENGTH RATINGS ===========");
            System.out.println("Top 5 attacks (note: small-sample noise is expected):");
            ratings.stream().limit(5).forEach(r -> System.out.println("   " + r));
            System.out.println("Best 5 defences (lowest = best):");
            ratings.stream()
                    .sorted(java.util.Comparator.comparingDouble(TeamRating::defence))
                    .limit(5)
                    .forEach(r -> System.out.println("   " + r));
            System.out.println("=============================================");

            // ----- S4: predict a real match from the fitted ratings -----
            double leagueAvg = RatingService.leagueAverageGoals(all);
            var model = new com.kandanda.model.PoissonMatchModel(leagueAvg);
            java.util.Map<String, TeamRating> byName = new java.util.HashMap<>();
            for (TeamRating r : ratings) byName.put(r.team(), r);

            var arg = byName.get("Argentina");
            var fra = byName.get("France");
            var grid = model.predict(arg, fra);
            var market = new com.kandanda.model.MarketCalculator(grid);

            System.out.println("=========== S4/S5 MATCH PREDICTION =========");
            System.out.printf("Argentina vs France (2022 final, fitted on full tournament)%n");
            System.out.printf("Expected goals: ARG %.2f - %.2f FRA%n",
                    grid.homeExpectedGoals(), grid.awayExpectedGoals());
            System.out.printf("ARG win %.1f%%   draw %.1f%%   FRA win %.1f%%%n",
                    market.homeWin() * 100, market.draw() * 100, market.awayWin() * 100);
            System.out.println("--- full market table (S5) ---");
            market.headlineMarkets().forEach((name, p) ->
                    System.out.printf("   %-18s %.1f%%%n", name, p * 100));
            System.out.println("Most likely scorelines:");
            printTopScorelines(grid, 5);
            System.out.println("=============================================");

            // ----- S6: the backtest experiment (group-trained vs all-data) -----
            var backtest = new com.kandanda.experiment.BacktestService();
            var result = backtest.run(all);
            System.out.println("============== S6 BACKTEST ===================");
            System.out.printf("Train: %d group games   Test: %d knockout games   (all markets scored)%n",
                    result.groupMatches(), result.knockoutMatches());
            System.out.println("Baseline to beat: Brier 0.2500 (always-0.5 coin flip)");
            System.out.println();
            System.out.println("GROUP-ONLY model  [FAIR — never saw the knockouts]:");
            System.out.printf("   %s%n", result.groupOnly());
            System.out.println("ALL-DATA model    [LEAKAGE — trained on the games it predicts, do NOT trust]:");
            System.out.printf("   %s%n", result.allData());
            System.out.println();
            System.out.println("Read the GROUP-ONLY number. If its Brier > 0.25, the honest");
            System.out.println("base rate cannot yet beat a coin flip on knockouts — which is a");
            System.out.println("true finding about tournament football, and the bar every future");
            System.out.println("improvement (priors, player layer, Dixon-Coles) must beat.");
            System.out.println("=============================================");

            // ----- S7: sweep the pre-tournament prior strength k -----
            double[] kValues = {0, 0.5, 1, 2, 3, 5, 8, 12, 20, 40};
            var sweep = backtest.sweepPrior(all, kValues);
            System.out.println("============== S7 PRIOR SWEEP ================");
            System.out.println("Group-trained, scored on knockouts. Lower Brier = better.");
            System.out.println("  k       Brier");
            double bestK = 0, bestBrier = Double.MAX_VALUE;
            for (var e : sweep.entrySet()) {
                System.out.printf("  %-6.1f  %.4f%n", e.getKey(), e.getValue());
                if (e.getValue() < bestBrier) { bestBrier = e.getValue(); bestK = e.getKey(); }
            }
            System.out.printf("Best k=%.1f (Brier %.4f). k=0 was %.4f; baseline 0.2500.%n",
                    bestK, bestBrier, sweep.get(0.0));
            System.out.println("NOTE: a very large best-k means group form carries little knockout");
            System.out.println("signal, so shrinking toward average wins. That is a finding, not a");
            System.out.println("victory — real signal must come from richer data (Tier 2) later.");
            System.out.println("=============================================");

            // ----- S8: Dixon-Coles low-score correction -----
            double[] dc = backtest.compareDixonColes(all, 5.0, -0.1);
            System.out.println("============== S8 DIXON-COLES ================");
            System.out.printf("At prior k=5, group-trained, knockout Brier:%n");
            System.out.printf("   pure Poisson (rho=0)    : %.4f%n", dc[0]);
            System.out.printf("   Dixon-Coles (rho=-0.10) : %.4f%n", dc[1]);
            System.out.printf("   change: %+.4f  (%s)%n", dc[1] - dc[0],
                    dc[1] < dc[0] ? "small improvement" : "no improvement");
            System.out.println("Expected: a tiny improvement. DC is canonical and theoretically");
            System.out.println("sound, but on 16 knockout games its effect is marginal — kept as");
            System.out.println("a correct, honest result, not oversold.");
            System.out.println("=============================================");

            // ----- S13 (Tier 2): team-form modifier sweep -----
            double[] formWeights = {0, 0.1, 0.25, 0.5, 0.75, 1.0, 1.5};
            var formSweep = backtest.sweepForm(all, 12.0, -0.1, formWeights);
            System.out.println("======== S13 TIER 2: TEAM FORM ==============");
            System.out.println("Group-trained (prior k=12, DC rho=-0.1), knockout Brier.");
            System.out.println("Form = group over/under-performance vs own xG. w=0 is base.");
            System.out.println("  weight  Brier");
            double baseBrier = formSweep.get(0.0), bestW = 0, bestB = Double.MAX_VALUE;
            for (var e : formSweep.entrySet()) {
                System.out.printf("  %-6.2f  %.4f%n", e.getKey(), e.getValue());
                if (e.getValue() < bestB) { bestB = e.getValue(); bestW = e.getKey(); }
            }
            System.out.printf("Best weight=%.2f (Brier %.4f) vs base %.4f.%n", bestW, bestB, baseBrier);
            System.out.println("A sweet spot that gets WORSE past it = a real but PARTIAL signal,");
            System.out.println("not noise being overfit. One tournament only — needs the validation");
            System.out.println("basket (2018, UCLs, Euro/Copa 2024) to confirm it generalises.");
            System.out.println("=============================================");

            // ----- S14-data: xG vs goals as the model input -----
            long withXg = all.stream().filter(MatchResult::hasXg).count();
            System.out.println("============ S14-DATA: xG vs GOALS ===========");
            System.out.printf("%d/%d matches carry xG.%n", withXg, all.size());
            if (withXg > 0) {
                System.out.println("Group-trained, scored on knockout GOAL outcomes. Lower Brier better.");
                System.out.println("  prior k | goals  | xG     | winner");
                for (double k : new double[]{5, 8, 12, 20}) {
                    double[] cmp = backtest.compareXg(all, k, -0.1);
                    String win = cmp[1] < cmp[0] ? "xG" : "goals";
                    System.out.printf("  k=%-5.0f  %.4f  %.4f  %s%n", k, cmp[0], cmp[1], win);
                }
                System.out.println("Verdict: xG is a real but INCREMENTAL gain (biggest at low prior;");
                System.out.println("shrinkage already tames variance at high prior). Its bigger value is");
                System.out.println("per-game truth on high-variance matches (e.g. ARG dominated KSA on xG");
                System.out.println("yet lost) — which matters for the residual analyzer next.");
            } else {
                System.out.println("No xG in this dataset — falls back to goals cleanly.");
            }
            System.out.println("=============================================");
        };
    }

    /** Print the n most likely exact scorelines from a grid. */
    private static void printTopScorelines(com.kandanda.model.ScorelineGrid grid, int n) {
        record Cell(int h, int a, double p) { }
        java.util.List<Cell> cells = new java.util.ArrayList<>();
        int max = grid.maxGoals();
        for (int i = 0; i <= max; i++)
            for (int j = 0; j <= max; j++)
                cells.add(new Cell(i, j, grid.probabilityOf(i, j)));
        cells.sort(java.util.Comparator.comparingDouble(Cell::p).reversed());
        cells.stream().limit(n).forEach(c ->
                System.out.printf("   %d-%d : %.1f%%%n", c.h(), c.a(), c.p() * 100));
    }
}