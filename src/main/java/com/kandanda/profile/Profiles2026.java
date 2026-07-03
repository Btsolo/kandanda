package com.kandanda.profile;

import java.util.List;

/**
 * Seed 2026 profile reads for the fixture-relevant teams — PROPOSED by the assistant
 * for the developer to sanity-check, exactly like {@link Profiles2022}. Used ONLY by
 * the EXPERIMENTAL lineup-adjusted view in the runner (never the locked baseline).
 *
 * <p>Notes: Messi (6 group goals, record) still the archetypal fitted talisman;
 * Ronaldo kept at negative roleFit per H3 unless the developer overrides ("why is
 * Ronaldo still starting?" remains the 2026 discourse); France low-dependence (depth,
 * H2 — Mbappé AND Dembélé both on 4 goals); Haaland high-dependence for Norway;
 * Vinícius the Brazil talisman this cycle; Morocco/Canada system-leaning.
 */
public final class Profiles2026 {

    private Profiles2026() { }

    public static List<TeamProfile> teams() {
        return List.of(
                new TeamProfile("Argentina", 0.85, 0.80, 0.50, "Lionel Messi"),
                new TeamProfile("Portugal", 0.65, 0.10, 0.40, "Cristiano Ronaldo"),
                new TeamProfile("France", 0.30, 0.40, 0.90, "Kylian Mbappé"),
                new TeamProfile("England", 0.60, 0.30, 0.50, "Harry Kane"),
                new TeamProfile("Brazil", 0.60, 0.40, 0.80, "Vinícius Júnior"),
                new TeamProfile("Norway", 0.80, 0.50, 0.30, "Erling Haaland"),
                new TeamProfile("Spain", 0.50, 0.50, 0.90, "Lamine Yamal"),
                new TeamProfile("Morocco", 0.25, 0.70, 0.40, null),
                new TeamProfile("Canada", 0.30, 0.60, 0.30, null)
        );
    }

    public static List<PlayerProfile> players() {
        return List.of(
                p("Lionel Messi", "Argentina", 0.95, 0.80, 0.90),
                p("Cristiano Ronaldo", "Portugal", 0.90, 0.30, -0.40),
                p("Kylian Mbappé", "France", 0.60, 0.70, 0.80),
                p("Harry Kane", "England", 0.75, 0.20, 0.40),
                p("Vinícius Júnior", "Brazil", 0.70, 0.50, 0.70),
                p("Erling Haaland", "Norway", 0.90, 0.50, 0.70),
                p("Lamine Yamal", "Spain", 0.70, 0.60, 0.90)
        );
    }

    private static PlayerProfile p(String name, String team,
                                   double resp, double bigMatch, double roleFit) {
        return new PlayerProfile(name, team, PlayerProfile.PositionGroup.ATT,
                PlayerProfile.DataDerived.none(),
                new PlayerProfile.JudgmentDerived(resp, bigMatch, roleFit));
    }
}