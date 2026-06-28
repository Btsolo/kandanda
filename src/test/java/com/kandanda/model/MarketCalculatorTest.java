package com.kandanda.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for market derivation.
 *
 * <p>The strongest checks are the PARTITION checks: 1X2, over/under, BTTS, and
 * odd/even each carve the whole probability space into mutually exclusive, exhaustive
 * pieces — so each set must sum to 1.0. If any doesn't, a market formula is wrong.
 * Values cross-checked against a Python prototype (grid for lambda 1.8 / 1.1).
 */
class MarketCalculatorTest {

    private static final double TOL = 1e-6;

    private MarketCalculator calc(double lh, double la) {
        var grid = new PoissonMatchModel(1.34).buildGrid(lh, la);
        return new MarketCalculator(grid);
    }

    @Test
    void oneXtwoPartitionsToOne() {
        var c = calc(1.8, 1.1);
        assertEquals(1.0, c.homeWin() + c.draw() + c.awayWin(), TOL);
    }

    @Test
    void overUnderPartitionsToOne() {
        var c = calc(1.8, 1.1);
        assertEquals(1.0, c.over(2.5) + c.under(2.5), TOL);
    }

    @Test
    void bttsPartitionsToOne() {
        var c = calc(1.8, 1.1);
        assertEquals(1.0, c.bttsYes() + c.bttsNo(), TOL);
    }

    @Test
    void oddEvenPartitionsToOne() {
        var c = calc(1.8, 1.1);
        assertEquals(1.0, c.oddTotalGoals() + c.evenTotalGoals(), TOL);
    }

    @Test
    void drawNoBetPartitionsToOne() {
        var c = calc(1.8, 1.1);
        assertEquals(1.0, c.drawNoBetHome() + c.drawNoBetAway(), TOL);
    }

    @Test
    void doubleChanceMatchesComponentSums() {
        var c = calc(1.8, 1.1);
        assertEquals(c.homeWin() + c.draw(), c.doubleChanceHomeOrDraw(), TOL);
        assertEquals(c.draw() + c.awayWin(), c.doubleChanceDrawOrAway(), TOL);
        assertEquals(c.homeWin() + c.awayWin(), c.doubleChanceHomeOrAway(), TOL);
    }

    @Test
    void knownValuesFromPrototype() {
        var c = calc(1.8, 1.1);
        // From the verified Python prototype:
        assertEquals(0.538, c.homeWin(), 1e-3);
        assertEquals(0.554, c.over(2.5), 1e-3);
        assertEquals(0.557, c.bttsYes(), 1e-3);
        assertEquals(0.099, c.correctScore(1, 0), 1e-3);
    }

    @Test
    void equalTeamsSymmetricMarkets() {
        var c = calc(1.4, 1.4);
        assertEquals(c.homeWin(), c.awayWin(), TOL);
        assertEquals(c.homeWinByExactly(1), c.awayWinByExactly(1), TOL);
    }
}
