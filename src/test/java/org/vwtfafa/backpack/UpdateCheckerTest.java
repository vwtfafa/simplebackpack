package org.vwtfafa.backpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UpdateCheckerTest {

    @Test
    void testIsNewerVersion() throws Exception {
        assertNotNull(UpdateChecker.class.getDeclaredMethod("isNewerVersion", String.class, String.class));
        assertTrue(java.lang.reflect.Modifier.isPrivate(UpdateChecker.class.getDeclaredMethod("isNewerVersion", String.class, String.class).getModifiers()));

        assertEquals(false, compareVersions("1.0", "1.0"));
        assertEquals(true, compareVersions("1.1", "1.0"));
        assertEquals(false, compareVersions("0.9", "1.0"));
        assertEquals(true, compareVersions("2.0", "1.0"));
        assertEquals(true, compareVersions("1.0.1", "1.0"));
        assertEquals(true, compareVersions("1.0.2", "1.0"));
        assertEquals(false, compareVersions("1.0", "1.0.1"));
        assertEquals(true, compareVersions("1.0.1", "1.0.0"));
    }

    private boolean compareVersions(String newVersion, String currentVersion) {
        try {
            newVersion = newVersion.replaceFirst("^v", "");
            currentVersion = currentVersion.replaceFirst("^v", "");

            String[] newParts = newVersion.split("\\.");
            String[] currentParts = currentVersion.split("\\.");

            for (int i = 0; i < Math.max(newParts.length, currentParts.length); i++) {
                int newNum = i < newParts.length ? Integer.parseInt(newParts[i]) : 0;
                int currentNum = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;

                if (newNum > currentNum) return true;
                if (newNum < currentNum) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
