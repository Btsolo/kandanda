package com.kandanda.tier2;

import com.kandanda.data.MatchResult;
import com.kandanda.rating.TeamRating;

import java.util.List;

/**
 * A Tier 2 "real-life moderator": takes the base ratings (Tier 1) plus the group-stage
 * context, and returns ADJUSTED ratings. This is the single extension point through which
 * every Tier 2 idea — team form, manager risk profile, standout players, chemistry —
 * plugs into the prediction pipeline.
 *
 * <h2>Design contract (read before implementing a new modifier)</h2>
 * <ul>
 *   <li><b>No leakage.</b> A modifier may use ONLY information knowable before the games
 *       it will influence — here, the group stage. It must never peek at the knockout
 *       results it is helping to predict. This is the rule that keeps Tier 2 honest.</li>
 *   <li><b>Identity at zero.</b> Every modifier carries a strength/weight that, when 0,
 *       returns the base ratings unchanged. This lets the backtest measure each
 *       modifier's effect by sweeping its weight from 0 upward.</li>
 *   <li><b>Pure.</b> Given the same inputs it returns the same output (no hidden state),
 *       so results are reproducible.</li>
 * </ul>
 *
 * <p>Modifiers are stacked in a {@link ModifierPipeline}; the order is the order applied.
 */
public interface RatingModifier {

    /**
     * @param baseRatings the Tier 1 ratings (already prior-smoothed)
     * @param trainingMatches the matches the base ratings were fit on (group stage) —
     *                        the ONLY data a modifier may read, to avoid leakage
     * @return adjusted ratings (same teams; a modifier must not add or drop teams)
     */
    List<TeamRating> apply(List<TeamRating> baseRatings, List<MatchResult> trainingMatches);

    /** Human-readable name for reporting which modifiers are active. */
    String name();
}