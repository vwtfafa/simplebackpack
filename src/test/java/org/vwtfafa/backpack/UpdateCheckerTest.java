package org.vwtfafa.backpack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    @Test
    void equalVersionsAreNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("1.0", "1.0"));
        assertFalse(UpdateChecker.isNewerVersion("1.0.0", "1.0"));
    }

    @Test
    void higherVersionsAreNewer() {
        assertTrue(UpdateChecker.isNewerVersion("1.1", "1.0"));
        assertTrue(UpdateChecker.isNewerVersion("2.0", "1.0"));
        assertTrue(UpdateChecker.isNewerVersion("1.0.1", "1.0"));
        assertTrue(UpdateChecker.isNewerVersion("1.0.1", "1.0.0"));
    }

    @Test
    void lowerVersionsAreNotNewer() {
        assertFalse(UpdateChecker.isNewerVersion("0.9", "1.0"));
        assertFalse(UpdateChecker.isNewerVersion("1.0", "1.0.1"));
    }

    @Test
    void vPrefixIsIgnored() {
        assertTrue(UpdateChecker.isNewerVersion("v2.0", "1.9"));
        assertFalse(UpdateChecker.isNewerVersion("v1.0", "1.5"));
    }

    @Test
    void unparseableVersionsNeverReportUpdates() {
        assertFalse(UpdateChecker.isNewerVersion("not-a-version", "1.0"));
        assertFalse(UpdateChecker.isNewerVersion("1.0-beta", "1.0"));
    }

    @Test
    void nullAndBlankVersionsNeverReportUpdates() {
        assertFalse(UpdateChecker.isNewerVersion(null, "1.0"));
        assertFalse(UpdateChecker.isNewerVersion("1.0", null));
        assertFalse(UpdateChecker.isNewerVersion("", "1.0"));
        assertFalse(UpdateChecker.isNewerVersion("   ", "1.0"));
    }

    @Test
    void whitespaceAndBothSidedPrefixesAreIgnored() {
        assertTrue(UpdateChecker.isNewerVersion(" v2.0 ", "v1.9"));
        assertFalse(UpdateChecker.isNewerVersion("v1.0", "v1.5"));
    }

    @Test
    void multiDigitSegmentsCompareNumerically() {
        assertTrue(UpdateChecker.isNewerVersion("7.10", "7.2"));
        assertFalse(UpdateChecker.isNewerVersion("7.2", "7.10"));
    }

    @Test
    void qualifiersNeverWinOverTheirRelease() {
        assertFalse(UpdateChecker.isNewerVersion("1.0-beta", "1.0"));
        assertTrue(UpdateChecker.isNewerVersion("1.0", "1.0-beta"));
        assertFalse(UpdateChecker.isNewerVersion("1.0-SNAPSHOT", "1.0"));
        assertFalse(UpdateChecker.isNewerVersion("26.3-pre-2", "26.3"));
    }
}
