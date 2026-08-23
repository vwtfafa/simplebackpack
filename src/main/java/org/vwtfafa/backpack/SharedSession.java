package org.vwtfafa.backpack;

import java.util.UUID;

/**
 * A temporary grant that lets a player open another player's backpack
 * until the expiry timestamp.
 */
record SharedSession(UUID owner, long expiryMillis) {

    boolean isExpired() {
        return System.currentTimeMillis() > expiryMillis;
    }
}
