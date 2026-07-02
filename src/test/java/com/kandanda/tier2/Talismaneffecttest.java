package com.kandanda.tier2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the signed talisman-absence effect (S16).
 */
class TalismanEffectTest {

    @Test
    void identityWhenNoClaim() {
        // weight 0, dependence 0, or roleFit 0 -> exactly 1 (no effect).
        assertEquals(1.0, TalismanEffect.absenceMultiplier(0.7, 0.5, 0.0), 1e-12);
        assertEquals(1.0, TalismanEffect.absenceMultiplier(0.0, 0.5, 1.0), 1e-12);
        assertEquals(1.0, TalismanEffect.absenceMultiplier(0.7, 0.0, 1.0), 1e-12);
    }

    @Test
    void wellFittedStarAbsenceReducesAttack() {
        // roleFit > 0: absence hurts (multiplier < 1).
        assertTrue(TalismanEffect.absenceMultiplier(0.85, 0.9, 0.5) < 1.0);
    }

    @Test
    void misfitStarAbsenceBoostsAttack() {
        // H3, the Ronaldo case: roleFit < 0 -> absence HELPS (multiplier > 1).
        assertTrue(TalismanEffect.absenceMultiplier(0.7, -0.4, 0.5) > 1.0);
    }

    @Test
    void multiplierIsClampedBothSides() {
        // Extreme inputs cannot seize the base rate (yin-yang bound).
        assertEquals(TalismanEffect.MIN_MULT,
                TalismanEffect.absenceMultiplier(1.0, 1.0, 10.0), 1e-12);
        assertEquals(TalismanEffect.MAX_MULT,
                TalismanEffect.absenceMultiplier(1.0, -1.0, 10.0), 1e-12);
    }
}