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
