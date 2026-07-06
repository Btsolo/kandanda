package com.kandanda.profile;

import java.util.List;

/**
 * 2026 profile reads — expanded to ALL 12 teams still alive (post-R16, 06 Jul), each with
 * its structural talisman (if any) plus current-tournament STANDOUTS. PROPOSED by the
 * assistant for the developer to sanity-check; every value is a judgment hypothesis.
 *
 * <p>Design note (per the developer's Trossard point): "standout of THIS tournament"
 * (largely data-derived: goals/xG/xA in these five games) is a DIFFERENT attribute from
 * "talisman" (structural: the team is built around him). Standouts are therefore extra
 * PlayerProfiles here; the team-level standout link + auto-populated per-player data
 * block is the next schema story (S20).
 */
public final class Profiles2026 {

    private Profiles2026() { }

    public static List<TeamProfile> teams() {
        return List.of(
                // team, starDependence, chemistry, eliteWideQuality, talisman
                new TeamProfile("Argentina", 0.85, 0.80, 0.50, "Lionel Messi"),
                new TeamProfile("France", 0.30, 0.40, 0.90, "Kylian Mbappé"),
                new TeamProfile("England", 0.60, 0.30, 0.50, "Harry Kane"),
                new TeamProfile("Spain", 0.50, 0.50, 0.90, "Lamine Yamal"),
                new TeamProfile("Portugal", 0.65, 0.10, 0.40, "Cristiano Ronaldo"),
                new TeamProfile("Norway", 0.85, 0.50, 0.30, "Erling Haaland"),
                new TeamProfile("Belgium", 0.45, 0.30, 0.50, "Kevin De Bruyne"),
                new TeamProfile("USA", 0.40, 0.60, 0.50, "Christian Pulisic"),
                new TeamProfile("Colombia", 0.55, 0.60, 0.60, "Luis Díaz"),
                new TeamProfile("Egypt", 0.70, 0.60, 0.40, "Mohamed Salah"),
                new TeamProfile("Morocco", 0.25, 0.70, 0.40, null),   // system team
                new TeamProfile("Switzerland", 0.30, 0.55, 0.30, null) // system team
        );
    }

    public static List<PlayerProfile> players() {
        return List.of(
                // Talismans
                p("Lionel Messi", "Argentina", ATT, 0.95, 0.80, 0.90),
                p("Kylian Mbappé", "France", ATT, 0.60, 0.70, 0.80),
                p("Harry Kane", "England", ATT, 0.75, 0.20, 0.40),
                p("Lamine Yamal", "Spain", ATT, 0.70, 0.60, 0.90),
                p("Cristiano Ronaldo", "Portugal", ATT, 0.90, 0.30, -0.40), // H3 misfit
                p("Erling Haaland", "Norway", ATT, 0.90, 0.60, 0.80), // 2 vs BRA: fit up
                p("Kevin De Bruyne", "Belgium", MID, 0.60, 0.40, 0.30),
                p("Christian Pulisic", "USA", ATT, 0.55, 0.50, 0.60),
                p("Luis Díaz", "Colombia", ATT, 0.70, 0.50, 0.70),
                p("Mohamed Salah", "Egypt", ATT, 0.85, 0.50, 0.60),
                // Current-tournament standouts (developer's Trossard point)
                p("Leandro Trossard", "Belgium", ATT, 0.40, 0.50, 0.80), // BEL's best this WC
                p("Jude Bellingham", "England", MID, 0.60, 0.80, 0.90),  // H5; MEX brace
                p("Ousmane Dembélé", "France", ATT, 0.40, 0.50, 0.80),
                p("Azzedine Ounahi", "Morocco", MID, 0.40, 0.60, 0.80),  // CAN masterclass
                p("Folarin Balogun", "USA", ATT, 0.45, 0.40, 0.60),
                p("Jhon Arias", "Colombia", MID, 0.40, 0.50, 0.70),
                p("Johan Manzambi", "Switzerland", MID, 0.35, 0.50, 0.70),
                p("Emam Ashour", "Egypt", MID, 0.35, 0.50, 0.60),
                p("Vinícius Júnior", "Brazil", ATT, 0.70, 0.50, 0.70) // kept for record (eliminated)
        );
    }

    private static final PlayerProfile.PositionGroup ATT = PlayerProfile.PositionGroup.ATT;
    private static final PlayerProfile.PositionGroup MID = PlayerProfile.PositionGroup.MID;

    private static PlayerProfile p(String name, String team, PlayerProfile.PositionGroup pos,
                                   double resp, double bigMatch, double roleFit) {
        return new PlayerProfile(name, team, pos,
                PlayerProfile.DataDerived.none(),
                new PlayerProfile.JudgmentDerived(resp, bigMatch, roleFit));
    }
}