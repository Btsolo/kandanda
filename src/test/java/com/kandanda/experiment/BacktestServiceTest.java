package com.kandanda.experiment;

import com.kandanda.data.MatchResult;
import com.kandanda.data.Team;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the backtest experiment.
 *
 * <p>We can't assert exact calibration numbers without committing the whole 2022
 * dataset into the test, so we verify the STRUCTURE of the experiment: the split works,
 * both reports are produced, and the all-data (leakage) model scores at least as well
 * as the group-only model — which must be true, because leakage can only help the
 * score. That relationship is the test's real assertion and also documents WHY we
 * don't trust the all-data number.
 */
class BacktestServiceTest {

    private MatchResult m(String round, String home, String away, int hg, int ag) {
        return new MatchResult("T", round, LocalDate.of(2022, 1, 1),
                new Team(home), new Team(away), hg, ag);
    }

    /** A tiny synthetic tournament: 4 teams, a group stage, then a knockout. */
    private List<MatchResult> miniTournament() {
        List<MatchResult> t = new ArrayList<>();
        // Group stage (round-robin-ish) — establishes a clear hierarchy A>B>C>D.
        t.add(m("Matchday 1", "A", "D", 3, 0));
        t.add(m("Matchday 1", "B", "C", 2, 1));
        t.add(m("Matchday 2", "A", "C", 2, 0));
        t.add(m("Matchday 2", "B", "D", 2, 0));
        t.add(m("Matchday 3", "A", "B", 1, 0));
        t.add(m("Matchday 3", "C", "D", 2, 1));
        // Knockout — same teams, so both models can rate them.
        t.add(m("Semi-finals", "A", "C", 2, 1));
        t.add(m("Final", "A", "B", 1, 0));
        return t;
    }

    @Test
    void splitsGroupAndKnockout() {
        var result = new BacktestService().run(miniTournament());
        assertEquals(6, result.groupMatches());
        assertEquals(2, result.knockoutMatches());
    }

    @Test
    void bothReportsProduced() {
        var result = new BacktestService().run(miniTournament());
        assertNotNull(result.groupOnly());
        assertNotNull(result.allData());
        assertTrue(result.groupOnly().count() > 0);
        assertTrue(result.allData().count() > 0);
    }

    @Test
    void leakageModelScoresNoWorseThanFairModel() {
        // The all-data model has seen the knockout results it predicts, so its Brier
        // must be <= the group-only model's. This is the whole reason we don't trust it.
        var result = new BacktestService().run(miniTournament());
        assertTrue(result.allData().brier() <= result.groupOnly().brier() + 1e-9,
                "Leakage model should score at least as well as the fair model");
    }

    @Test
    void throwsIfNoKnockouts() {
        List<MatchResult> groupOnly = List.of(
                m("Matchday 1", "A", "B", 1, 0),
                m("Matchday 2", "A", "B", 2, 1));
        assertThrows(IllegalStateException.class,
                () -> new BacktestService().run(groupOnly));
    }
}
