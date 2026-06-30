package com.kandanda.tier2;

import com.kandanda.data.MatchResult;
import com.kandanda.data.Team;
import com.kandanda.rating.TeamRating;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Tier 2 modifier framework and the team-form modifier.
 *
 * <p>Core guarantees verified:
 * <ul>
 *   <li>weight 0 = identity (base ratings unchanged) — required so the backtest can
 *       measure each modifier against the base rate;</li>
 *   <li>an overperforming team's attack is boosted, an underperformer's is cut;</li>
 *   <li>the empty pipeline is a no-op.</li>
 * </ul>
 */
class FormModifierTest {

    private MatchResult m(String home, String away, int hg, int ag) {
        return new MatchResult("T", "Matchday 1", LocalDate.of(2022, 1, 1),
                new Team(home), new Team(away), hg, ag);
    }

    /** Base ratings: two equal, average teams. */
    private List<TeamRating> equalRatings() {
        return List.of(new TeamRating("A", 1.0, 1.0), new TeamRating("B", 1.0, 1.0));
    }

    @Test
    void weightZeroIsIdentity() {
        var matches = List.of(m("A", "B", 5, 0)); // A massively overperforms
        var out = new FormModifier(0.0).apply(equalRatings(), matches);
        var byName = out.stream().collect(Collectors.toMap(TeamRating::team, x -> x));
        // weight 0 must leave attack exactly as the base (1.0).
        assertEquals(1.0, byName.get("A").attack(), 1e-12);
        assertEquals(1.0, byName.get("B").attack(), 1e-12);
    }

    @Test
    void overperformerGetsAttackBoost() {
        // A scores 5 vs an average defence; expected ~1.0, so formRatio >> 1.
        var matches = List.of(m("A", "B", 5, 0));
        var out = new FormModifier(0.5).apply(equalRatings(), matches);
        var byName = out.stream().collect(Collectors.toMap(TeamRating::team, x -> x));
        assertTrue(byName.get("A").attack() > 1.0, "overperformer A should be boosted");
        assertTrue(byName.get("B").attack() < 1.0, "B scored 0, should be cut");
    }

    @Test
    void defenceIsUnchangedByFormModifier() {
        var matches = List.of(m("A", "B", 5, 0));
        var out = new FormModifier(0.5).apply(equalRatings(), matches);
        for (var r : out) {
            assertEquals(1.0, r.defence(), 1e-12, "form modifier must not touch defence");
        }
    }

    @Test
    void emptyPipelineIsNoOp() {
        var base = equalRatings();
        var out = ModifierPipeline.empty().apply(base, List.of(m("A", "B", 5, 0)));
        assertEquals(base, out);
    }

    @Test
    void pipelineDescribesActiveModifiers() {
        var p = new ModifierPipeline(List.of(new FormModifier(0.5)));
        assertTrue(p.describe().contains("form"));
        assertEquals("base only", ModifierPipeline.empty().describe());
    }
}