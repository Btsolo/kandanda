# Kandanda Intelligence — Document 02
## Tier 2 (Real-Life Moderator) — Intelligence Layer Backlog

Status: captured during Sprint 1. NOT yet built. These are the "Football Manager
hidden attributes" features. They are all **Tier 2 moderators**: they adjust the
Tier 1 (Dixon-Coles) base-rate strength numbers. None is built until the base rate
exists and is validated, and each must prove itself on the Scoreboard before
acceptance (Definition of Done, anti-Klement rule).

The golden rule for every item below: **it is a hypothesis until calibration on
held-out games says otherwise.** "Player X bottles big games" is a story you can
tell about anyone after a bad match. The only proof is: does encoding it improve
out-of-sample calibration? If not, it is commentary, not a feature.

---

### S13 — Manager risk-profile
Model each manager on a cautious↔gambler axis from observable behaviour:
- in-game decisions (does the team protect a 1-0 lead or push for the second?)
- substitution timing and type (defensive shore-up vs attacking gamble)
- selection (extra defender vs extra forward in big games)
Feeds a small adjustment to a team's attack/defence split, and to in-play
variance (gamblers widen the outcome distribution — more likely to win OR lose).

### S14 — Substitution impact tracking
Record each substitution and the game-state swing after it (xG/goals before vs
after). Over many matches this builds a prior on whether a manager's changes tend
to help. This is a *manager* signal more than a player one, and it is data-hungry
— only feasible if the lineup/event API exposes sub timings.

### S15 — Per-player profile (the core FM-style object)
Each player gets a profile carrying hidden-ish traits, e.g.:
- shooting quality (esp. from distance)
- big-stage temperament: "rises" vs "shrinks" (the Igor Thiago hypothesis —
  players who underperform their baseline in the biggest games, vs players who
  exceed it). Must be measured as deviation from expected, on held-out games, or
  it is just hindsight.
- influence/leadership (the "Messi buff" — see S9, the flagship test case)
These aggregate into a team-strength modifier, weighted by who is actually on the
pitch (so it interacts with availability/injuries, S10).

### S16 — Ball + shooting-profile interaction (tournament-specific)
Observation: the 2026 match ball appears to produce more long-range / unusual
goals. If true, this is a *tournament-level base-rate shift* (more goals from
distance) that interacts with player profiles: a team with strong long-shooters
(S15) under a tactic that permits shooting (S13/tactics) gains more from this ball
than a team that only scores from close range.
- First, test the base-rate claim: are there actually more long-range goals this
  tournament than prior ones? (quantify before believing it — this is exactly the
  kind of "weird goals" narrative that feels true and may not be)
- If confirmed, it becomes a goals-environment modifier, amplified per-team by
  long-shot personnel.

### S17 — Tactics layer (low block, set-piece dependence, pressing)
The structural-football-change factors from the original brief: set-piece
dependence, low-block-vs-better-teams, hydration breaks, rule changes. Each is a
candidate modifier or base-rate shift. Captured here; prioritised after S13–S16.

---

## How these connect (so we don't lose the thread)

```
 Per-player profiles (S15) ──┐
 Manager risk-profile (S13) ─┼──► team attack/defence modifier ──► adjusts Dixon-Coles λ
 Substitution priors (S14) ──┘                                      (Tier 1 base rate)
 Ball/shooting environment (S16) ──► goals-environment modifier ───┘
 Availability/injuries (S10) ──► weights which players' profiles count
 Tactics (S17) ──► shapes attack/defence split and variance
                                   │
                                   ▼
                        every output still judged by
                        the Scoreboard (S1, built)
```

All of it remains Sprint 3+ work. Sprint 1 finishes the base rate first; there is
nothing for these to adjust until then.
