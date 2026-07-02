package com.kandanda.profile;

/**
 * A team profile — the team-level attributes the transcripts and the developer's reads
 * identified as mattering (doc 04, H1/H2/H4). All values here are JUDGMENT-derived
 * hypotheses until validated; data-derived team signals (form, residuals) already live
 * in the model/analysis layers, so this record deliberately holds only the judged side.
 *
 * @param team           team name (must match the dataset's team naming)
 * @param starDependence 0..1 spectrum (H2): 0 = pure system team (Japan, Netherlands,
 *                       Spain-2022 — relies on the game plan), 1 = pure star team
 *                       (lives/dies by the talisman). France's depth = low dependence
 *                       DESPITE having stars. Sets how much player profiles even matter
 *                       for this team: a talisman modifier scales with this.
 * @param chemistry      -1..+1 (H4): squad cohesion beyond ratings. Argentina-2022's
 *                       everyone-parks-ambition-for-Messi = strongly positive. 0 = unknown.
 * @param eliteWideQuality 0..1 (H1): elite wide 1v1 ability that breaks low blocks.
 *                       Per the low-block-meta transcript, only France/Spain-tier wide
 *                       players unlock deep defences; teams without it get low-blocked.
 *                       Interacts with opponent style — a modifier input, not a rating.
 * @param talisman       name of the talisman player if there is one (links to a
 *                       {@link PlayerProfile}); null for system teams
 */
public record TeamProfile(
        String team,
        double starDependence,
        double chemistry,
        double eliteWideQuality,
        String talisman
) {
    public TeamProfile {
        if (starDependence < 0 || starDependence > 1) {
            throw new IllegalArgumentException("starDependence must be in [0,1]");
        }
        if (chemistry < -1 || chemistry > 1) {
            throw new IllegalArgumentException("chemistry must be in [-1,1]");
        }
        if (eliteWideQuality < 0 || eliteWideQuality > 1) {
            throw new IllegalArgumentException("eliteWideQuality must be in [0,1]");
        }
    }

    /** A neutral profile for a team we have no reads on (no claims made). */
    public static TeamProfile neutral(String team) {
        return new TeamProfile(team, 0.0, 0.0, 0.0, null);
    }
}
