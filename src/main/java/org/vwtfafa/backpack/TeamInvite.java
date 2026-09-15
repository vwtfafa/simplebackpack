package org.vwtfafa.backpack;

import java.util.UUID;

/**
 * A pending team invitation that expires after a fixed time.
 */
record TeamInvite(UUID inviter, long expiresAtMillis) {

    boolean isExpired() {
        return System.currentTimeMillis() > expiresAtMillis;
    }
}
