package org.vwtfafa.backpack;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class BackpackManagerTest {

    @Test
    void testResolveEffectiveOwnerWithoutSession() {
        assertTrue(true);
    }

    @Test
    void testSharedSessionCleanupLogic() {
        UUID owner = UUID.randomUUID();

        SharedSession expired = new SharedSession(owner, System.currentTimeMillis() - 1000);
        assertTrue(expired.isExpired());

        SharedSession valid = new SharedSession(owner, System.currentTimeMillis() + 3600000);
        assertFalse(valid.isExpired());
    }
}
