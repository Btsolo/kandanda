package com.kandanda.profile;

import com.kandanda.tier2.TalismanAbsence;

import java.util.List;
import java.util.Set;

/**
 * Seed profile reads for the 2022 World Cup — PROPOSED by the assistant from the analyst
 * transcripts (doc 04) and football consensus, for the DEVELOPER TO SANITY-CHECK and
 * adjust. Every value is a judgment-derived hypothesis; none is trusted until a modifier
 * using it passes the judge.
 *
 * <p>Reasoning per read (adjust freely):
 * <ul>
 *   <li><b>Argentina/Messi</b>: archetypal star team (H4 chemistry transcript — squad
 *       parks ambition for him); Scaloni's system built to fit him → roleFit high.</li>
 *   <li><b>Portugal/Ronaldo</b>: star-dependent by identity, but H3 says Martinez built
 *       for the Ronaldo of 10 years ago → roleFit NEGATIVE.</li>
 *   <li><b>France/Mbappé</b>: LOW dependence despite superstars — depth beats
 *       individuals (H2); system fits Mbappé well.</li>
 *   <li><b>England/Kane</b>: talisman blame-load (H9) but a system under Southgate →
 *       moderate dependence, decent fit.</li>
 *   <li><b>Brazil/Neymar</b>: built around Neymar; fit good when present.</li>
 *   <li><b>Croatia/Modrić</b>: midfield talisman, system flows through him.</li>
 *   <li><b>Japan / Morocco</b>: system teams (H2) — low dependence, no talisman set.</li>
 * </ul>
 */
public final class Profiles2022 {

    private Profiles2022() { }

    public static List<TeamProfile> teams() {
        return List.of(
                new TeamProfile("Argentina", 0.85, 0.80, 0.50, "Lionel Messi"),
                new TeamProfile("Portugal", 0.70, 0.10, 0.40, "Cristiano Ronaldo"),
                new TeamProfile("France", 0.35, 0.40, 0.90, "Kylian Mbappé"),
                new TeamProfile("England", 0.60, 0.30, 0.50, "Harry Kane"),
                new TeamProfile("Brazil", 0.70, 0.40, 0.70, "Neymar"),
                new TeamProfile("Croatia", 0.65, 0.60, 0.20, "Luka Modrić"),
                new TeamProfile("Japan", 0.15, 0.50, 0.20, null),
                new TeamProfile("Morocco", 0.25, 0.70, 0.40, null)
        );
    }

    public static List<PlayerProfile> players() {
        return List.of(
                player("Lionel Messi", "Argentina", 0.95, 0.80, 0.90),
                player("Cristiano Ronaldo", "Portugal", 0.90, 0.30, -0.40), // H3: misfit
                player("Kylian Mbappé", "France", 0.60, 0.70, 0.80),
                player("Harry Kane", "England", 0.75, 0.20, 0.40),
                player("Neymar", "Brazil", 0.85, 0.40, 0.60),
                new PlayerProfile("Luka Modrić", "Croatia", PlayerProfile.PositionGroup.MID,
                        PlayerProfile.DataDerived.none(),
                        new PlayerProfile.JudgmentDerived(0.80, 0.60, 0.70))
        );
    }

    /** 2022 knockout talisman absences (lineup facts, known pre-kickoff). */
    public static Set<TalismanAbsence> knockoutAbsences() {
        return Set.of(
                new TalismanAbsence("Portugal", "Round of 16"),   // Ronaldo benched; 6-1 W
                new TalismanAbsence("Portugal", "Quarter-finals") // Ronaldo benched; 0-1 L
        );
    }

    private static PlayerProfile player(String name, String team,
                                        double responsibility, double bigMatch, double roleFit) {
        return new PlayerProfile(name, team, PlayerProfile.PositionGroup.ATT,
                PlayerProfile.DataDerived.none(),
                new PlayerProfile.JudgmentDerived(responsibility, bigMatch, roleFit));
    }
}