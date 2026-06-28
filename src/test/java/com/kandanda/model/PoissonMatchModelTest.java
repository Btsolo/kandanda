package com.kandanda.model;

import com.kandanda.rating.TeamRating;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Poisson scoreline model.
 *
 * <p>Expected values are textbook Poisson numbers and were cross-checked in a Python
 * prototype before the Java port. We verify: the PMF itself, that the grid is a valid
 * probability distribution (sums to ~1), and symmetry (equal teams ⇒ equal win/loss).
 */
class PoissonMatchModelTest {

    private static final double TOL = 1e-4;

    @Test
    void poissonMatchesTextbookValues() {
        // Poisson(lambda=2): P(0)=e^-2=0.13534, P(1)=2e^-2=0.27067, P(2)=0.27067
        assertEquals(0.13534, PoissonMatchModel.poisson(0, 2.0), TOL);
        assertEquals(0.27067, PoissonMatchModel.poisson(1, 2.0), TOL);
        assertEquals(0.27067, PoissonMatchModel.poisson(2, 2.0), TOL);
    }

    @Test
    void gridIsValidProbabilityDistribution() {
        var model = new PoissonMatchModel(1.34);
        var grid = model.buildGrid(1.8, 1.1);
        // All mass should sum to ~1.0 (tiny loss to truncation beyond 10 goals).
        assertEquals(1.0, grid.totalProbability(), 1e-3);
    }

    @Test
    void exactScorelineProbabilityIsProductOfMarginals() {
        var model = new PoissonMatchModel(1.34);
        var grid = model.buildGrid(1.8, 1.1);
        // p(1,0) = P_home(1)*P_away(0)
        double expected = PoissonMatchModel.poisson(1, 1.8) * PoissonMatchModel.poisson(0, 1.1);
        assertEquals(expected, grid.probabilityOf(1, 0), 1e-12);
    }

    @Test
    void equalTeamsGiveSymmetricWinLoss() {
        var model = new PoissonMatchModel(1.34);
        var grid = model.buildGrid(1.4, 1.4);
        double win = 0, loss = 0;
        int n = grid.maxGoals();
        for (int i = 0; i <= n; i++) {
            for (int j = 0; j <= n; j++) {
                if (i > j) win += grid.probabilityOf(i, j);
                else if (i < j) loss += grid.probabilityOf(i, j);
            }
        }
        assertEquals(win, loss, 1e-9);
    }

    @Test
    void strongerAttackRaisesExpectedGoals() {
        var model = new PoissonMatchModel(1.34);
        var strong = new TeamRating("Strong", 2.0, 1.0);
        var weak = new TeamRating("Weak", 0.5, 1.0);
        var grid = model.predict(strong, weak);
        // Strong's expected goals = 2.0 * 1.0 * 1.34 = 2.68; Weak's = 0.5*1.0*1.34 = 0.67
        assertEquals(2.68, grid.homeExpectedGoals(), 1e-9);
        assertEquals(0.67, grid.awayExpectedGoals(), 1e-9);
        assertTrue(grid.homeExpectedGoals() > grid.awayExpectedGoals());
    }

    @Test
    void rejectsNonPositiveLeagueAverage() {
        assertThrows(IllegalArgumentException.class, () -> new PoissonMatchModel(0));
    }

    @Test
    void dixonColesRhoZeroEqualsPlainPoisson() {
        // rho=0 must reproduce pure Poisson exactly (backward compatibility).
        var plain = new PoissonMatchModel(1.34).buildGrid(1.8, 1.1);
        var dcZero = new PoissonMatchModel(1.34, 0.0).buildGrid(1.8, 1.1);
        for (int i = 0; i <= plain.maxGoals(); i++) {
            for (int j = 0; j <= plain.maxGoals(); j++) {
                assertEquals(plain.probabilityOf(i, j), dcZero.probabilityOf(i, j), 1e-12);
            }
        }
    }

    @Test
    void dixonColesGridStillSumsToOne() {
        var grid = new PoissonMatchModel(1.34, -0.1).buildGrid(1.6, 1.2);
        assertEquals(1.0, grid.totalProbability(), 1e-9);
    }

    @Test
    void negativeRhoRaisesDrawsLowersOneNil() {
        var plain = new PoissonMatchModel(1.34, 0.0).buildGrid(1.4, 1.1);
        var dc = new PoissonMatchModel(1.34, -0.1).buildGrid(1.4, 1.1);
        // 0-0 and 1-1 should rise; 1-0 and 0-1 should fall.
        assertTrue(dc.probabilityOf(0, 0) > plain.probabilityOf(0, 0));
        assertTrue(dc.probabilityOf(1, 1) > plain.probabilityOf(1, 1));
        assertTrue(dc.probabilityOf(1, 0) < plain.probabilityOf(1, 0));
        assertTrue(dc.probabilityOf(0, 1) < plain.probabilityOf(0, 1));
    }
}