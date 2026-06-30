# Kandanda Intelligence — Document 03
## The Research Instrument: three-phase model, the luck/residual judge, and the profile layer

Status: Sprint 3 design. Written after the team-form result (S13) and the developer's
"separate luck from signal" reframing. This supersedes the loose Tier 2 backlog in
doc 02 by giving it a spine.

---

## 1. What we are actually building (the reframe)

We are NOT building the live prediction app yet. We are building a **research
instrument** whose purpose is to discover **which signals are real** — so that the
later live app ships only validated signals and known error-corrections.

The instrument exists because tournaments are luck-soaked and small-data. That is a
feature, not a bug: if a signal survives the noise of a knockout tournament, it is
probably real. We use the luck to *filter*.

### The three phases of a match

| Phase | What it is | Live app can use it? |
|-------|-----------|----------------------|
| BEFORE | Knowable pre-kickoff: base rate + lineup + profile/tactic/chemistry adjustments | YES |
| DURING | Run-of-play events: early goal, red card, tactical shift, a player misused | NO (not live, for now) |
| AFTER | The explanation: why the residual happened (sub, a player rising, wrong tactics) | NO (research-only) |

The live app, for now, works **solo** on BEFORE data only — the last input it gets is
the **lineup**, from which it infers likely tactics. DURING/AFTER are research-only,
used to *learn* which BEFORE-signals matter. (Live in-match data is a possible far-future
extension, explicitly deferred.)

### The engine connecting the phases — the residual

```
   residual = (what actually happened)  −  (what the BEFORE model expected)
```

A residual is where luck OR hidden signal lives. The instrument's core job:
**mine residuals for patterns that survive luck.** Pure luck is random — big one game,
gone the next. A residual that PERSISTS across a team's matches is not luck; it is a real
effect the BEFORE model is missing. That persistence test is the whole idea.

The team-form result (S13) already hinted this works: "did the team out-score its xG in
the group?" is a residual, and weighting it improved knockout calibration (0.2393 ->
0.2320). Form helped *because consistent over-performance is signal, not luck.*

---

## 1b. The governing principle: math and intelligence balance each other (yin & yang)

The intelligence layer (profiles, talisman, chemistry, tactics) and the mathematics
layer (base rate: ratings, Poisson, Dixon-Coles) are **two halves that correct each
other's blindness.** Neither rules.

- **Math without intelligence** is cold and context-blind: it can't see that Argentina is
  built around Messi, or that France's depth beats individuals; it reads 7-0 vs Costa Rica
  as pure quality. The profiles fill that gap.
- **Intelligence without math** is worse — pure narrative, the storytelling trap,
  confident hindsight. Untethered profiles "explain" anything. The math anchors them: a
  football read must actually move the measured expected goals, or it is just vibes.

**Consequences for every build:**
1. Every profile modifier is a **bounded ADJUSTMENT to the math, never a replacement.** A
   talisman modifier nudges the base rating by a capped amount; it can lean the result,
   never seize it. (The `RatingModifier` shape — base in, adjusted out, identity at zero —
   exists to enforce exactly this. It is the philosophical core, not just convenience.)
2. The **residual analyzer is the referee between the two halves.** When the math and the
   intelligence layer disagree, the residual — measured over many games — decides who was
   right. Disputes are settled by evidence, not argument.
3. The final prediction is always (base rate) adjusted by (bounded intelligence nudges),
   with the balance itself tunable and tested. Neither half is ever allowed to dominate
   by construction.

---

## 2. The judge must come before the profiles

A player/manager profile is only useful if we can test whether it is true. Without a
judge, "Messi buffs Argentina" is a story we cannot distinguish from hindsight. So the
build order is judge-first:

1. **Residual analyzer** (the scale / taste-test). For each team, record per-match
   residuals from the BEFORE model, and measure: are they CONSISTENT (signal) or
   SCATTERED (luck)? Output: a per-team "how much of this team's over/under-performance
   looks real vs lucky" number. This is also the **bias-correction** tool the developer
   described: if the model is wrong in the same direction repeatedly (residual ~0.5 every
   time), subtract that systematic error.

2. **Profile data structures** (designed now, populated next). Where the football
   knowledge lives. See section 3. We design them alongside the judge so the ideas have a
   home, but we only TRUST a profile once it passes the judge.

3. **Profile modifiers** (each a `RatingModifier`, judged on the Scoreboard + residual
   analyzer). Talisman, squad-depth, chemistry — each a hypothesis, kept only if it
   reduces residual / improves calibration across games and (later) across tournaments.

This is the same discipline as the whole project: **your football eye proposes, the math
instrument disposes.**

---

## 3. The profile layer (where the football knowledge goes)

These are FM/Opta/Brighton-inspired. A profile is NOT "how good is the player" — it is a
set of ROLE and INFLUENCE attributes that modify the TEAM, and that INTERACT.

### Player profile (attributes, not a single rating)
- **Talisman / tournament-star responsibility** — the team is built around this player;
  his presence/absence swings the team more than his raw rating (Kane, Messi, James
  Rodríguez, Ronaldo). High responsibility also means high *blame load* — Kane must do
  more or the team (and he) gets blamed.
- **Influence polarity** — the SAME talisman profile can be net-positive or net-negative
  depending on fit. Messi's profile fits current Argentina better than Ronaldo's fits
  current Portugal. The profile is identical; the *interaction* with the squad decides
  the sign. (Two sides of one coin.)
- **Position & role nuance** — e.g. a striker who drops deep to receive but is not good
  at it (the developer's Gyökeres read) is a NEGATIVE role-fit signal even for a good
  player; a profile must capture *how* he plays, not just quality.
- **Golden-boot / individual-incentive race** — a striker chasing the golden boot has an
  individual incentive that can raise his shot volume; a factor for player-level markets.

### Team profile
- **Star-dependence vs system spectrum** — at one end, star teams (Argentina/Messi,
  Portugal/Ronaldo) live and die by the talisman; at the other, system teams (Japan,
  Netherlands, Spain-2022) rely on a game plan. France is the special case: depth so
  strong in every position it beats individual brilliance *unless* that brilliance is
  backed by tactics. So "star dependence" is a team attribute on a spectrum, and it sets
  how much the player profiles even matter for that team.
- **Chemistry / form counterweight** — a star's influence is balanced by team chemistry,
  group-stage form, and whether there is an above-average player in the same position
  (read from group data via the FM/Opta-style approach).

### Manager profile
- **Risk axis** (cautious <-> gambler) — from in-game decisions, sub timing/type,
  selection. Affects both the rating split and the VARIANCE (gamblers widen outcomes).
- **Tactical identity** — low block, pressing, set-piece dependence, how they set up vs
  stronger/weaker sides.

### The key principle: interactions, not sums
These do not add up naively. Talisman × squad-depth × chemistry × tactics INTERACT. A
strong talisman on a low-chemistry team with a cautious manager is a different animal than
the same talisman on a deep, well-drilled side. Modelling the *interactions* is the
research problem. We start with single effects (does talisman-presence matter at all?) and
add interactions only as the judge validates each layer.

---

## 4. Data sourcing (per the developer's plan)

- BEFORE: base rate (have it) + group-stage stats (have results; xG and richer stats to
  be sourced) + **lineups** (developer will provide pre-match) + profile attributes
  (seeded from the developer's football knowledge and the analyst transcripts).
- DURING/AFTER: analyst transcripts (The Tactics Board, etc.), the developer's own match
  observations, news on tactical changes (e.g. France moving a winger to the 10 to make
  room on the flank). Research-only inputs that LABEL what the residual meant.
- The instrument learns from how systems like Opta, The Athletic, and Football Manager
  collect and use such data (the "reverse-engineer proven systems" approach), expressed
  in real math: probability, numerical analysis, bias-correction, interaction terms.

---

## 5. Build sequence (Sprint 3)

- **S14 — Residual analyzer (the judge).** Per-team residuals from the BEFORE model;
  consistency-vs-luck measure; systematic-bias correction. JUDGE FIRST.
- **S15 — Profile data model.** Java structures for player/team/manager profiles as
  designed in section 3. Designed now, populated from developer knowledge + transcripts.
  No claims trusted yet — just the home for the data.
- **S16 — First profile modifier: talisman presence.** A `RatingModifier` using lineup
  (is the talisman starting?) + team star-dependence. Judged on Scoreboard + residual
  analyzer. Kept only if it helps.
- **S17 — Validation across tournaments.** Load 2018/UCL/Euro-Copa; test whether each
  validated signal REPLICATES (independent replication, no cross-tournament leakage).
  A signal that only works on 2022 was a fluke.
- **S18+ — Interactions, manager profile, more player profiles** — each added only after
  passing the judge, in the same `RatingModifier` framework.

### Hard rules carried forward
- No leakage: BEFORE-modifiers read only pre-kickoff data; residual analysis never feeds
  knockout results back into the BEFORE prediction it grades.
- Identity at zero: every modifier is a no-op at weight 0, so the backtest measures it.
- The eye proposes, the math disposes: no profile is trusted on narrative alone.
- Tournaments only; luck is reported, not hidden.
