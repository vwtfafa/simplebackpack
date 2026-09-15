package org.vwtfafa.backpack;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory team storage: owner-to-members plus an O(1) member-to-owner
 * reverse index so ownership lookups never need to scan all teams.
 */
public class TeamRegistry {
    private final Map<UUID, Set<UUID>> teamsByOwner = new HashMap<>();
    private final Map<UUID, UUID> ownerByMember = new HashMap<>();

    public void clear() {
        teamsByOwner.clear();
        ownerByMember.clear();
    }

    /**
     * Creates a team owned by the given player. Members that were tracked in
     * another team are moved out of it first.
     */
    public void createTeam(UUID owner, Set<UUID> members) {
        Set<UUID> teamMembers = new HashSet<>(members);
        teamMembers.add(owner);
        teamsByOwner.put(owner, teamMembers);
        for (UUID member : teamMembers) {
            indexMember(member, owner);
        }
    }

    /**
     * Adds a member to the team owned by the given owner.
     */
    public void addMember(UUID owner, UUID member) {
        Set<UUID> members = teamsByOwner.get(owner);
        if (members == null) {
            return;
        }
        members.add(member);
        indexMember(member, owner);
    }

    /**
     * Removes a member from their team. When the owner leaves, ownership
     * passes deterministically to the member with the lowest UUID. An empty
     * team is removed entirely.
     *
     * @return true if the member was part of a team
     */
    public boolean removeMember(UUID member) {
        UUID owner = ownerByMember.get(member);
        if (owner == null) {
            return false;
        }
        Set<UUID> members = teamsByOwner.get(owner);
        if (members != null) {
            members.remove(member);
        }
        ownerByMember.remove(member);
        if (members == null || members.isEmpty()) {
            teamsByOwner.remove(owner);
        } else if (member.equals(owner)) {
            // Deterministic successor: lowest UUID, stable across restarts
            UUID successor = members.stream().min(UUID::compareTo).orElse(null);
            if (successor != null) {
                transferOwnership(owner, successor);
            }
        }
        return true;
    }

    /**
     * Rekeys an existing team to a new owner without changing its members.
     */
    public void transferOwnership(UUID oldOwner, UUID newOwner) {
        Set<UUID> members = teamsByOwner.remove(oldOwner);
        if (members == null || newOwner == null) {
            return;
        }
        members.remove(oldOwner);
        members.add(newOwner);
        teamsByOwner.put(newOwner, members);
        for (UUID member : members) {
            ownerByMember.put(member, newOwner);
        }
    }

    /**
     * Returns the owner of the team containing the member,
     * or null when the member has no team.
     */
    public UUID findOwner(UUID member) {
        return ownerByMember.get(member);
    }

    /**
     * Returns the member set of the team owned by the given player,
     * or null when there is no such team.
     */
    public Set<UUID> membersOf(UUID owner) {
        return teamsByOwner.get(owner);
    }

    /**
     * Read-only view of all teams for persistence.
     */
    public Set<Map.Entry<UUID, Set<UUID>>> entries() {
        return Collections.unmodifiableSet(teamsByOwner.entrySet());
    }

    private void indexMember(UUID member, UUID owner) {
        UUID previousOwner = ownerByMember.put(member, owner);
        if (previousOwner != null && !previousOwner.equals(owner)) {
            Set<UUID> previousMembers = teamsByOwner.get(previousOwner);
            if (previousMembers != null) {
                previousMembers.remove(member);
                if (previousMembers.isEmpty()) {
                    teamsByOwner.remove(previousOwner);
                }
            }
        }
    }
}
