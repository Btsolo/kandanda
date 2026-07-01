package com.kandanda.analysis;

import com.kandanda.data.MatchResult;
import com.kandanda.data.Team;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the residual analyzer.
 *
 * <p>We verify the decomposition behaves correctly on constructed cases:
 * a team that consistently out-CREATES shows a positive creation residual; a team that
 * scores far above its xG shows a positive FINISHING residual (the luck signal).
 */
class ResidualAnalyzerTest {

    private MatchResult m(String round, String home, String away,
                          int hg, int ag, double hx, double ax) {
        return new MatchResult("T", round, LocalDate.of(2022, 1, 1),
                new Team(home), new Team(away), hg, ag, hx, ax);
    }

    @Test
    void throwsOnEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResidualAnalyzer().analyse(List.of()));
    }

    @Test
    void finishingResidualCapturesOverConversion() {
        // A scores 3 goals off just 1.0 xG every game -> big POSITIVE finishing residual
        // (converting way above expectation = the luck signal).
        List<MatchResult> t = new ArrayList<>();
        t.add(m("Matchday 1", "A", "B", 3, 0, 1.0, 1.0));
        t.add(m("Matchday 2", "A", "C", 3, 0, 1.0, 1.0));
        t.add(m("Matchday 3", "A", "D", 3, 0, 1.0, 1.0));
        // filler so B/C/D exist with their own games
        t.add(m("Matchday 1", "B", "C", 1, 1, 1.0, 1.0));
        t.add(m("Matchday 2", "D", "B", 1, 1, 1.0, 1.0));
        t.add(m("Matchday 3", "C", "D", 1, 1, 1.0, 1.0));

        Map<String, TeamResidual> r = new ResidualAnalyzer().analyse(t).stream()
                .collect(Collectors.toMap(TeamResidual::team, x -> x));
        // A converts 3 goals on 1.0 xG -> finishing mean strongly positive.
        assertTrue(r.get("A").finishingMean() > 1.0,
                "A over-converts, finishing residual should be large positive");
    }

    @Test
    void creationResidualCapturesOverPerformanceVsExpectation() {
        // A generates much more xG than its opponents -> should have positive creation mean.
        List<MatchResult> t = new ArrayList<>();
        t.add(m("Matchday 1", "A", "B", 1, 0, 3.0, 0.3));
        t.add(m("Matchday 2", "A", "C", 1, 0, 3.0, 0.3));
        t.add(m("Matchday 3", "A", "D", 1, 0, 3.0, 0.3));
        t.add(m("Matchday 1", "B", "C", 1, 1, 1.0, 1.0));
        t.add(m("Matchday 2", "D", "B", 1, 1, 1.0, 1.0));
        t.add(m("Matchday 3", "C", "D", 1, 1, 1.0, 1.0));

        Map<String, TeamResidual> r = new ResidualAnalyzer().analyse(t).stream()
                .collect(Collectors.toMap(TeamResidual::team, x -> x));
        // A creates 3.0 xG/game; even after the model rates it highly, it's the top creator.
        assertEquals("A", new ResidualAnalyzer().analyse(t).get(0).team(),
                "A should sort first by creation mean");
        assertTrue(r.get("A").matches() == 3);
    }

    @Test
    void readClassifiesUnderratedTeam() {
        // Directly construct a residual: creates well, finishes cold -> UNDERRATED read.
        var tr = new TeamResidual("X", 3, 0.5, 1.2, -0.4, -1.0);
        assertTrue(tr.read().contains("UNDERRATED"));
    }

    @Test
    void readClassifiesLuckyTeam() {
        var tr = new TeamResidual("Y", 3, -0.3, -0.8, 0.5, 1.1);
        assertTrue(tr.read().contains("LUCKY"));
    }
}
