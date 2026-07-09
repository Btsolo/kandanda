package com.kandanda.profile;

import java.util.Objects;

/**
 * A player profile — FM-style: NOT a single rating, but a set of attributes that
 * modify how the TEAM performs, split by PROVENANCE.
 *
 * <h2>Design lineage</h2>
 * FM separates visible/measurable skill outputs from hidden character/role reads.
 * Kandanda keeps that split explicit:
 * <ul>
 *   <li>{@link DataDerived}: measured values, usually scraped per-90.</li>
 *   <li>{@link JudgmentDerived}: human/analyst hypotheses such as talisman load.</li>
 *   <li>{@link TacticalDerived}: role/archetype vectors used by the chemistry and
 *       intelligence layer.</li>
 * </ul>
 *
 * <p>All tactical numbers are 0..1 and should be read as <b>relative load/share</b>,
 * not raw ability. They are hypotheses until a modifier using them passes the judge.
 */
public record PlayerProfile(
        String name,
        String team,
        PositionGroup position,
        DataDerived data,
        JudgmentDerived judgment,
        TacticalDerived tactical
) {
    /** Backward-compatible constructor for older profile files. */
    public PlayerProfile(String name, String team, PositionGroup position,
                         DataDerived data, JudgmentDerived judgment) {
        this(name, team, position, data, judgment, TacticalDerived.neutral());
    }

    public PlayerProfile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(team, "team");
        Objects.requireNonNull(position, "position");
        data = Objects.requireNonNullElseGet(data, DataDerived::none);
        judgment = Objects.requireNonNullElseGet(judgment, JudgmentDerived::neutral);
        tactical = Objects.requireNonNullElseGet(tactical, TacticalDerived::neutral);
    }

    /** Coarse position groups; each has different position-appropriate metrics. */
    public enum PositionGroup { GK, DEF, MID, ATT }

    /**
     * MEASURED attributes — pullable from StatsBomb/FBref/Opta-like sources.
     * All nullable per-field: fill what the source provides, leave the rest null.
     */
    public record DataDerived(
            Double minutesPlayed,
            Double goalsPer90,
            Double xgPer90,
            Double xaPer90,
            Double chancesCreatedPer90
    ) {
        public static DataDerived none() {
            return new DataDerived(null, null, null, null, null);
        }
    }

    /**
     * JUDGED attributes — from the developer's football eye and analyst transcripts.
     *
     * @param talismanResponsibility 0..1: how much the team function/load runs through him
     * @param bigMatchTemperament -1..+1: big-match lift/drop; 0 = neutral/unknown
     * @param roleFit -1..+1: how well the current system platforms him; 0 = neutral/unknown
     */
    public record JudgmentDerived(
            double talismanResponsibility,
            double bigMatchTemperament,
            double roleFit
    ) {
        public JudgmentDerived {
            requireRange("talismanResponsibility", talismanResponsibility, 0.0, 1.0);
            requireRange("bigMatchTemperament", bigMatchTemperament, -1.0, 1.0);
            requireRange("roleFit", roleFit, -1.0, 1.0);
        }

        public static JudgmentDerived neutral() {
            return new JudgmentDerived(0.0, 0.0, 0.0);
        }
    }

    /**
     * TACTICAL attributes — the football-intelligence vector.
     *
     * <p>These are not raw individual ratings. They represent the share of a team
     * function that the player can realistically carry in the current tournament system.
     * For example, a defender can score high in progressionValue or chanceCreationValue
     * if he is a two-phase full-back/wing-back.</p>
     *
     * @param fmRole Football Manager-style role label
     * @param archetype broader role/archetype used by modifiers and reports
     * @param activeAttributes short FM-style attributes currently expressed
     * @param impactNote short source/intelligence note
     * @param buildUpValue starting/recycling possession, keeper distribution, first phase
     * @param progressionValue line-breaking passes/carries, xT movement
     * @param chanceCreationValue final ball, cutbacks, xA-like actions
     * @param boxThreat shooting, late box arrival, striker gravity
     * @param widthValue stretching the pitch, overlaps, wide isolation
     * @param pressingValue counter-press trigger, work rate, defensive intensity
     * @param defensiveControl duels, aerials, box protection, rest defence, shot-stopping
     * @param transitionValue counter-attack speed, outlet value, vertical threat
     * @param possessionSecurity press resistance, tempo safety, low-turnover control
     * @param setPieceValue attacking/defensive set-piece value
     * @param lateGameImpact substitute/closing-game effect, fatigue exploitation
     */
    public record TacticalDerived(
            String fmRole,
            String archetype,
            String activeAttributes,
            String impactNote,
            double buildUpValue,
            double progressionValue,
            double chanceCreationValue,
            double boxThreat,
            double widthValue,
            double pressingValue,
            double defensiveControl,
            double transitionValue,
            double possessionSecurity,
            double setPieceValue,
            double lateGameImpact
    ) {
        public TacticalDerived {
            fmRole = Objects.requireNonNullElse(fmRole, "");
            archetype = Objects.requireNonNullElse(archetype, "");
            activeAttributes = Objects.requireNonNullElse(activeAttributes, "");
            impactNote = Objects.requireNonNullElse(impactNote, "");
            requireUnit("buildUpValue", buildUpValue);
            requireUnit("progressionValue", progressionValue);
            requireUnit("chanceCreationValue", chanceCreationValue);
            requireUnit("boxThreat", boxThreat);
            requireUnit("widthValue", widthValue);
            requireUnit("pressingValue", pressingValue);
            requireUnit("defensiveControl", defensiveControl);
            requireUnit("transitionValue", transitionValue);
            requireUnit("possessionSecurity", possessionSecurity);
            requireUnit("setPieceValue", setPieceValue);
            requireUnit("lateGameImpact", lateGameImpact);
        }

        public static TacticalDerived neutral() {
            return new TacticalDerived("", "", "", "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
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