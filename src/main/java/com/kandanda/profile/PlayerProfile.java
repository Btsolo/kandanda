package com.kandanda.profile;

/**
 * A player profile — FM-style: NOT a single rating, but a set of attributes that
 * modify how the TEAM performs, split by PROVENANCE.
 *
 * <h2>Design lineage (doc 04 §4b)</h2>
 * Reverse-engineered from Football Manager's structure rather than invented:
 * <ul>
 *   <li>FM separates <b>visible skill attributes</b> (technical/mental/physical, weighted
 *       by role) from <b>hidden character attributes</b> (Consistency, Important Matches,
 *       Pressure, Professionalism). Our split mirrors that exactly:
 *       {@link DataDerived} ≈ visible/measurable, {@link JudgmentDerived} ≈ hidden/character.</li>
 *   <li>FM's <i>Important Matches</i> hidden attribute IS our {@code bigMatchTemperament}.
 *       FM's <i>Consistency</i> IS what the residual analyzer's consistency measure
 *       estimates from real data — the analyzer is an instrument for estimating FM-style
 *       hidden attributes empirically.</li>
 *   <li>FM lesson: attributes work IN CONJUNCTION, never isolated. Profiles feed
 *       interaction-aware modifiers; they are never summed naively.</li>
 * </ul>
 *
 * <h2>The two provenances are physically separate types</h2>
 * You cannot build a profile without declaring which claims are measured and which are
 * judged. This is the yin-yang principle enforced by the type system: when the judge
 * (residual analyzer) validates or rejects a signal, we always know whether it came from
 * stats or from a human read.
 *
 * <p>Plain record, not JPA — profiles are reference data loaded/curated per tournament,
 * not accumulating match rows. Persistence can come later if needed.
 *
 * @param name     player name
 * @param team     national team
 * @param position coarse position group; determines WHICH data-derived stats are
 *                 meaningful (goals/xG for ATT, xA/creation for MID, defensive actions
 *                 for DEF, post-shot xG for GK) — the position-appropriate-data rule
 * @param data     measured attributes (nullable fields inside; fill what sources provide)
 * @param judgment judged attributes (from the developer's eye + analyst transcripts)
 */
public record PlayerProfile(
        String name,
        String team,
        PositionGroup position,
        DataDerived data,
        JudgmentDerived judgment
) {

    /** Coarse position groups; each has different position-appropriate metrics. */
    public enum PositionGroup { GK, DEF, MID, ATT }

    /**
     * MEASURED attributes — pullable from StatsBomb/FBref. All nullable per-field:
     * fill what the position and the source provide, leave the rest null. Per-90
     * basis so players with different minutes are comparable.
     *
     * <p>Position-appropriate usage (not enforced, but the convention):
     * ATT → goalsPer90/xgPer90; MID → xaPer90/chancesCreatedPer90 (+ progressive
     * passes later); DEF → defensive actions (later); GK → post-shot xG prevented
     * (later). Fields are added ONLY when a modifier actually uses them — design
     * deep, implement minimal.
     */
    public record DataDerived(
            Double minutesPlayed,
            Double goalsPer90,
            Double xgPer90,
            Double xaPer90,
            Double chancesCreatedPer90
    ) {
        /** Convenience: an empty data block (all unknown). */
        public static DataDerived none() {
            return new DataDerived(null, null, null, null, null);
        }
    }

    /**
     * JUDGED attributes — from the developer's football eye and analyst transcripts.
     * These exist in NO dataset (FM's "hidden attributes"). Every value is a HYPOTHESIS
     * until the residual analyzer validates the modifier that uses it.
     *
     * @param talismanResponsibility 0..1 — how much the team is built around this player
     *                               (Messi/Kane/James-Rodríguez high; squad player ~0).
     *                               High responsibility also means high blame-load (H9).
     * @param bigMatchTemperament    -1..+1 — rises (+) or shrinks (−) on the big stage.
     *                               FM's "Important Matches". 0 = neutral/unknown.
     * @param roleFit                -1..+1 — how well the CURRENT system platforms him
     *                               (Ronaldo-in-Martinez-system negative per H3;
     *                               Gyökeres-as-dropping-9 negative per the developer's
     *                               Sweden read). 0 = neutral/unknown.
     */
    public record JudgmentDerived(
            double talismanResponsibility,
            double bigMatchTemperament,
            double roleFit
    ) {
        public JudgmentDerived {
            if (talismanResponsibility < 0 || talismanResponsibility > 1) {
                throw new IllegalArgumentException("talismanResponsibility must be in [0,1]");
            }
            if (bigMatchTemperament < -1 || bigMatchTemperament > 1) {
                throw new IllegalArgumentException("bigMatchTemperament must be in [-1,1]");
            }
            if (roleFit < -1 || roleFit > 1) {
                throw new IllegalArgumentException("roleFit must be in [-1,1]");
            }
        }

        /** Convenience: a neutral judgment block (no claims made). */
        public static JudgmentDerived neutral() {
            return new JudgmentDerived(0.0, 0.0, 0.0);
        }
    }
}
