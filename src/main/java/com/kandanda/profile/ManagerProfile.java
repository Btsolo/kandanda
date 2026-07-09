package com.kandanda.profile;

import java.util.Objects;

/**
 * A manager profile. The original slice was just {@code riskAxis}; this version
 * keeps that backward-compatible core and adds a tactical plan vector for the
 * chemistry/intelligence layer.
 *
 * <p>All tactical-plan values are 0..1 intensity/bias values. They should be
 * treated as pre-match hypotheses, not truth.</p>
 */
public record ManagerProfile(
        String name,
        String team,
        double riskAxis,
        TacticalPlan tactics
) {
    /** Backward-compatible constructor for older code. */
    public ManagerProfile(String name, String team, double riskAxis) {
        this(name, team, riskAxis, TacticalPlan.neutral());
    }

    public ManagerProfile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(team, "team");
        requireRange("riskAxis", riskAxis, -1.0, 1.0);
        tactics = Objects.requireNonNullElseGet(tactics, TacticalPlan::neutral);
    }

    public static ManagerProfile neutral(String name, String team) {
        return new ManagerProfile(name, team, 0.0, TacticalPlan.neutral());
    }

    /**
     * @param tacticalIdentity short label for the manager game model
     * @param notes human-readable tactical note
     * @param possessionBias preference for ball circulation/territorial control
     * @param pressingBias counter-press/high-press intensity
     * @param transitionBias verticality/counter-attack bias
     * @param lowBlockBias comfort defending deeper/protecting space
     * @param widthBias reliance on wide lanes/overlaps/wing isolation
     * @param setPieceBias set-piece reliance/value
     * @param substitutionAggression willingness/effectiveness in changing games from bench
     * @param tacticalFlexibility ability to change structure without breaking cohesion
     */
    public record TacticalPlan(
            String tacticalIdentity,
            String notes,
            double possessionBias,
            double pressingBias,
            double transitionBias,
            double lowBlockBias,
            double widthBias,
            double setPieceBias,
            double substitutionAggression,
            double tacticalFlexibility
    ) {
        public TacticalPlan {
            tacticalIdentity = Objects.requireNonNullElse(tacticalIdentity, "");
            notes = Objects.requireNonNullElse(notes, "");
            requireUnit("possessionBias", possessionBias);
            requireUnit("pressingBias", pressingBias);
            requireUnit("transitionBias", transitionBias);
            requireUnit("lowBlockBias", lowBlockBias);
            requireUnit("widthBias", widthBias);
            requireUnit("setPieceBias", setPieceBias);
            requireUnit("substitutionAggression", substitutionAggression);
            requireUnit("tacticalFlexibility", tacticalFlexibility);
        }

        public static TacticalPlan neutral() {
            return new TacticalPlan("", "", 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private static void requireUnit(String name, double value) {
        requireRange(name, value, 0.0, 1.0);
    }

    private static void requireRange(String name, double value, double min, double max) {
        if (value < min || value > max || Double.isNaN(value)) {
            throw new IllegalArgumentException(name + " must be in [" + min + "," + max + "]");
        }
    }
}