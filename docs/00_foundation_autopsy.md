# Kandanda Intelligence — Foundation Document 00
## Autopsy of existing prediction systems, and the learning path for Sprint 1

Status: Sprint 1, Phase A (research synthesis). Written 21 June 2026, during the 2026 World Cup group stage.

This document is the "study the masters before you build" phase you asked for. It decodes the three reference systems, autopsies where they failed and why, draws the engineering lessons, and lays out the concrete learning path for the first build. No code yet — that comes once you have read and pushed back on this.

---

## 1. The core reframe (read this first)

You came in wanting a "score prediction system" that gives the 10 most likely outcomes per match and maps them to betting markets. After autopsying the real systems, the project should split cleanly into two layers that must never be confused:

- **Intelligence layer** — player impact/influence, tactics, coach style, chemistry, off-pitch factors, form, conditions. This is the FM/Brighton-inspired part. It is *hard*, *data-hungry*, and *where understanding lives*.
- **Prediction layer** — converting team-strength numbers into calibrated probabilities across every betting market. This is a *largely solved mathematical problem* (Poisson / Dixon-Coles + Monte Carlo). It is the part you can build, understand, and validate quickly.

The intelligence layer feeds two numbers into the prediction layer per team: an **attack strength** and a **defence strength**. Everything fancy you want to do (player influence, tactics, fatigue) ultimately exists to make those two numbers per team better than the bookmaker's. That is the whole architecture in one sentence.

The single most important finding from the research: **a model can be "correct" repeatedly and still be worthless.** Klement proved it. So our definition of success is NOT "did we pick the winner" — it is **calibration measured over many predictions** (when we say 30%, does it happen ~30% of the time?). Build the scoreboard before the player.

---

## 2. System teardown #1 — Opta Supercomputer (the one worth copying)

### How it actually works
Despite the "supercomputer" branding, the mechanism is modest and learnable:

1. **Team strength ratings** — each team has attacking and defensive strength ratings, calibrated against thousands of historical international matches (this is essentially the Opta Power Rankings).
2. **Match model** — for any fixture, those ratings produce a probability of win / draw / loss. Opta *also folds in betting-market odds* as an input.
3. **Monte Carlo simulation** — the entire tournament bracket is played out 10,000–25,000 times. Count how often each team reaches each stage / wins. Those counts become the percentages you see ("Spain 16.1%").

### The critical structural insight
Because Opta uses **bookmaker odds as an input**, its output can never diverge wildly from the market. It is a sophisticated blend of (market wisdom) + (its own ratings). Consequence: the headline numbers are not the interesting output. **The divergences are.** Example from June 2026: Opta gives Brazil 6.6% to win; bookmakers imply ~10%. That ~3.4-point gap is the only genuinely actionable signal in the whole table — it says "the market is more bullish on Brazil than the fundamentals justify." Value betting is entirely about finding and sizing these gaps.

### What we copy
- The **two-numbers-per-team** abstraction (attack, defence).
- **Monte Carlo simulation** of the bracket for tournament-level markets (winner, to-reach-final, stage of elimination, etc.).
- The discipline of **comparing your probability to the market's implied probability** as the core output, not the raw probability alone.

### What we deliberately do NOT copy at first
Using bookmaker odds as a model input. For learning purposes we want our strength ratings to be *independent* of the market, so that our divergences are meaningful. If we feed the market in, we just re-derive the market. (Opta does it because their commercial goal is to be reliably close, not to be independently right.)

---

## 3. System teardown #2 — Opta's failure history (your validation curriculum)

This is the most useful section for building intuition about *what is and isn't predictable*.

| Tournament | Model said | Reality | Lesson |
|---|---|---|---|
| 2014 WC | Brazil favoured | Brazil reached semis, lost 7-1 to Germany | Catastrophic single-game collapse is invisible to strength ratings |
| 2018 WC | France highly rated | France won | Hit — favourites tier is modelable |
| 2022 WC | Argentina ~5% | Argentina won | The *eventual champion* was given 1-in-20. Tournament winner is near-unpredictable |
| Euro 2024 | Spain top pick | Spain won | Hit |

**The pattern:** these models are *fine* at ranking the favourites tier and *useless* at naming the single tournament winner. Why? A World Cup win is a ~7-game sample. At that sample size, variance (injuries, red cards, penalty shootouts, one moment of brilliance, refereeing) dominates skill. Opta openly admits the model "can't predict injuries, refereeing decisions, penalty shootouts, or individual moments of brilliance."

**Engineering takeaway for us:** predict at the level where structure beats noise — the **single match** and especially **aggregate market behaviour over many matches** — and treat the tournament-winner number as entertainment, not signal. The per-match over/under and BTTS markets are far more honestly modelable than "who lifts the trophy."

---

## 4. System teardown #3 — Klement (what NOT to build)

Joachim Klement, German economist, correctly called Germany 2014, France 2018, Argentina 2022 — three in a row. His 2026 pick: Netherlands over Portugal in the final.

His variables: GDP per capita, population size, "status of football in the society," FIFA world ranking, and an explicit **randomness component**.

What is missing is everything about *football*: no tactics, no current form, no squad quality beyond FIFA rank, no player data. It is a **socioeconomic regression with a random number generator bolted on.**

And critically — **he says so himself.** He built it to *mock* economists' belief that they can predict anything. He calls it "completely irrational... like playing the lottery," says three correct calls are like "a coin landing heads three times," and that anyone betting on his pick is "beyond help."

**The lesson is the whole reason this teardown matters:** three consecutive correct predictions is *fully consistent with pure luck*. If you judge a model by whether its single headline call came true, you cannot distinguish Klement (luck) from a genuinely good model. **This is why our validation layer scores calibration across hundreds of predictions, not hit/miss on the winner.** Klement is the cautionary tale that justifies the entire scoreboard-first philosophy.

---

## 5. The mathematical spine: Dixon-Coles (this is what you learn first)

Here is the payoff that ties your whole betting-markets table to one model.

Football scorelines are well-described by the **Poisson distribution**. If you know a team's *expected goals* in a match (call it λ), the probability they score exactly k goals is a fixed formula. Give home team expected goals λ_home and away team λ_away, and you get a probability for **every scoreline** (0-0, 1-0, 2-1, ...) as a grid.

From that single scoreline grid, **every market in your list falls out by addition:**

- **1X2** — sum the grid cells where home > away (home win), = (draw), < (away win).
- **Over/Under 2.5** — sum all cells where total goals ≥ 3 vs ≤ 2.
- **Both Teams To Score** — sum all cells where both > 0.
- **Correct Score** — read a single cell directly.
- **Winning Margin** — sum cells along each diagonal.
- **Odd/Even total** — sum alternating anti-diagonals.
- **Draw No Bet, Double Chance** — simple recombinations of the 1X2 cells.
- **Asian/European Handicap** — shift the grid and re-sum.

**One model → the entire match-level betting table.** That is the "don't build from scratch, apply a working concept" principle in its purest form.

**Dixon-Coles** is the 1997 refinement of raw Poisson that the whole industry still uses. It adds two things plain Poisson gets wrong: (1) a correction for low scores (0-0, 1-0, 1-1 happen slightly more than independent Poisson predicts), and (2) a time-decay weighting so recent matches matter more than old ones. It is ~40 lines of Python. It is the single highest-value thing to learn in this project.

Where do λ_home and λ_away come from? From each team's **attack strength × opponent's defence strength × home advantage.** And *those strength numbers* are exactly what the intelligence layer (player impact, form, tactics, fatigue) exists to refine. The layers connect here.

For tournament markets (winner, reach-final, group winner, stage of elimination), you wrap the match model in **Monte Carlo**: simulate each fixture's scoreline from its grid, advance the bracket, repeat 10,000+ times, count outcomes. Same engine Opta uses.

---

## 6. Sprint 1 plan (concrete, sequenced, learning-first)

**Phase A — Research synthesis (this document).** Done once you have read and challenged it.

**Phase B — The scoreboard, before any predictor.**
Build the *validation harness* first. A small module that stores (prediction, market-implied-probability, actual-outcome) rows and computes:
- **Brier score** (calibration — the core metric)
- **log-loss** (punishes confident wrong calls)
- **vs-market baseline** (did we beat the bookmaker's implied probability?)
This is deliberately first so that the moment a predictor exists, we can already tell if it's Klement-luck or real.

**Phase C — Dixon-Coles match model on historical data.**
Implement Poisson → Dixon-Coles. Fit it on a past tournament (2022 WC group stage is ideal — known results, manageable size). Generate the scoreline grid for each match. Derive all match-level markets. Run them through the Phase B scoreboard. Learn what good/bad calibration feels like.

**Phase D — Monte Carlo tournament wrapper.**
Wrap the match model to simulate the bracket. Reproduce an Opta-style winner table for 2022 and compare to what Opta actually said and what actually happened. This is the "autopsy by reproduction" step — you'll *feel* why the winner number is noise.

**Phase E — Live 2026 rolling validation.**
Now point it at 2026. You feed in group-game-1 results, the model updates, you log predictions for game 2, score them, repeat. This is the real-time fine-tuning you described — and it's honest because you're scoring out-of-sample.

The **intelligence layer** (player influence, tactics, TikTok/Athletic scraping, coach styles, chemistry, conditions) is **Sprint 2+**. It plugs in by improving the attack/defence strength numbers that feed Dixon-Coles. We design its schema early but build it only once the spine is validated — otherwise we'd be tuning fancy inputs to a model we can't yet score.

---

## 7. Data sources — what's realistic

- **Historical match results** (for fitting): freely available. football-data.co.uk, open datasets, FIFA/Wikipedia results tables. Enough to fit Dixon-Coles.
- **Lineups / injuries / form, pre-match**: API-Football (api-football.com) has a free tier covering fixtures, lineups, injuries. This is the realistic backbone for the live layer.
- **Bookmaker odds** (to compute market-implied probabilities for the scoreboard): the-odds-api.com has a free tier; football-data.co.uk includes historical closing odds.
- **The Athletic / tactical analysis / TikTok (The Tactics Board)**: these are *intelligence-layer, Sprint 2* inputs. The Athletic is paywalled — substitute free tactical sources or transcribe manually as you said. TikTok analysis = manual transcription into structured notes. We design how these become features later; not needed for the spine.

Legality/ethics note: scraping paywalled content (The Athletic) is a terms-of-service problem; prefer official APIs and manual note-taking for those. The free APIs above cover everything Sprint 1 needs without scraping.

---

## 8. Open decisions for you

1. **Confirm the sequence**: scoreboard (B) → Dixon-Coles (C) → Monte Carlo (D) → live (E). Do you want to start by *reproducing 2022* (clean, known answers — better for learning) before touching live 2026 data?
2. **Independent ratings vs market-fed**: I recommend our strength ratings stay independent of bookmaker odds so our divergences mean something. Agree?
3. **You mentioned you'll supply current 2026 results** — for Phase E we'll want group-game results as they come in. Hold those until the spine (B–D) is built and validated on 2022.

Nothing here is built from scratch: Dixon-Coles is a 1997 paper everyone uses, Monte Carlo bracket simulation is what Opta does, and the betting-market derivations are arithmetic on the scoreline grid. We're assembling proven concepts and adding the intelligence layer on top — exactly the brief.
