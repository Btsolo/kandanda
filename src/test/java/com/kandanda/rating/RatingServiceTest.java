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
}
