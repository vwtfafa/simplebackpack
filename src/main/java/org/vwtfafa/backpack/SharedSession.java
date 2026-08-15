package org.vwtfafa.backpack;

import java.util.UUID;

public class SharedSession {
    private final UUID owner;
    private final long expiryMillis;

    public SharedSession(UUID owner, long expiryMillis) {
        this.owner = owner;
        this.expiryMillis = expiryMillis;
    }

    public UUID getOwner() {
        return owner;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryMillis;
    }
}
