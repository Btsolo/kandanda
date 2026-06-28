package com.kandanda.rating;

/**
 * A team's fitted strength: attack and defence multipliers, both centred on 1.0.
 *
 * <ul>
 *   <li>{@code attack} &gt; 1.0 means the team scores more than an average team.</li>
 *   <li>{@code defence} &lt; 1.0 means the team concedes LESS than average — so for
 *       defence, lower is better. (A common source of sign confusion; named clearly.)</li>
 * </ul>
 *
 * <p>These are the two numbers per team that feed the Dixon-Coles match model (S4).
 */
public record TeamRating(String team, double attack, double defence) {

    @Override
    public String toString() {
        return String.format("%-16s atk=%.2f def=%.2f", team, attack, defence);
    }
}
