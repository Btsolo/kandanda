package com.kandanda.tier2;

import com.kandanda.data.MatchResult;
import com.kandanda.rating.TeamRating;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies a sequence of {@link RatingModifier}s to the base ratings, in order.
 *
 * <p>An empty pipeline returns the base ratings unchanged — so "Tier 1 only" is just an
 * empty pipeline. Adding a modifier is how a Tier 2 feature switches on. The backtest can
 * build pipelines with different modifier sets and score each, which is how every Tier 2
 * idea proves (or fails to prove) that it improves calibration.
 */
public class ModifierPipeline {

    private final List<RatingModifier> modifiers;

    public ModifierPipeline(List<RatingModifier> modifiers) {
        this.modifiers = List.copyOf(modifiers);
    }

    /** Convenience for the no-modifier (Tier 1 only) case. */
    public static ModifierPipeline empty() {
        return new ModifierPipeline(List.of());
    }

    public List<TeamRating> apply(List<TeamRating> baseRatings, List<MatchResult> trainingMatches) {
        List<TeamRating> current = baseRatings;
        for (RatingModifier m : modifiers) {
            current = m.apply(current, trainingMatches);
        }
        return current;
    }

    /** Names of active modifiers, for reporting. */
    public String describe() {
        if (modifiers.isEmpty()) return "base only";
        List<String> names = new ArrayList<>();
        for (RatingModifier m : modifiers) names.add(m.name());
        return String.join(" + ", names);
    }
}