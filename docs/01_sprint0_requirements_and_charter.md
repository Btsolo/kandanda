# Kandanda Intelligence — Document 01
## Sprint 0: Project Charter, Requirements & Methodology

Status: Sprint 0 (inception). 21 June 2026.
Companion to: `00_foundation_autopsy.md` (the research/autopsy phase).

Sprint 0 in Scrum is the "get ready to build" sprint — no shippable feature, but
we define the product, the backlog, the methodology, and the definition of done.
This document is that artifact. Read it as the thing a senior dev would write
before the team touches code, so everyone agrees what we're building and how
we'll know it's working.

---

## 1. Project charter (the one-paragraph "why")

Kandanda Intelligence is a tournament football prediction system that estimates
the probability of every major betting-market outcome (match-level and
tournament-level) for international tournament matches. It is built and validated
on the completed 2022 World Cup, then demonstrated live on 2026. Its purpose is
twofold: (a) a genuinely calibrated predictor that knows how uncertain it is, and
(b) a learning vehicle for the developer to understand probabilistic modelling,
the SDLC, and Java/Spring Boot architecture. Success is measured by *calibration
over many predictions*, never by a single correct call.

---

## 2. Methodology — Agile/Scrum (lightweight, solo-adapted)

### Why Agile and not Waterfall or Spiral
- **Waterfall** assumes requirements are knowable upfront. Ours are not — we do
  not yet know whether features like "player influence multiplier" or
  "corner-based scoreline build-up" actually improve predictions. Requirements
  *emerge from experiments*. Waterfall would force us to commit to a design we
  can't yet justify.
- **Spiral** (risk-driven, prototype-heavy) is a close second and shares Agile's
  iterative DNA, but its heavy per-cycle risk-analysis ceremony is overkill solo.
- **Agile/Scrum** gives short, time-boxed sprints each ending in something
  *demonstrable and validated*. This matches the project's experimental nature
  and the developer's "prototype by a deadline, then improve" working style.

### What we actually run (solo-adapted Scrum / "Scrumban")
We keep Scrum's skeleton and drop the team ceremony:
- **Product backlog** — prioritized list of user stories (Section 5).
- **Sprints** — time-boxed (here, themed by tournament phase, not calendar weeks,
  since 2022 is historical).
- **Definition of Done (DoD)** — a story isn't "done" until it meets the DoD
  (Section 6). This is the discipline that stops us shipping unvalidated features.
- **Sprint review/demo** — each sprint ends by running the new code through the
  scoreboard and writing a short retro note.
We drop: daily standups, story-point poker, separate PO/SM roles (developer wears
all hats). When we perform a formal Scrum activity, the doc will name it so the
vocabulary sticks.

### Tech stack (and why)
- **Java 17 + Spring Boot** — consistency with the developer's existing stack
  (Smart Gazette), dependency injection for clean layering, easy to expose the
  predictor as a REST service later, mature testing story (JUnit) which *matters
  a lot here* because the scoreboard IS a test harness.
- **PostgreSQL** — same as Smart Gazette; stores matches, teams, ratings,
  predictions, and outcomes for the validation layer.
- **Maths in Java** — Dixon-Coles is ~40 lines; the Apache Commons Math library
  gives us Poisson/optimization helpers so we don't hand-roll numerics.
- **Build/test**: Maven, JUnit 5.
- Python is an option for one-off exploratory data analysis, but the *system* is
  Java/Spring Boot so it integrates with the developer's broader portfolio.

---

## 3. Architecture overview (the three-box mental model)

Every component slots into one of three tiers. This is the system's spine.

```
                    ┌─────────────────────────────────────┐
   INPUT DATA  ───► │  TIER 1: BASE RATE (the rational prior)
  (results,         │  Dixon-Coles match model.
   strengths)       │  Team attack/defence  ->  scoreline grid
                    │  -> every match-level market by arithmetic
                    └─────────────────┬───────────────────┘
                                      │ adjusts
                    ┌─────────────────▼───────────────────┐
   INTELLIGENCE ──► │  TIER 2: MODERATOR (the real-life layer)
   (player          │  Player-influence multiplier, chemistry,
    influence,      │  injuries, fatigue, momentum, conditions.
    chemistry,      │  Klement's lesson: non-football data matters.
    injuries)       │  Adjusts Tier 1's strength numbers.
                    └─────────────────┬───────────────────┘
                                      │ produces
                    ┌─────────────────▼───────────────────┐
                    │  TIER 3: DECLARED UNCERTAINTY (luck)
                    │  Tournament = 7-game sample; variance
                    │  dominates. The system reports a width
                    │  on every probability, never a bare point.
                    └─────────────────┬───────────────────┘
                                      │ scored by
                    ┌─────────────────▼───────────────────┐
                    │  THE SCOREBOARD (validation harness)
                    │  Brier, log-loss, vs-market baseline.
                    │  Built FIRST. Judges everything above.
                    └─────────────────────────────────────┘
```

Bottom-up market building (your corners/cards/who-scores-first idea) lives inside
Tier 1/2: small component markets are predicted and *aggregated* up toward the
scoreline conclusion. We start with the scoreline-down version (Dixon-Coles) because
it's proven, then add component-up signals as Tier-2 moderators once the spine works.

---

## 4. Requirements

### Functional requirements (what the system does)
- FR1 — Store teams, matches, and results for a tournament (2022 first).
- FR2 — Maintain per-team attack & defence strength ratings, independent of
  bookmaker odds.
- FR3 — Given a fixture, produce a full scoreline probability grid (Dixon-Coles).
- FR4 — Derive every match-level market from the grid: 1X2, Double Chance, DNB,
  BTTS, Over/Under (all lines), Correct Score, Winning Margin, Odd/Even,
  First/Second-half (where data allows), Asian/European Handicap.
- FR5 — Simulate the tournament bracket via Monte Carlo for tournament-level
  markets: Winner, Finalists, Group Winner, To-Qualify, Stage of Elimination,
  Reach Final/Semi.
- FR6 — Record every prediction with its market-implied counterpart and the
  eventual actual outcome.
- FR7 — Compute calibration metrics (Brier, log-loss) and a vs-market comparison
  over any set of recorded predictions.
- FR8 — Update ratings after each round (the "fine-tune across game weeks" loop).
- FR9 (Tier 2, later) — Apply player-influence / chemistry / availability
  modifiers to strength ratings, toggleable so their effect on calibration is
  measurable.
- FR10 — Output, per match, a ranked list of the most-likely outcomes across
  markets (the "top N" view requested), each with probability and uncertainty.

### Non-functional requirements (qualities)
- NFR1 — *Validatability first*: no predictive feature is accepted without
  scoreboard evidence it improves (or at least doesn't harm) calibration.
- NFR2 — *Explainability*: every probability must be traceable to its inputs
  (which strength numbers, which modifiers). No black boxes — this is a learning
  system.
- NFR3 — *Reproducibility*: fixing the random seed reproduces a Monte Carlo run.
- NFR4 — *Independence*: base ratings derived from match results, NOT bookmaker
  odds, so divergences from the market are meaningful.
- NFR5 — *Honest uncertainty*: every output carries a declared uncertainty; the
  system never emits a bare point estimate for a tournament-level claim.

---

## 5. Product backlog (prioritized)

Priority order is deliberate: the scoreboard precedes the predictor (so we can
never fool ourselves), and the proven maths precedes the speculative intelligence
layer.

| ID | Story | Tier | Priority |
|----|-------|------|----------|
| S1 | As a developer, I can record (prediction, market prob, outcome) and compute Brier/log-loss, so I can judge any model. | Scoreboard | P0 |
| S2 | As a developer, I can load 2022 teams/matches/results into PostgreSQL. | Data | P0 |
| S3 | As a developer, I can fit attack/defence strength ratings from match results. | Tier 1 | P0 |
| S4 | As a developer, I can produce a Dixon-Coles scoreline grid for any fixture. | Tier 1 | P0 |
| S5 | As a developer, I can derive all match-level markets from a grid. | Tier 1 | P0 |
| S6 | As a developer, I can run the S1 scoreboard over all 2022 group matches. | Validation | P0 |
| S7 | As a developer, I can Monte-Carlo the bracket for tournament markets. | Tier 1 | P1 |
| S8 | As a developer, I can re-fit ratings after each round (rolling update). | Tier 1 | P1 |
| S9 | As a developer, I can apply a player-influence modifier and measure its calibration effect (Messi-buff test). | Tier 2 | P2 |
| S10| As a developer, I can add availability/fatigue/chemistry modifiers. | Tier 2 | P2 |
| S11| As a developer, I can emit a per-match ranked "top N outcomes" view. | Output | P2 |
| S12| As a developer, I can point the validated engine at 2026 data (live demo). | Live | P3 |

### Sprint plan (themed, not calendar-bound)
- **Sprint 1 — "The Scoreboard & The Base Rate"**: S1–S6. Exit = a validated
  Dixon-Coles model scored on 2022 group stage. *This is the working prototype.*
- **Sprint 2 — "The Tournament & The Rolling Loop"**: S7, S8. Exit = reproduce an
  Opta-style 2022 winner table and the round-by-round fine-tuning loop.
- **Sprint 3 — "The Real-Life Moderator"**: S9–S11. Exit = the Messi-buff
  experiment, with a clear calibration verdict, plus the top-N output.
- **Sprint 4 — "Live Demo"**: S12. Point the validated engine at 2026.

---

## 6. Definition of Done (applies to every story)

A story is DONE only when:
1. Code compiles, runs, and is committed.
2. It has at least one JUnit test proving the core behaviour.
3. For any *predictive* story, its output has been run through the Scoreboard
   (S1) and the calibration result is recorded — even if the result is "no
   improvement" (a negative result is a valid, kept result).
4. A 2–4 sentence note explains what was built and what the developer learned
   (the academic trail).

This DoD is the single most important discipline in the project. It is what
separates us from Klement: we never accept a feature on the strength of a story,
only on the strength of measured calibration.

---

## 7. Sprint 1 kickoff — what happens next

First story is **S1, the Scoreboard**, not the predictor. We build the judge
before the contestant. Concretely: a small Spring Boot module with a
`Prediction` entity (predicted prob, market-implied prob, actual outcome) and a
`CalibrationService` computing Brier score and log-loss, with JUnit tests using
hand-checked numbers so we *know* the metric is correct before we trust it on
real models.

Then S2 (load 2022 data), then S3–S5 (the Dixon-Coles base rate), then S6 (run
the scoreboard over 2022 and read our first real calibration number).

Open the next message and we start writing S1.
