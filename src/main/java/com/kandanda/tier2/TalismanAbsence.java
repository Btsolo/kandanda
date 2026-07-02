package com.kandanda.tier2;

/**
 * A per-match, BEFORE-phase lineup fact: this team's talisman did not start this round's
 * match. Lineups are published before kickoff, so using this is leakage-free.
 *
 * <p>2022 knockout examples: Ronaldo benched for Portugal's Round of 16 (won 6-1 without
 * him) and Quarter-final (lost 0-1). In 2026 live use, the developer supplies these from
 * pre-match lineups.
 *
 * @param team  team whose talisman is absent (must match dataset naming)
 * @param round the round label of the match (e.g. "Round of 16")
 */
public record TalismanAbsence(String team, String round) { }