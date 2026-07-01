package com.kandanda.data;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * One played match, reduced to what the goal model needs.
 *
 * <p>Critical modelling decision baked into this entity: we store the
 * <b>90-minute (full-time) score only</b> — {@code homeGoals}/{@code awayGoals}
 * are the goals in normal time. Extra-time and penalty-shootout outcomes are
 * deliberately NOT stored for goal-modelling, because:
 * <ul>
 *   <li>A penalty shootout is a near coin-flip; Opta itself admits shootouts are
 *       not predictable. Letting them into the goal model would teach it noise.</li>
 *   <li>We separate "how many goals the run of play produced" (what Dixon-Coles
 *       models) from "who survived the lottery" (a separate, later concern).</li>
 * </ul>
 *
 * <p>{@code tournament} (e.g. "WC-2022") lets one table hold our whole validation
 * basket — 2022 WC, 2018, Euro/Copa 2024 — and later the live 2026 demo, all queried
 * by tournament.
 *
 * <p>{@code round} (e.g. "Matchday 1", "Final") preserves time-ordering within a
 * tournament, which we need for the point-in-time / no-leakage rule: ratings used to
 * predict a match may only come from earlier rounds.
 */
@Entity
@Table(name = "match_result")
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tournament;

    @Column(nullable = false)
    private String round;

    private LocalDate date;

    @ManyToOne(optional = false)
    @JoinColumn(name = "home_team_id")
    private Team homeTeam;

    @ManyToOne(optional = false)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    /** Goals in normal time (90'). NOT including extra time or penalties. */
    @Column(nullable = false)
    private int homeGoals;

    @Column(nullable = false)
    private int awayGoals;

    /**
     * Expected goals (xG) for each side, if available. Nullable because not every
     * tournament in the basket has xG. When present, xG is a cleaner scoring-rate signal
     * than actual goals (it strips finishing variance), so the model can optionally use it
     * as the Poisson rate instead of goals. Like goals, this is the 90-minute figure with
     * penalty-shootout shots excluded.
     */
    private Double homeXg;
    private Double awayXg;

    protected MatchResult() { }

    public MatchResult(String tournament, String round, LocalDate date,
                       Team homeTeam, Team awayTeam, int homeGoals, int awayGoals) {
        this(tournament, round, date, homeTeam, awayTeam, homeGoals, awayGoals, null, null);
    }

    public MatchResult(String tournament, String round, LocalDate date,
                       Team homeTeam, Team awayTeam, int homeGoals, int awayGoals,
                       Double homeXg, Double awayXg) {
        this.tournament = tournament;
        this.round = round;
        this.date = date;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.homeGoals = homeGoals;
        this.awayGoals = awayGoals;
        this.homeXg = homeXg;
        this.awayXg = awayXg;
    }

    public Long getId() { return id; }
    public String getTournament() { return tournament; }
    public String getRound() { return round; }
    public LocalDate getDate() { return date; }
    public Team getHomeTeam() { return homeTeam; }
    public Team getAwayTeam() { return awayTeam; }
    public int getHomeGoals() { return homeGoals; }
    public int getAwayGoals() { return awayGoals; }
    public Double getHomeXg() { return homeXg; }
    public Double getAwayXg() { return awayXg; }
    public boolean hasXg() { return homeXg != null && awayXg != null; }

    @Override
    public String toString() {
        return String.format("%s: %s %d-%d %s (%s)",
                tournament, homeTeam.getName(), homeGoals, awayGoals,
                awayTeam.getName(), round);
    }
}
