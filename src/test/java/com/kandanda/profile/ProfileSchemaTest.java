package com.kandanda.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the S15 profile schema.
 *
 * <p>What matters here is the CONTRACT, not computation: provenance is physically
 * separated (you must construct DataDerived and JudgmentDerived explicitly), judged
 * values are range-checked (a hypothesis must at least be well-formed), and neutral
 * factories make "no claims" the easy default — a profile should never smuggle in an
 * accidental opinion.
 */
class ProfileSchemaTest {

    @Test
    void playerProfileHoldsBothProvenancesExplicitly() {
        // Messi 2022-style profile: high talisman responsibility, thrives on big stage,
        // system built to fit him (Scaloni). Data side left empty until sourced.
        var messi = new PlayerProfile(
                "Lionel Messi", "Argentina", PlayerProfile.PositionGroup.ATT,
                PlayerProfile.DataDerived.none(),
                new PlayerProfile.JudgmentDerived(0.95, 0.8, 0.9));
        assertEquals("Argentina", messi.team());
        assertEquals(0.95, messi.judgment().talismanResponsibility());
        assertNull(messi.data().xgPer90(), "unsourced data stays null, never guessed");
    }

    @Test
    void judgmentValuesAreRangeChecked() {
        assertThrows(IllegalArgumentException.class, () ->
                new PlayerProfile.JudgmentDerived(1.5, 0, 0)); // responsibility > 1
        assertThrows(IllegalArgumentException.class, () ->
                new PlayerProfile.JudgmentDerived(0.5, -2, 0)); // temperament < -1
        assertThrows(IllegalArgumentException.class, () ->
                new PlayerProfile.JudgmentDerived(0.5, 0, 2)); // roleFit > 1
    }

    @Test
    void neutralJudgmentMakesNoClaims() {
        var neutral = PlayerProfile.JudgmentDerived.neutral();
        assertEquals(0.0, neutral.talismanResponsibility());
        assertEquals(0.0, neutral.bigMatchTemperament());
        assertEquals(0.0, neutral.roleFit());
    }

    @Test
    void teamProfileEncodesStarDependenceSpectrum() {
        // H2: Argentina = star team around Messi; Japan = system team, no talisman.
        var argentina = new TeamProfile("Argentina", 0.85, 0.8, 0.5, "Lionel Messi");
        var japan = new TeamProfile("Japan", 0.15, 0.3, 0.2, null);
        assertTrue(argentina.starDependence() > japan.starDependence());
        assertNull(japan.talisman(), "system teams have no talisman");
    }

    @Test
    void teamProfileRangeChecked() {
        assertThrows(IllegalArgumentException.class,
                () -> new TeamProfile("X", 1.2, 0, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamProfile("X", 0.5, -1.5, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TeamProfile("X", 0.5, 0, 9, null));
    }

    @Test
    void managerProfileRiskAxisChecked() {
        var gambler = new ManagerProfile("Gambler", "X", 0.9);
        assertEquals(0.9, gambler.riskAxis());
        assertThrows(IllegalArgumentException.class,
                () -> new ManagerProfile("Bad", "X", 3.0));
        assertEquals(0.0, ManagerProfile.neutral("N", "X").riskAxis());
    }
}
