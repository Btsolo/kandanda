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
            int n18 = loader.load("data/worldcup-2018.json", "WC-2018");
            int n26 = loader.load("data/worldcup-2026.json", "WC-2026");
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
                System.out.println("Verdict (honest, correct 48/16 split): as a raw team-rating input");
                System.out.println("on one tournament, xG is roughly a WASH with goals — marginally");
                System.out.println("better only at low prior (k=5), marginally worse at the priors we");
                System.out.println("actually use (k=8-12). xG's real value is NOT here in the aggregate");
                System.out.println("Brier, but in the per-game creation-vs-finishing split the residual");
                System.out.println("analyzer (S14) needs — e.g. ARG dominated KSA on xG yet lost. So xG");
                System.out.println("is kept as INFRASTRUCTURE for S14, not as a standalone rating win.");
            } else {
                System.out.println("No xG in this dataset — falls back to goals cleanly.");
            }
            System.out.println("=============================================");

            // ----- S14: residual analyzer (the judge) on the group stage -----
            List<MatchResult> groupMatches = all.stream()
                    .filter(mm -> mm.getRound() != null && mm.getRound().startsWith("Matchday"))
                    .toList();
            if (!groupMatches.isEmpty() && groupMatches.stream().anyMatch(MatchResult::hasXg)) {
                var analyzer = new com.kandanda.analysis.ResidualAnalyzer(12.0);
                var residuals = analyzer.analyse(groupMatches);
                System.out.println("======== S14 RESIDUAL ANALYZER (JUDGE) ======");
                System.out.printf("Group stage (%d matches). Creation = xG vs expected (SKILL).%n",
                        groupMatches.size());
                System.out.println("Finishing = goals vs xG (LUCK, regresses). Read separates them.");
                System.out.println("--- top 5 creators (genuinely outplaying expectation) ---");
                residuals.stream().limit(5).forEach(r -> System.out.println("   " + r));
                System.out.println("--- biggest over-finishers (riding luck, due to regress) ---");
                residuals.stream()
                        .sorted(java.util.Comparator.comparingDouble(
                                com.kandanda.analysis.TeamResidual::finishingMean).reversed())
                        .limit(3)
                        .forEach(r -> System.out.println("   " + r));
                System.out.println("This judge is what every Tier 2 hypothesis must answer to:");
                System.out.println("a claim 'team X is underrated' must show a real, consistent");
                System.out.println("positive creation residual — not just one lucky result.");
                System.out.println("=============================================");

                // ----- S16: talisman-absence effect (first judged profile attribute) -----
                var teams16 = com.kandanda.profile.Profiles2022.teams();
                var players16 = com.kandanda.profile.Profiles2022.players();
                var absences16 = com.kandanda.profile.Profiles2022.knockoutAbsences();
                double[] tw = {0, 0.25, 0.5, 0.75, 1.0};
                var signed = backtest.sweepTalisman(all, 8.0, -0.1,
                        teams16, players16, absences16, tw);
                // Naive version: force roleFit=+1 (absence always a penalty) for contrast.
                var naivePlayers = players16.stream().map(p -> new com.kandanda.profile.PlayerProfile(
                                p.name(), p.team(), p.position(), p.data(),
                                new com.kandanda.profile.PlayerProfile.JudgmentDerived(
                                        p.judgment().talismanResponsibility(), p.judgment().bigMatchTemperament(), 1.0)))
                        .toList();
                var naive = backtest.sweepTalisman(all, 8.0, -0.1,
                        teams16, naivePlayers, absences16, tw);
                System.out.println("======== S16 TALISMAN EFFECT (SIGNED, H3) ====");
                System.out.println("2022 case: Ronaldo benched R16 (6-1 W) + QF (0-1 L).");
                System.out.println("  weight | naive(absence=penalty) | signed(by roleFit)");
                for (double w : tw) {
                    System.out.printf("  %-6.2f  %.4f                 %.4f%n", w, naive.get(w), signed.get(w));
                }
                System.out.println("Judge's verdict: naive gets WORSE with weight (rejected) — Portugal");
                System.out.println("won without Ronaldo. Signed (misfit -> absence helps) stays level or");
                System.out.println("marginally better: consistent with H3. Only 2 affected matches, so");
                System.out.println("direction over magnitude; 2026 lineups give the real test.");
                System.out.println("=============================================");

                // ----- S17: validation basket — does form REPLICATE on 2018? -----
                List<MatchResult> wc18 = matchRepo.findByTournamentOrderByDateAsc("WC-2018");
                if (!wc18.isEmpty()) {
                    double[] fw17 = {0, 0.1, 0.25, 0.5, 0.75, 1.0, 1.5};
                    var f22 = backtest.sweepForm(all, 12.0, -0.1, fw17);
                    var f18 = backtest.sweepForm(wc18, 12.0, -0.1, fw17);
                    System.out.println("===== S17 VALIDATION BASKET: FORM REPLICATION =====");
                    System.out.println("Same method, two tournaments, independently (no cross-leakage).");
                    System.out.println("  weight | WC-2022 | WC-2018");
                    for (double w : fw17) {
                        System.out.printf("  %-6.2f  %.4f   %.4f%n", w, f22.get(w), f18.get(w));
                    }
                    System.out.println("VERDICT: form FAILED replication. 2022 improved to a sweet spot");
                    System.out.println("(~w=0.75); 2018 gets monotonically WORSE with any form weight.");
                    System.out.println("The 2022 'sweet-spot fingerprint' was one tournament fooling us.");
                    System.out.println("Group form -> knockout carryover is NOT a stable signal across");
                    System.out.println("World Cups. FormModifier stays in the codebase as infrastructure,");
                    System.out.println("but is NOT part of the trusted model. This is the instrument");
                    System.out.println("working: a promising signal died honestly before shipping.");
                    System.out.println("=============================================");
                }

                // ----- S18: 2026 LIVE FORWARD TEST — locked pre-match predictions -----
                List<MatchResult> wc26 = matchRepo.findByTournamentOrderByDateAsc("WC-2026");
                if (!wc26.isEmpty()) {
                    List<MatchResult> group26 = wc26.stream()
                            .filter(mm -> mm.getRound() != null && mm.getRound().startsWith("Matchday"))
                            .toList();
                    var rs26 = new com.kandanda.rating.RatingService(8.0, false); // TRUSTED model only
                    var ratings26 = rs26.fit(group26);
                    double avg26 = rs26.leagueAverage(group26);
                    var model26 = new com.kandanda.model.PoissonMatchModel(avg26, -0.1);
                    java.util.Map<String, com.kandanda.rating.TeamRating> by26 = new java.util.HashMap<>();
                    for (var r : ratings26) by26.put(r.team(), r);
                    // Fixtures known and UNPLAYED at lock time (2026-07-03). The git commit
                    // of this output is the tamper-proof timestamp. NO SportRadar peeking.
                    String[][] fixtures = {
                            {"Argentina", "Cape Verde"}, {"Colombia", "Ghana"}, {"Australia", "Egypt"},
                            {"Canada", "Morocco"}, {"Paraguay", "France"}, {"Brazil", "Norway"},
                            {"Mexico", "England"}, {"Spain", "Portugal"}, {"Belgium", "USA"},
                            {"Switzerland", "Colombia"}, {"Argentina", "Egypt"},   // locked 07-04
                            {"France", "Morocco"}, {"Norway", "England"}};         // QFs locked 07-06
                    System.out.println("==== S18 LIVE 2026: LOCKED PREDICTIONS ======");
                    System.out.println("Trusted model ONLY (goals, prior k=8, DC rho=-0.1) fitted on the");
                    System.out.println("72 group games. Locked pre-kickoff; scored as results arrive.");
                    System.out.println("(Switzerland-Algeria already played -> excluded; SUI's R16 locks");
                    System.out.println("once its opponent is known.)");
                    // Lineup intelligence (EXPERIMENTAL, not the scored baseline): as
                    // lineups drop, add e.g. new TalismanAbsence("Portugal","Round of 16")
                    // below and re-run for the adjusted second set.
                    java.util.Set<com.kandanda.tier2.TalismanAbsence> lineup26 = new java.util.HashSet<>();
                    java.util.Map<String, Double> dep26 = new java.util.HashMap<>();
                    java.util.Map<String, Double> fit26 = new java.util.HashMap<>();
                    for (var tp : com.kandanda.profile.Profiles2026.teams()) dep26.put(tp.team(), tp.starDependence());
                    for (var pp : com.kandanda.profile.Profiles2026.players()) fit26.put(pp.team(), pp.judgment().roleFit());
                    for (String[] f : fixtures) {
                        var th = by26.get(f[0]);
                        var ta = by26.get(f[1]);
                        if (th == null || ta == null) continue;
                        double lh = th.attack() * ta.defence() * avg26;
                        double la = ta.attack() * th.defence() * avg26;
                        var mk = new com.kandanda.model.MarketCalculator(model26.buildGrid(lh, la)).headlineMarkets();
                        System.out.printf("%n--- %s v %s  (xG %.2f - %.2f) ---%n", f[0], f[1], lh, la);
                        for (var e : mk.entrySet()) {
                            System.out.printf("   %-18s %5.1f%%%n", e.getKey(), 100 * e.getValue());
                        }
                        // Experimental lineup-adjusted view (only if absences entered):
                        String rd = fixtureRound(f[0], f[1]);
                        boolean habs = lineup26.contains(new com.kandanda.tier2.TalismanAbsence(f[0], rd));
                        boolean aabs = lineup26.contains(new com.kandanda.tier2.TalismanAbsence(f[1], rd));
                        if (habs || aabs) {
                            double alh = habs ? lh * com.kandanda.tier2.TalismanEffect.absenceMultiplier(
                                    dep26.getOrDefault(f[0], 0.0), fit26.getOrDefault(f[0], 0.0), 0.5) : lh;
                            double ala = aabs ? la * com.kandanda.tier2.TalismanEffect.absenceMultiplier(
                                    dep26.getOrDefault(f[1], 0.0), fit26.getOrDefault(f[1], 0.0), 0.5) : la;
                            var mk2 = new com.kandanda.model.MarketCalculator(model26.buildGrid(alh, ala)).headlineMarkets();
                            System.out.printf("   [EXPERIMENTAL lineup-adjusted] 1: %.1f%%  X: %.1f%%  2: %.1f%%%n",
                                    100 * mk2.get("Home win"), 100 * mk2.get("Draw"), 100 * mk2.get("Away win"));
                        }
                    }
                    System.out.println();
                    System.out.println("Commit this output BEFORE the games. Compare to SportRadar only");
                    System.out.println("AFTER results are scored (no mimicry). Scoring = S19: results get");
                    System.out.println("appended to worldcup-2026.json and the locked calls are Briered.");
                    System.out.println("=============================================");
                }
            }
        };
    }


    /** Round label for a locked 2026 fixture (R32 for the July-3 trio, else R16). */
    private static String fixtureRound(String home, String away) {
        String pair = home + "|" + away;
        if (java.util.Set.of("Argentina|Cape Verde", "Colombia|Ghana", "Australia|Egypt")
                .contains(pair)) return "Round of 32";
        if (java.util.Set.of("France|Morocco", "Norway|England").contains(pair))
            return "Quarter-finals";
        return "Round of 16";
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