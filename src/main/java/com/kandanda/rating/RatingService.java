package com.kandanda.rating;

import com.kandanda.data.MatchResult;

import java.util.*;

/**
 * Fits attack and defence strength ratings from a set of match results, using the
 * classic iterative Maher-style scheme.
 *
 * <h2>The idea</h2>
 * A team's attack should be judged against the defences it faced, and its defence
 * against the attacks it faced — but those are circular (everything depends on
 * everything). We break the circle by iteration:
 * <ol>
 *   <li>Start every team at average (attack = defence = 1.0).</li>
 *   <li>Update each attack = (goals scored) / (sum over games of opponent_defence × avg).</li>
 *   <li>Re-centre attacks to mean 1.0.</li>
 *   <li>Update each defence = (goals conceded) / (sum over games of opponent_attack × avg).</li>
 *   <li>Re-centre defences to mean 1.0.</li>
 *   <li>Repeat until the numbers stop moving (convergence).</li>
 * </ol>
 *
 * <h2>Modelling decisions</h2>
 * <ul>
 *   <li><b>No home advantage.</b> A World Cup is played at neutral venues, so we set
 *       the home factor to 1.0. (For club competitions later, this becomes a real
 *       parameter.)</li>
 *   <li><b>Known weakness — small-sample noise.</b> With only 3–7 games per team,
 *       a single shock result (e.g. beating a strong side once) can wildly inflate a
 *       rating. These raw ratings are deliberately the honest, un-regularised version;
 *       a pre-tournament prior to tame the noise is a planned later improvement,
 *       added only once the Scoreboard shows it helps.</li>
 * </ul>
 *
 * <p>This Java implementation is a direct port of a prototype verified against the
 * real 2022 data (finalists France/Argentina correctly topped the attack table).
 */
public class RatingService {

    private static final int MAX_ITERATIONS = 100;
    private static final double NEUTRAL_HOME_ADVANTAGE = 1.0; // World Cup = neutral venues

    /**
     * Prior strength k (additive smoothing): the number of "phantom average games" each
     * team is treated as having also played. k=0 means no prior (raw ratings). Larger k
     * pulls every rating toward 1.0 (average), which tames small-sample noise — and
     * matters MORE for teams with fewer games, exactly when it's needed.
     *
     * <p>A 2022 sweep showed k=0 scored Brier 0.31 on knockouts (worse than a coin), while
     * a substantial prior pushed below the 0.25 baseline — strong evidence that group-stage
     * ratings carry little knockout signal, so shrinking them toward average helps.
     */
    private final double priorStrength;

    /** Default: no prior (k=0), preserving the original raw-rating behaviour. */
    public RatingService() {
        this(0.0);
    }

    public RatingService(double priorStrength) {
        if (priorStrength < 0) {
            throw new IllegalArgumentException("priorStrength (k) must be >= 0");
        }
        this.priorStrength = priorStrength;
    }

    /**
     * League average goals scored by one team in one game, over the given matches.
     * This is the baseline the Poisson model multiplies by; exposed so callers build
     * the match model with the SAME average the ratings were fitted against.
     */
    public static double leagueAverageGoals(List<MatchResult> matches) {
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("No matches");
        }
        int total = 0;
        for (MatchResult m : matches) total += m.getHomeGoals() + m.getAwayGoals();
        return total / (2.0 * matches.size());
    }

    /**
     * Fit ratings from the given matches.
     *
     * @param matches the matches to fit on (e.g. all of WC-2022, or only rounds played
     *                so far — the caller controls the point-in-time window)
     * @return one {@link TeamRating} per team appearing in the matches
     */
    public List<TeamRating> fit(List<MatchResult> matches) {
        if (matches == null || matches.isEmpty()) {
            throw new IllegalArgumentException("Cannot fit ratings on no matches");
        }

        // Collect teams.
        Set<String> teams = new TreeSet<>();
        for (MatchResult m : matches) {
            teams.add(m.getHomeTeam().getName());
            teams.add(m.getAwayTeam().getName());
        }

        // League average goals scored by one team in one game (single source of truth).
        double avg = leagueAverageGoals(matches);
        // Additive-smoothing prior: k phantom average games (k=0 => no prior).
        double prior = priorStrength * avg;

        Map<String, Double> attack = new HashMap<>();
        Map<String, Double> defence = new HashMap<>();
        for (String t : teams) {
            attack.put(t, 1.0);
            defence.put(t, 1.0);
        }

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            updateAttack(teams, matches, attack, defence, avg, prior);
            updateDefence(teams, matches, attack, defence, avg, prior);
        }

        List<TeamRating> result = new ArrayList<>();
        for (String t : teams) {
            result.add(new TeamRating(t, attack.get(t), defence.get(t)));
        }
        result.sort(Comparator.comparingDouble(TeamRating::attack).reversed());
        return result;
    }

    private void updateAttack(Set<String> teams, List<MatchResult> matches,
                              Map<String, Double> attack, Map<String, Double> defence,
                              double avg, double prior) {
        Map<String, Double> next = new HashMap<>();
        for (String t : teams) {
            double scored = 0.0, expected = 0.0;
            for (MatchResult m : matches) {
                String home = m.getHomeTeam().getName();
                String away = m.getAwayTeam().getName();
                if (home.equals(t)) {
                    scored += m.getHomeGoals();
                    expected += defence.get(away) * avg;
                }
                if (away.equals(t)) {
                    scored += m.getAwayGoals();
                    expected += defence.get(home) * avg;
                }
            }
            // Additive smoothing: add `prior` phantom average goals to numerator AND
            // denominator. Pulls toward 1.0; effect shrinks as real games accumulate.
            double denom = expected + prior;
            next.put(t, denom > 0 ? (scored + prior) / denom : 1.0);
        }
        recentreToMeanOne(next);
        attack.putAll(next);
    }

    private void updateDefence(Set<String> teams, List<MatchResult> matches,
                               Map<String, Double> attack, Map<String, Double> defence,
                               double avg, double prior) {
        Map<String, Double> next = new HashMap<>();
        for (String t : teams) {
            double conceded = 0.0, expected = 0.0;
            for (MatchResult m : matches) {
                String home = m.getHomeTeam().getName();
                String away = m.getAwayTeam().getName();
                if (home.equals(t)) {
                    conceded += m.getAwayGoals();
                    expected += attack.get(away) * avg;
                }
                if (away.equals(t)) {
                    conceded += m.getHomeGoals();
                    expected += attack.get(home) * avg;
                }
            }
            double denom = expected + prior;
            next.put(t, denom > 0 ? (conceded + prior) / denom : 1.0);
        }
        recentreToMeanOne(next);
        defence.putAll(next);
    }

    /** Re-centre a set of values so their mean is exactly 1.0 (keeps the scale stable). */
    private void recentreToMeanOne(Map<String, Double> values) {
        double mean = values.values().stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
        if (mean <= 0) return;
        values.replaceAll((k, v) -> v / mean);
    }
}