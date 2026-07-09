package com.kandanda.profile;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * S23 (EXPERIMENTAL): collapses a squad's per-player {@link PlayerProfile.TacticalDerived}
 * vectors into team-level tactical indices, then into a small attack-lambda multiplier.
 *
 * <h2>Why importance-weighted, not a flat mean</h2>
 * A 26-man squad is not a starting XI. A flat average lets bench players dilute the stars,
 * so a team's signal collapses toward the middle. Instead each player's tactical
 * contribution is weighted by his {@code talismanResponsibility} (read as team-function
 * load — the developer's Nuno-Mendes rule: importance, not position). Stars move the team
 * vector; fringe players barely do.
 *
 * <h2>What it produces</h2>
 * An {@code attackIndex} (progression + chanceCreation + boxThreat + transition — the chain
 * that manufactures goals) and a {@code solidityIndex} (defensiveControl + possessionSecurity).
 * A team's attack multiplier nudges its lambda up when its own attackIndex beats the field
 * mean and down when the opponent's solidity beats the field mean — both scaled by a small
 * weight and clamped. This is a CANDIDATE signal: shown beside the baseline, never scored,
 * until the judge evaluates it post-tournament next to chemistry.
 */
public final class TacticalAggregate {

    private TacticalAggregate() { }

    public record TeamIndices(double attackIndex, double solidityIndex) { }

    /** Importance-weighted team indices for every team present in the roster. */
    public static Map<String, TeamIndices> compute(List<PlayerProfile> roster) {
        Map<String, double[]> sum = new HashMap<>();   // [wAttack, wSolidity, W]
        for (PlayerProfile p : roster) {
            var t = p.tactical();
            double w = p.judgment().talismanResponsibility();
            if (w <= 0) w = 0.05; // floor so zero-importance players still count faintly
            double attack = (t.progressionValue() + t.chanceCreationValue()
                    + t.boxThreat() + t.transitionValue()) / 4.0;
            double solidity = (t.defensiveControl() + t.possessionSecurity()) / 2.0;
            double[] acc = sum.computeIfAbsent(p.team(), k -> new double[3]);
            acc[0] += w * attack;
            acc[1] += w * solidity;
            acc[2] += w;
        }
        Map<String, TeamIndices> out = new HashMap<>();
        for (var e : sum.entrySet()) {
            double[] a = e.getValue();
            out.put(e.getKey(), new TeamIndices(a[0] / a[2], a[1] / a[2]));
        }
        return out;
    }

    /** Field-mean of the attack indices (the reference each team deviates from). */
    public static double meanAttack(Map<String, TeamIndices> idx) {
        return idx.values().stream().mapToDouble(TeamIndices::attackIndex).average().orElse(0);
    }

    public static double meanSolidity(Map<String, TeamIndices> idx) {
        return idx.values().stream().mapToDouble(TeamIndices::solidityIndex).average().orElse(0);
    }

    /**
     * Attack-lambda multiplier for {@code team} facing {@code opponent}.
     * <pre>mult = 1 + weight * [ (ownAttack − meanAttack) − (oppSolidity − meanSolidity) ]</pre>
     * Own attacking quality lifts lambda; opponent's defensive solidity suppresses it.
     * Clamped to [0.75, 1.25] — a nudge, never a takeover. weight=0 → exactly 1.
     */
    public static double attackMultiplier(String team, String opponent,
                                          Map<String, TeamIndices> idx, double weight) {
        TeamIndices me = idx.get(team), opp = idx.get(opponent);
        if (me == null || opp == null) return 1.0;
        double dAtk = me.attackIndex() - meanAttack(idx);
        double dSol = opp.solidityIndex() - meanSolidity(idx);
        double mult = 1.0 + weight * (dAtk - dSol);
        return Math.max(0.75, Math.min(1.25, mult));
    }
}