package com.kandanda.tier2;

import com.kandanda.data.MatchResult;
import com.kandanda.rating.TeamRating;

import java.util.*;

/**
 * Team-form modifier: nudges a team's attack rating by how much it over- or
 * under-performed its own expected goals during the GROUP stage.
 *
 * <h2>How form is computed (no leakage)</h2>
 * For each team, over the training (group) matches:
 * <pre>
 *   formRatio = actual group goals / expected group goals
 * </pre>
 * where expected goals come from the base ratings. A ratio &gt; 1 means the team scored
 * more than its rating predicted (possible momentum/confidence); &lt; 1 means less.
 * This uses only group data, so it never peeks at the knockouts it will influence.
 *
 * <h2>How it is applied</h2>
 * <pre>
 *   adjustedAttack = baseAttack × (1 + weight × (formRatio − 1))
 * </pre>
 * {@code weight = 0} returns base ratings unchanged (identity). A fractional weight
 * applies only PART of the form signal, reflecting that 3-game over-performance is partly
 * real momentum and partly variance. A 2022 sweep found the calibration sweet spot around
 * weight ≈ 0.5 — and crucially the score got WORSE past that, the fingerprint of a real
 * (but partial) signal rather than noise being overfit.
 *
 * <p>Defence is left unchanged: attacking over-performance is the cleaner, more
 * interpretable signal. A separate defensive-form modifier could be added later if it
 * earns its place on the Scoreboard.
 */
public class FormModifier implements RatingModifier {

    private final double weight;

    public FormModifier(double weight) {
        this.weight = weight;
    }

    @Override
    public List<TeamRating> apply(List<TeamRating> baseRatings, List<MatchResult> trainingMatches) {
        // Index base ratings by team for expected-goals lookups.
        Map<String, TeamRating> base = new HashMap<>();
        for (TeamRating r : baseRatings) base.put(r.team(), r);

        // League average over the training matches (same basis as the rating fit).
        int totalGoals = 0;
        for (MatchResult m : trainingMatches) totalGoals += m.getHomeGoals() + m.getAwayGoals();
        double avg = trainingMatches.isEmpty() ? 0 : totalGoals / (2.0 * trainingMatches.size());

        // Accumulate actual and expected group goals per team.
        Map<String, Double> actual = new HashMap<>();
        Map<String, Double> expected = new HashMap<>();
        for (MatchResult m : trainingMatches) {
            String h = m.getHomeTeam().getName();
            String a = m.getAwayTeam().getName();
            TeamRating rh = base.get(h), ra = base.get(a);
            if (rh == null || ra == null) continue;

            actual.merge(h, (double) m.getHomeGoals(), Double::sum);
            actual.merge(a, (double) m.getAwayGoals(), Double::sum);
            expected.merge(h, rh.attack() * ra.defence() * avg, Double::sum);
            expected.merge(a, ra.attack() * rh.defence() * avg, Double::sum);
        }

        // Apply the form adjustment to attack only.
        List<TeamRating> adjusted = new ArrayList<>();
        for (TeamRating r : baseRatings) {
            double exp = expected.getOrDefault(r.team(), 0.0);
            double act = actual.getOrDefault(r.team(), 0.0);
            double formRatio = exp > 0 ? act / exp : 1.0;
            double newAttack = r.attack() * (1.0 + weight * (formRatio - 1.0));
            // Guard against a pathological negative (only possible with extreme weights).
            if (newAttack < 0) newAttack = 0;
            adjusted.add(new TeamRating(r.team(), newAttack, r.defence()));
        }
        return adjusted;
    }

    @Override
    public String name() {
        return String.format("form(w=%.2f)", weight);
    }
}