package org.vwtfafa.backpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

class TeamRegistryTest {

    @Test
    void createTeamIndexesOwnerAndMembers() {
        TeamRegistry registry = new TeamRegistry();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        registry.createTeam(owner, Set.of(member));

        assertEquals(owner, registry.findOwner(owner));
        assertEquals(owner, registry.findOwner(member));
        assertEquals(Set.of(owner, member), registry.membersOf(owner));
    }

    @Test
    void memberIsMovedOutOfPreviousTeam() {
        TeamRegistry registry = new TeamRegistry();
        UUID firstOwner = UUID.randomUUID();
        UUID secondOwner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        registry.createTeam(firstOwner, Set.of(member));
        registry.createTeam(secondOwner, Set.of(member));

        assertEquals(secondOwner, registry.findOwner(member));
        assertEquals(Set.of(firstOwner), registry.membersOf(firstOwner));
    }

    @Test
    void ownerLeavingTransfersToLowestUuid() {
        TeamRegistry registry = new TeamRegistry();
        UUID owner = UUID.randomUUID();
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        registry.createTeam(owner, Set.of(low, high, owner));

        assertTrue(registry.removeMember(owner));
        UUID successor = registry.findOwner(low);
        assertEquals(successor, registry.findOwner(high));
        assertFalse(successor.equals(owner));
    }

    @Test
    void removingLastMemberDropsTeam() {
        TeamRegistry registry = new TeamRegistry();
        UUID owner = UUID.randomUUID();
        registry.createTeam(owner, Set.of());
        assertTrue(registry.removeMember(owner));

        assertNull(registry.findOwner(owner));
        assertNull(registry.membersOf(owner));
    }

    @Test
    void tryAddMemberRespectsCapacity() {
        TeamRegistry registry = new TeamRegistry();
        UUID owner = UUID.randomUUID();
        registry.createTeam(owner, Set.of());

        assertTrue(registry.tryAddMember(owner, UUID.randomUUID(), 2));
        assertFalse(registry.tryAddMember(owner, UUID.randomUUID(), 2));
        assertFalse(registry.tryAddMember(UUID.randomUUID(), UUID.randomUUID(), 5));
    }

    @Test
    void membersOfReturnsIsolatedSnapshot() {
        TeamRegistry registry = new TeamRegistry();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        registry.createTeam(owner, Set.of(member));

        Set<UUID> snapshot = registry.membersOf(owner);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(UUID.randomUUID()));
        assertEquals(Set.of(owner, member), registry.membersOf(owner));
    }

    @Test
    void entriesAreIsolatedSnapshots() {
        TeamRegistry registry = new TeamRegistry();
        UUID owner = UUID.randomUUID();
        registry.createTeam(owner, Set.of());

        for (Map.Entry<UUID, Set<UUID>> entry : registry.entries()) {
            assertThrows(UnsupportedOperationException.class,
                    () -> entry.getValue().add(UUID.randomUUID()));
        }
        assertEquals(Set.of(owner), registry.membersOf(owner));
    }
}
