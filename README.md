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

### S14-data (xG as Poisson input) — DONE
2022 per-match xG sourced from StatsBomb Open Data (comp 43, season 106; penalty-
shootout shots excluded). `MatchResult` gained nullable xG; `RatingService(k, useXg)`
fits on xG or goals; `BacktestService.compareXg` scores both.
- Result: xG is a REAL but INCREMENTAL gain — beats goals at low prior (0.2387 vs
  0.2401 at k=5), edge shrinks at high prior (shrinkage already tames variance).
  Kept, not oversold. Bigger value is per-game truth on high-variance games
  (ARG dominated KSA on xG yet lost) — matters for the residual analyzer.
- Also fixed: .gitignore was too broad (`data/` ignored the resource JSON); now `/data/`.

### BUGFIX: robust group/knockout split
The split was hard-coded to Matchday 1/2/3, which silently mis-split StatsBomb data
(labelled Matchday 1..13) into 8 group + 56 knockout instead of 48 + 16. This flattered
the xG result. Fixed: `BacktestService.isGroupStage` = "starts with Matchday". Guarded
by tests. Honest re-validation: xG is roughly a WASH with goals as a raw rating input
(marginally better at k=5, marginally worse at k=8-12). xG's value is INFRASTRUCTURE for
the residual analyzer, not a standalone rating win.

### S14 (residual analyzer — the judge) — DONE
`com.kandanda.analysis`: the instrument that separates skill from luck.
- `ResidualAnalyzer` fits xG-based ratings, then per team computes:
  CREATION residual (xG - expected xG) = skill, tends to persist;
  FINISHING residual (goals - xG) = luck, regresses to mean.
- `TeamResidual` classifies teams (underrated / lucky / creating).
- 2022 validation (real data): Germany top creator but finished cold (went out unlucky);
  Spain biggest over-finisher on thin creation (regressed, out in R16); Morocco
  over-finished AND under-created (run outran underlying numbers). The judge tells true
  stories goals hide.
- This is the referee every Tier 2 hypothesis must answer to.

### S15 (profile schema) — DONE
`com.kandanda.profile`: the home for the football knowledge, reverse-engineered from
Football Manager's structure (doc 04 §4b) rather than invented.
- `PlayerProfile` physically separates the two provenances as nested types:
  `DataDerived` (measured, per-90, nullable — fill what sources provide) and
  `JudgmentDerived` (talismanResponsibility, bigMatchTemperament, roleFit — the
  developer's reads; FM's "hidden attributes"). You cannot construct a profile without
  declaring which claims are measured and which are judged.
- Key convergence: FM's "Important Matches" = bigMatchTemperament; FM's "Consistency" =
  what the S14 residual analyzer MEASURES — the analyzer is an instrument for estimating
  FM-style hidden attributes from real data.
- `TeamProfile`: starDependence spectrum (H2, system↔star), chemistry (H4),
  eliteWideQuality (H1 low-block breaker), talisman link.
- `ManagerProfile`: risk axis only (minimal slice; tactical identity deferred).
- All judged values range-checked; neutral() factories make "no claims" the default.
- Every profile value is a HYPOTHESIS until a modifier using it passes the judge.

### S16 (talisman-absence effect, SIGNED) — DONE
First judgment-derived attribute before the judge — and the judge's first real verdict:
- NAIVE hypothesis ("talisman absent -> team weaker") REJECTED: penalising Portugal for
  Ronaldo's benching made knockout Brier monotonically worse (he was benched R16 + QF;
  Portugal won 6-1 without him).
- SIGNED version (H3): effect = 1 - w x starDependence x roleFit, clamped [0.5,1.5].
  Misfit star (roleFit<0) -> absence HELPS. Level-to-marginally-better on 2022 —
  consistent with H3. Only 2 affected matches; 2026 lineups give the real test.
- New: `BacktestService.LambdaAdjuster` per-match hook (the entry point 2026 live
  lineups will use), `TalismanAbsence`, `TalismanEffect`, `Profiles2022` seed reads
  (assistant-proposed, developer to sanity-check).

### S17 (validation basket: WC-2018) — DONE, and the biggest finding so far
2018 loaded via the same StatsBomb-xG pipeline (48/16 split handled by the robust
isGroupStage — the S14 bugfix paying off).
- FORM FAILED REPLICATION: on 2022 form improved to a sweet spot (0.2378 -> 0.2298 at
  w=0.75); on 2018 it gets monotonically WORSE at every weight (0.2364 -> 0.2727 at
  w=1.0). The 2022 fingerprint was one tournament fooling us. Group form -> knockout
  carryover is NOT a stable cross-tournament signal.
- FormModifier stays as infrastructure but is NOT part of the trusted model.
- This is the instrument working as designed: a promising signal died honestly on
  out-of-sample data instead of shipping. (Klement lesson enforced by machinery.)

### Next: more basket tournaments (Euros/Copa) to grow the test bed; residuals on 2018;
2026 live forward test with locked pre-match predictions.

### S18 (2026 LIVE forward test) — LOCKED
72 group games loaded (WC-2026, with xG). Trusted model only (goals, k=8, DC -0.1)
fitted on the group stage; predictions for the 9 unplayed fixtures (3 R32 + 6 R16)
printed by the runner and LOCKED by the git commit timestamp, pre-kickoff 2026-07-03.
Rules: no SportRadar comparison until after results are scored (no mimicry); no model
changes between lock and scoring. The gold-standard test no backtest can match.

### S19 (live scoring) — RUNNING, updated 06 Jul
7 knockout results in (R32: ARG 1-1 CPV, AUS 1-1 EGY, COL 1-0 GHA; R16: CAN 0-3 MAR,
PAR 0-1 FRA, BRA 1-2 NOR, MEX 2-3 ENG). LIVE Brier 0.2295 over 63 predictions —
beats coin (0.25) and the 0.232-0.238 backtest band. Locked additions: SUI-COL,
ARG-EGY (Jul 7), QFs FRA-MAR (Jul 9), NOR-ENG (Jul 11). Profiles2026 expanded to all
12 remaining teams incl. standouts (Trossard, Bellingham, Ounahi...). Standout vs
talisman distinction noted for S20 (data-derived standout + auto per-player stats).

### Ops mode (07 Jul): SHOW_HISTORY flag
S2-S17 validation trail gated behind SHOW_HISTORY=false (flip true anytime; nothing
deleted — findings live in README/docs/git). S18 now prints ONLY pending locks (played
fixtures drop out automatically); S19 prints the per-game live scoreboard + total.
Spain 1-0 Portugal added (Merino 90+1; xG 1.77-0.60 — Ronaldo's last WC). USA-Belgium
pending; its QF (Spain vs winner, Jul 10) locks once known.