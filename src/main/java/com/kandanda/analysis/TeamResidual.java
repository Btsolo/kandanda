package com.kandanda.analysis;

/**
 * A team's residual profile from a set of matches: how their actual output deviated
 * from what the base-rate model expected, split into two independent stories.
 *
 * <h2>The two residuals (this split is the whole point)</h2>
 * <ul>
 *   <li><b>Creation residual</b> = xG − expected xG. Did the team create MORE or fewer
 *       chances than their rating predicted? Positive = outplayed expectation. This is
 *       the SKILL signal — it tends to persist.</li>
 *   <li><b>Finishing residual</b> = goals − xG. Did they convert their chances better or
 *       worse than an average finisher? This is largely the LUCK signal — finishing
 *       regresses to the mean, so a big finishing residual usually does NOT persist.</li>
 * </ul>
 *
 * <p>Combining them classifies a team:
 * <ul>
 *   <li>creation +, finishing − → <b>underrated</b> (creating but unlucky in front of
 *       goal; due a correction upward). The Belgium/Argentina-2022 pattern.</li>
 *   <li>creation −, finishing + → <b>overrated / lucky</b> (scoring on little; due to
 *       regress).</li>
 * </ul>
 *
 * <h2>Consistency (signal vs noise)</h2>
 * {@code creationConsistency} is mean ÷ (a floored standard deviation). A large absolute
 * value means the deviation was PERSISTENT across the team's matches (more likely real
 * signal); near zero means it was scattered (more likely luck). The standard deviation is
 * floored (see {@link ResidualAnalyzer}) so a 2–3 game run with a tiny spread can't look
 * spuriously "infinitely consistent".
 *
 * @param team                 team name
 * @param matches              number of matches this profile is built from
 * @param creationMean         average (xG − expected xG) per match
 * @param creationConsistency  creationMean ÷ floored-sd (persistence measure)
 * @param finishingMean        average (goals − xG) per match
 * @param finishingConsistency finishingMean ÷ floored-sd
 */
public record TeamResidual(
        String team,
        int matches,
        double creationMean,
        double creationConsistency,
        double finishingMean,
        double finishingConsistency
) {
    /** A short human-readable classification of the team's residual profile. */
    public String read() {
        if (creationMean > 0.2 && finishingMean < 0.0) {
            return "creates+, finishes cold -> UNDERRATED";
        }
        if (creationMean < -0.1 && finishingMean > 0.2) {
            return "creates-, finishes hot -> LUCKY/overrated";
        }
        if (creationMean > 0.2) {
            return "genuinely creating (skill)";
        }
        if (creationMean < -0.2) {
            return "under-creating (weak)";
        }
        return "around expectation";
    }

    @Override
    public String toString() {
        return String.format("%-16s n=%d creation %+.2f (c=%+.2f) finishing %+.2f (c=%+.2f) | %s",
                team, matches, creationMean, creationConsistency,
                finishingMean, finishingConsistency, read());
    }
}
