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
}
