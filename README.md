# Kandanda Intelligence

Tournament football prediction system. Validated on 2022 World Cup (+ recent UCLs,
Euro/Copa 2024), demoed live on 2026. Built as a learning project under lightweight
Agile/Scrum. See `docs/` for the foundation autopsy, Sprint 0 charter, and Tier-2 backlog.

## Stack
Java 17, Spring Boot 3.3, Spring Data JPA, H2 (file-based; swappable to PostgreSQL
via config only), Jackson, JUnit 5.

## Status
### Sprint 1, Story S1 (The Scoreboard) — DONE
The validation harness, built before any predictor. `com.kandanda.scoreboard`:
Brier score, log-loss, beat-the-market check. 8 tests, all green.

### Sprint 1, Story S2 (Load 2022 data) — DONE
`com.kandanda.data`: loads the 2022 World Cup into the DB.
- `Team`, `MatchResult` — minimal entities (extract-don't-hoard: only teams + 90-min score)
- `TeamRepository`, `MatchResultRepository` — Spring Data, no hand-written SQL
- `TournamentLoader` — parses openfootball JSON, reads score.ft ONLY (penalties excluded)
- data file: `src/main/resources/data/worldcup-2022.json` (64 matches, public domain)

## Run it
```
mvn spring-boot:run
```
On startup it loads the 2022 data and prints "Total WC-2022 in DB: 64 matches".
Re-running is safe (idempotent — it won't double-load).

Inspect the data in your browser at http://localhost:8080/h2-console
(JDBC URL: jdbc:h2:file:./data/kandanda, user: sa, no password).

## Run the tests
```
mvn test
```

## The three-box architecture
1. Base rate (Dixon-Coles) — the rational prior. (NEXT: S3-S5)
2. Moderator (player influence, chemistry, injuries) — Tier 2. (Sprint 3, see docs/02)
3. Declared uncertainty (luck) — every output reports its width.
All judged by the Scoreboard (S1, done).

## Modelling decisions baked in so far
- Penalty shootouts excluded from goal data (they are noise; Opta agrees).
- Matches stored with round + date for point-in-time / no-leakage validation.
- One match_result table holds all tournaments (query by tournament tag).

### Sprint 1, Story S3 (Strength ratings) — DONE
`com.kandanda.rating`: fits attack & defence ratings from results.
- `TeamRating` — attack (>1 = scores more) and defence (<1 = concedes less; lower is better)
- `RatingService` — iterative Maher-style fit; no home advantage (neutral WC venues)
- Known, expected weakness: small-sample noise (one shock result inflates a rating).
  The raw un-regularised version is intentional; a pre-tournament prior is a planned
  later improvement, added only if the Scoreboard shows it helps.
- On startup the app now also prints the top attacks/defences for 2022.

### Sprint 1, Story S4 (Poisson scoreline model) — DONE
`com.kandanda.model`: turns two ratings into a probability for every scoreline.
- `PoissonMatchModel` — expected goals = attack x opp_defence x leagueAvg, then Poisson
- `ScorelineGrid` — the [11x11] probability grid; every betting market derives from it (S5)
- Plain independent-Poisson first (verified vs textbook + symmetry); Dixon-Coles
  correction is the next refinement, added only if it improves calibration.
- Poisson computed in log-space for numerical stability.
- On startup the app now predicts the 2022 final from fitted ratings and prints
  win/draw/loss + top scorelines. (Model leans France; actual was 2-2 then pens to
  Argentina — a clean illustration of single-game variance.)

### Sprint 1, Story S5 (Betting markets) — DONE
`com.kandanda.model.MarketCalculator`: every market derived from the grid.
- Individual methods: homeWin/draw/awayWin, doubleChance*, drawNoBet*, over/under(line),
  bttsYes/No, correctScore(h,a), homeWinByExactly/awayWinByExactly, odd/evenTotalGoals
- One shared private sum(condition) engine (BiPredicate) under all of them — no duplication
- headlineMarkets() bundle for scoring many at once (S6)
- Verified: 1X2, O/U, BTTS, odd/even each partition to 1.0

### Sprint 1, Story S6 (Backtest experiment) — DONE
`com.kandanda.experiment`: the train/test backtest. SPRINT 1 COMPLETE.
- `BacktestService` — splits group (train) vs knockout (test), fits both models, scores
- `MatchOutcomes` — actual market outcomes from the 90-minute score (penalties excluded)
- Group-only model = FAIR (never saw knockouts). All-data model = LEAKAGE (don't trust).
- Scores EVERY market on each knockout game for more signal from 16 matches.

KEY FINDING (2022): the fair group-only model scores Brier ~0.31 on knockouts — WORSE
than the 0.25 coin-flip baseline. This is a true finding about tournament football
(knockouts are variance-dominated) and sets the bar every future improvement must beat.
The all-data number (~0.24) looks good only because of leakage.

## Sprint 2 — beating the 0.31 base rate

### S7 (Pre-tournament prior / additive smoothing) — DONE
`RatingService(k)`: adds k "phantom average games" per team, shrinking noisy ratings
toward 1.0. Matters more for teams with fewer games (exactly when needed).
- `BacktestService.sweepPrior(...)` sweeps k and scores each on knockouts.
- 2022 finding: k=0 -> Brier 0.31 (worse than coin); raising k pushes BELOW the 0.25
  baseline, bottoming ~0.245 at large k. This means group-stage ratings carry little
  knockout signal, so shrinking toward average helps. A finding, not a victory — real
  signal must come from richer data (Tier 2) later, measured against this bar.

### S8 (Dixon-Coles low-score correction) — DONE
`PoissonMatchModel(leagueAvg, rho)`: nudges the four low-score cells (0-0,1-1 up;
1-0,0-1 down) toward how real football behaves, then renormalises to sum to 1.
rho=0 reproduces pure Poisson exactly (backward compatible).
- 2022: at prior k=5, rho=-0.1 improved knockout Brier ~0.2512 -> ~0.2498. Real,
  consistent, but marginal at 16 games. Canonical and kept; not oversold.

### Tier 2 (real-life moderator layer) — STARTED

Architecture: base ratings flow through a stack of toggleable `RatingModifier`s
(`tier2` package) before the Poisson model. Each modifier is identity at weight 0, uses
only group-stage data (no leakage), and is judged by the backtest.

#### S13 (team-form modifier) — DONE
`FormModifier`: adjusts attack by group over/under-performance vs own xG.
- 2022 (prior k=12, DC rho=-0.1): base Brier ~0.247 -> best ~0.242 at weight ~0.5,
  WORSE past that. A sweet spot is the fingerprint of a real but PARTIAL signal, not
  overfit noise. Single tournament only — needs the validation basket to confirm.

#### Next: validation basket (2018/UCL/Euro-Copa) to test if form generalises, then
more modifiers (manager risk, standout players) on the same framework.