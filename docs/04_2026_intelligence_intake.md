# Kandanda Intelligence — Document 04
## 2026 intelligence intake: transcript-derived hypotheses, the xG upgrade, and validation discipline

Status: intake log. Written 30 June 2026 from developer-supplied Tactics Board / Athletic
transcripts and a 2026 group-stage data file. NOTHING here is trusted yet — every item is a
HYPOTHESIS to be tested by the residual analyzer (S14) and the scoreboard. This doc is the
structured holding pen between "raw football read" and "validated model input."

---

## 0. Validation discipline for 2026 (read first)

2026 is the LIVE TEST SET. The model was built and validated on 2022. Therefore:

1. **No mimicry.** The data file includes SportRadar R32 win probabilities. We must lock
   in Kandanda's own predictions BEFORE comparing, and NEVER tune the model to match
   SportRadar. Divergence is a DIAGNOSTIC, not a target. (If we chase the benchmark we are
   just rebuilding SportRadar badly — the Opta lesson.)
2. **2026 enters as games to PREDICT, not as training to fiddle with.** Group results feed
   the rating fit (that's legitimate — it's the "before" data for knockout prediction), but
   we do not hand-tune on knockout outcomes as they arrive. Predict, then score.
3. Every transcript read below is the INTELLIGENCE layer proposing. The MATH (residuals,
   calibration) disposes. Yin-yang.

---

## 1. THE BIGGEST UPGRADE: use xG as the Poisson rate, not goals

The data file supplies per-match xG for all 2026 group games. This is potentially the
single most valuable change available, and it is independently argued by the England/Panama
transcript ("a boring 2-0 we'd win 19 times out of 20 is a better performance than an
exciting 4-3").

- **Why:** xG strips finishing variance — the exact luck the prior (S7) was fighting. A
  team's xG is a cleaner estimate of its underlying scoring rate than the goals it happened
  to convert. Feeding xG (or a blend of goals and xG) as the Maher/Poisson input should
  improve calibration more than any single profile modifier.
- **Evidence already visible:** Belgium +1.61 xG-diff/match while underperforming on
  results; Türkiye 12th in xG but eliminated (variance victim); Brazil only +0.24 despite
  reputation. These are residual signals the analyzer (S14) should surface.
- **Build note:** this is a Tier-1 data upgrade, not a Tier-2 modifier. Candidate to slot
  in around S14. Needs an xG-bearing dataset (2026 file has it; 2022 would need xG sourced
  to keep the validation basket consistent). Decision pending.

---

## 2. TACTICAL / STRUCTURAL hypotheses (team-level)

### H1 — Low block is "meta"; breaking it needs elite wide 1v1 quality
From the low-block transcript. Claim: against most top teams a disciplined low block is
effective, because only sides with elite wide dribblers (named: France, Spain) can break it
down; everyone else is reduced to crossing, which is low-efficiency. Examples cited:
Paraguay low-blocked Germany to penalties (and won); Ghana 0-0 vs England; Netherlands
low-blocked Morocco.
- **Testable form:** a team attribute `eliteWideQuality` (high for France/Spain). Hypothesis:
  high-`eliteWideQuality` teams suffer LESS attack-rating decay against deep-block opponents.
  Equivalently, underdogs with a disciplined low block over-perform their base rating vs
  favourites lacking wide quality. The residual analyzer can check whether low-block
  underdogs systematically beat expectation.

### H2 — System strength dominates individual quality (France special case)
From "systems > individuals" and "every footballer is a system player." Claim: player output
is contextual; a strong system platforms average players (Vitinha poor outside PSG; Pedri
thrives in Spain). France's distinguishing trait is depth/system so strong it beats
individual brilliance unless that brilliance is itself system-backed.
- **Testable form:** the star-dependence↔system spectrum from doc 03 section 3. System teams
  should show LOWER variance (more consistent residuals); star teams HIGHER variance
  (residuals swing with the star's involvement).

---

## 3. PLAYER / TALISMAN hypotheses

### H3 — Ronaldo polarity: not the problem, the system around him is
From the Ronaldo transcript, and it directly confirms the developer's "two sides of one
coin" read. Claim: Ronaldo (now a box poacher with elite IQ, not an athletic runner) is
mis-platformed by Martinez, who still builds for the player of 10 years ago. Contrast:
Scaloni built Argentina around an aging Messi by having the team compensate for his off-ball
work — a NET POSITIVE fit. Same talisman profile, opposite net effect.
- **Testable form:** talisman modifier must be SIGNED by fit, not just presence. Argentina+
  Messi = positive; Portugal+Ronaldo = near-neutral/negative GIVEN the current system.

### H4 — Messi's squad relationship is an unusual chemistry multiplier
From the Athletic Messi transcript: teammates park personal ambition to support his story;
unique aura/cohesion. Hypothesis: Argentina carries a chemistry bonus beyond its raw ratings.
- **Testable form:** a one-off chemistry attribute for Argentina-2026; only trusted if the
  residual analyzer shows Argentina consistently over-performing its base rate.

### H5 — Bellingham as England's hidden width/creativity unlock
From two Tactics Board clips: England are risk-averse (winger and full-back won't both push
up), creating narrowness; Bellingham, a midfielder, self-converts into the winger via
diagonal runs and is the one player who can beat a man wide — he created both goals vs
Panama. Hypothesis: England's attack rating should be conditioned on Bellingham's role/start.

### H6 — Kane's poor games are a SUPPLY problem, not a Kane problem
From "Why Harry Kane struggled vs Ghana": Kane's box movement was fine; the players behind
him (Saka, Bellingham, Gordon) failed to create/deliver quality. Hypothesis: Kane's output
is a function of chance-supply, not his own form — a caution against blaming the striker's
rating when the residual is really a midfield-creation deficit.

### H7 — Nico O'Reilly as an aerial second-striker (set-piece / late-game weapon)
From "Nico O'Reilly is England's Haaland": a 6'5" left-back used as a box aerial threat,
especially in chasing game-states. Hypothesis: a situational attack bump for England in
losing/late states — a game-state-conditional effect (hard to use in a pre-match-only model;
flagged for the DURING phase / future).

### H8 — Yamal as Spain's tactical unlock
From the Athletic Yamal clip: without Yamal, Spain horseshoe possession and stall vs deep
blocks (cf. Cape Verde 0-0); with him, his 1v1 ability creates chances that otherwise do not
exist AND lets teammates play earlier/more direct. Ties to H1 (Yamal IS the elite wide
quality that beats low blocks). Hypothesis: Spain's attack rating is strongly conditioned on
Yamal starting.

### H9 — Talisman blame-load / responsibility
From "every footballer is a system player" + the Kane and star transcripts. Stars (Kane,
Messi, Ronaldo, Bellingham) carry disproportionate team load and blame. This is the
`talismanResponsibility` attribute in doc 03 — high responsibility means the team swings more
with the star's presence/role.

---

## 4. INDIVIDUAL-INCENTIVE hypotheses (player markets)

### H10 — Golden Boot race inflates star shot/goal volume
Top scorers (data file §5): Messi 6; Mbappé 4, Dembélé 4, Haaland 4, Vinícius 4; Kane 3.
Hypothesis: players in a live Golden Boot race have individual incentive raising their shot
volume — relevant for first/anytime-scorer and player-goal markets (a future profile-market
layer, not the team result model).

---

## 4b. DESIGN PRINCIPLE: position-appropriate data + FM/Opta/Brighton mimicry

A correction locked in after the developer flagged a drift: we are NOT inventing a
player-rating system. We reverse-engineer proven ones (Football Manager, Opta,
Brighton), exactly as with the base rate. Two consequences:

**(a) Data must match the position/role — goals and xG are striker-centric.**
Building the profile layer around goals/xG alone would create a system that can only
"see" attackers. A profile must use the signal appropriate to the job:
- Attackers: goals, xG, shots, aerial threat (target men / O'Reilly-type).
- Creative mids / playmakers: xA (expected assists), chances created, progressive
  passes and carries, key passes.
- Deep mids / defenders: defensive actions, ball recoveries, duels won, progressive
  passing from deep, expected goals PREVENTED / conceded-vs-expected.
- Keepers: post-shot xG faced minus goals conceded (shot-stopping over expectation),
  command of area.
Most of these ARE available free (StatsBomb event data, FBref) — so this is largely a
data-selection discipline, not a sourcing blocker.

**(b) FM's structure is the template — mimic, don't reinvent.**
FM has solved "how to represent a player as data" over decades: attributes are
organised BY position/role, and contribution depends on role + duty + system-fit. We
study that organisation (and Opta's positional metrics, Brighton's role-based
valuation) to SHAPE our schema — then deliberately implement a MINIMAL first slice and
expand only as each attribute earns its place on the residual judge. Research deep,
build shallow.

**(c) Two provenances, clearly separated.** The schema holds two kinds of attribute:
- DATA-DERIVED (xA, progressive passes, defensive actions, keeper post-shot xG) —
  pullable from StatsBomb/FBref. The "math" side.
- JUDGMENT-DERIVED (talisman responsibility, Messi/Ronaldo polarity, big-match
  temperament, role-fit reads) — from the developer's eye and the transcripts, exist
  in NO dataset. The "eye" side.
Tag every attribute by provenance, so when the judge says a signal worked we can ask
whether it came from stats or a human read. This is the yin-yang principle at the data
level.

This frame governs the S15 schema design. Before implementing S15, research the
FM/Opta/Brighton position-role models properly, then design position-aware with both
provenances, implement minimal, expand on evidence.

---

## 5. DATA SOURCES (for the eventual live pipeline — deferred)

From the data file §1. Realistic free sources: FBref (xG, CSV exports), api-football.com
(fixtures/lineups, 100 req/day free), Football-Data.org, TheSportsDB. Avoid scraping FIFA.com
(ToS). Sofascore rate-limits hard. The Athletic is subscription (manual transcription only).
Build the automated pipeline LAST, once the profile schema is validated.

---

## 6. How these enter the build (priority order)

1. **xG as Poisson input (§1)** — biggest expected calibration gain; Tier-1 data upgrade.
   Source 2022 per-match xG from StatsBomb Open Data (comp 43, season 106; free, on
   GitHub, per-match derivable from shot events). Add xg field to match data, refactor
   model to switch goals<->xG, validate on 2022 knockouts via backtest. THE PROOF STEP.
2. **S14 residual analyzer** — the judge; required before trusting any H-item. Sits on
   the xG foundation (build xG first so residuals mean something).
3. **Profile schema (S15)** — position-aware, two-provenance (data + judgment), shaped
   by FM/Opta/Brighton study per section 4b. Research deep, implement minimal.
   Beyond xG, this needs position-appropriate data: xA/chance-creation (mids),
   defensive actions (defs), post-shot xG (keepers) — sourced when S15 is reached.
4. **Modifiers, each tested:** talisman-fit (H3), chemistry (H4), star-unlock (H5/H8),
   system-vs-star variance (H2), low-block interaction (H1).
5. Player-market layer (H10) and game-state effects (H7) — later.

Every H-item stays a HYPOTHESIS until the residual analyzer / scoreboard validates it on
out-of-sample games. The eye proposed (these transcripts); the math will dispose.
