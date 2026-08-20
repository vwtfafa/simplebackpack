package org.vwtfafa.backpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class BackpackManagerTest {

    @Test
    void testResolveEffectiveOwnerWithoutSession() {
        UUID player = UUID.randomUUID();
        SharedSession session = new SharedSession(player, System.currentTimeMillis() + 1000);

        assertEquals(player, session.getOwner());
        assertFalse(session.isExpired());
        }

        @Test
        void inventoryHoldersExposeTheirPurpose() {
        UUID owner = UUID.randomUUID();

        assertEquals(BackpackInventoryHolder.Type.BACKPACK,
            BackpackInventoryHolder.backpack(owner).getType());
        assertEquals(BackpackInventoryHolder.Type.ADMIN,
            BackpackInventoryHolder.admin(owner, true).getType());
        assertTrue(BackpackInventoryHolder.admin(owner, true).isPreview());
        assertEquals(BackpackInventoryHolder.Type.CONFIG,
            BackpackInventoryHolder.config().getType());
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
