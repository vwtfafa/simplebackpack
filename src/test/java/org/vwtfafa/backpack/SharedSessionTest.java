package org.vwtfafa.backpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.UUID;

class SharedSessionTest {

    @Test
    void testSharedSessionCreation() {
        UUID owner = UUID.randomUUID();
        long expiry = System.currentTimeMillis() + 3600000;
        SharedSession session = new SharedSession(owner, expiry);

        assertEquals(owner, session.getOwner());
        assertFalse(session.isExpired());
    }

    @Test
    void testSharedSessionExpired() {
        UUID owner = UUID.randomUUID();
        long expiry = System.currentTimeMillis() - 1000;
        SharedSession session = new SharedSession(owner, expiry);

        assertEquals(owner, session.getOwner());
        assertTrue(session.isExpired());
    }

    @Test
    void testSharedSessionNotExpired() {
        UUID owner = UUID.randomUUID();
        long expiry = System.currentTimeMillis() + 1000;
        SharedSession session = new SharedSession(owner, expiry);

        assertEquals(owner, session.getOwner());
        assertFalse(session.isExpired());
    }
}
