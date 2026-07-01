package com.kandanda.rating;

import com.kandanda.data.MatchResult;
import com.kandanda.data.Team;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the rating fit.
 *
 * <p>Strategy: a tiny synthetic league with an OBVIOUS hierarchy (Strong > Mid > Weak),
 * so we know the correct ordering by hand. This verifies the algorithm recovers the
 * truth, independent of any real-world knowledge. Verified against a Python prototype.
 */
class RatingServiceTest {

    private final RatingService service = new RatingService();

    /** Helper: build a MatchResult without a database. */
    private MatchResult match(String home, String away, int hg, int ag) {
        return new MatchResult("TEST", "R1", null,
                new Team(home), new Team(away), hg, ag);
    }

    private List<MatchResult> syntheticLeague() {
        List<MatchResult> ms = new ArrayList<>();
        ms.add(match("Strong", "Weak", 4, 0));
        ms.add(match("Strong", "Mid", 2, 0));
        ms.add(match("Mid", "Weak", 2, 0));
        ms.add(match("Weak", "Strong", 0, 3));
        ms.add(match("Mid", "Strong", 0, 1));
        ms.add(match("Weak", "Mid", 1, 2));
        return ms;
    }

    /** Match with xG, for xG-mode tests. */
    private MatchResult matchXg(String home, String away, int hg, int ag, double hx, double ax) {
        return new MatchResult("TEST", "R1", null,
                new Team(home), new Team(away), hg, ag, hx, ax);
    }

    @Test
    void xgModeUsesXgNotGoals() {
        // A beat B 0-3 on GOALS but dominated 3.0-0.2 on xG. In xG mode, A should rate
        // ABOVE B for attack — the opposite of what goals alone would say. This is the
        // Argentina-Saudi case in miniature.
        var matches = List.of(
                matchXg("A", "B", 0, 3, 3.0, 0.2),
                matchXg("B", "A", 3, 0, 0.2, 3.0));
        var xgService = new RatingService(0.0, true);
        Map<String, TeamRating> r = xgService.fit(matches).stream()
                .collect(Collectors.toMap(TeamRating::team, x -> x));
        assertTrue(r.get("A").attack() > r.get("B").attack(),
                "in xG mode, the xG-dominant team A should out-rate B on attack");
    }

    @Test
    void goalsModeIgnoresXg() {
        // Same matches, goals mode: B outscored A 6-0 on goals, so B should out-rate A.
        var matches = List.of(
                matchXg("A", "B", 0, 3, 3.0, 0.2),
                matchXg("B", "A", 3, 0, 0.2, 3.0));
        var goalsService = new RatingService(0.0, false);
        Map<String, TeamRating> r = goalsService.fit(matches).stream()
                .collect(Collectors.toMap(TeamRating::team, x -> x));
        assertTrue(r.get("B").attack() > r.get("A").attack(),
                "in goals mode, the higher-scoring team B should out-rate A");
    }

    @Test
    void xgModeFallsBackToGoalsWhenXgMissing() {
        // No xG present: xG mode must behave exactly like goals mode.
        var xgService = new RatingService(5.0, true);
        var goalsService = new RatingService(5.0, false);
        var a = xgService.fit(syntheticLeague());
        var b = goalsService.fit(syntheticLeague());
        // Same ordering and values, since the synthetic league has no xG.
        assertEquals(b.size(), a.size());
        assertEquals(b.get(0).team(), a.get(0).team());
        assertEquals(b.get(0).attack(), a.get(0).attack(), 1e-9);
    }

    @Test
    void recoversKnownHierarchy() {
        Map<String, TeamRating> r = service.fit(syntheticLeague()).stream()
                .collect(Collectors.toMap(TeamRating::team, x -> x));

        // Strong should have the highest attack and the best (lowest) defence.
        assertTrue(r.get("Strong").attack() > r.get("Mid").attack());
        assertTrue(r.get("Mid").attack() > r.get("Weak").attack());
        assertTrue(r.get("Strong").defence() < r.get("Mid").defence());
        assertTrue(r.get("Mid").defence() < r.get("Weak").defence());
    }

    @Test
    void attacksAreCentredNearOne() {
        // By construction we re-centre to mean 1.0, so the average attack ~= 1.0.
        var ratings = service.fit(syntheticLeague());
        double meanAttack = ratings.stream().mapToDouble(TeamRating::attack).average().orElse(0);
        assertEquals(1.0, meanAttack, 1e-9);
    }

    @Test
    void midTeamSitsInTheMiddle() {
        Map<String, TeamRating> r = service.fit(syntheticLeague()).stream()
                .collect(Collectors.toMap(TeamRating::team, x -> x));
        // Mid is symmetric in this league: attack ~= defence ~= 0.9.
        assertEquals(0.9, r.get("Mid").attack(), 0.05);
    }

    @Test
    void emptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> service.fit(List.of()));
    }

    @Test
    void priorShrinksRatingsTowardOne() {
        // With a large prior, every rating should be pulled close to 1.0 (average).
        var strongPrior = new RatingService(40.0);
        var ratings = strongPrior.fit(syntheticLeague());
        for (var r : ratings) {
            assertTrue(Math.abs(r.attack() - 1.0) < 0.25,
                    "strong prior should pull attack near 1.0, got " + r.attack());
            assertTrue(Math.abs(r.defence() - 1.0) < 0.25,
                    "strong prior should pull defence near 1.0, got " + r.defence());
        }
    }

    @Test
    void priorReducesSpreadOfRatings() {
        // The spread (max-min attack) should shrink as the prior strengthens.
        double spreadNoPrior = spread(new RatingService(0.0).fit(syntheticLeague()));
        double spreadWithPrior = spread(new RatingService(5.0).fit(syntheticLeague()));
        assertTrue(spreadWithPrior < spreadNoPrior,
                "prior should compress the rating spread");
    }

    private double spread(java.util.List<TeamRating> ratings) {
        double max = ratings.stream().mapToDouble(TeamRating::attack).max().orElse(0);
        double min = ratings.stream().mapToDouble(TeamRating::attack).min().orElse(0);
        return max - min;
    }

    @Test
    void negativePriorThrows() {
        assertThrows(IllegalArgumentException.class, () -> new RatingService(-1.0));
    }
}