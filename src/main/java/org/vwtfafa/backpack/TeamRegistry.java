package org.vwtfafa.backpack;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory team storage: owner-to-members plus an O(1) member-to-owner
 * reverse index so ownership lookups never need to scan all teams.
 *
 * Thread-safety: the maps and member sets are concurrent, compound mutations
 * are synchronized and readers receive immutable snapshots, so iteration
 * (team info, persistence) can never hit a ConcurrentModificationException.
 */
public class TeamRegistry {
    private final Map<UUID, Set<UUID>> teamsByOwner = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> ownerByMember = new ConcurrentHashMap<>();

    public void clear() {
        teamsByOwner.clear();
        ownerByMember.clear();
    }

    /**
     * Creates a team owned by the given player. Members that were tracked in
     * another team are moved out of it first.
     */
    public synchronized void createTeam(UUID owner, Set<UUID> members) {
        Set<UUID> teamMembers = ConcurrentHashMap.newKeySet();
        teamMembers.addAll(members);
        teamMembers.add(owner);
        teamsByOwner.put(owner, teamMembers);
        for (UUID member : teamMembers) {
            indexMember(member, owner);
        }
    }

    /**
     * Adds a member to the team owned by the given owner.
     */
    public synchronized void addMember(UUID owner, UUID member) {
        Set<UUID> members = teamsByOwner.get(owner);
        if (members == null) {
            return;
        }
        members.add(member);
        indexMember(member, owner);
    }

    /**
     * Adds a member only when the team exists and still has room; the
     * size check and the mutation are one atomic step so parallel accepts
     * can never push a team over its limit.
     *
     * @return true when the member was added
     */
    public synchronized boolean tryAddMember(UUID owner, UUID member, int maxSize) {
        Set<UUID> members = teamsByOwner.get(owner);
        if (members == null || members.size() >= maxSize) {
            return false;
        }
        members.add(member);
        indexMember(member, owner);
        return true;
    }

    /**
     * Removes a member from their team. When the owner leaves, ownership
     * passes deterministically to the member with the lowest UUID. An empty
     * team is removed entirely.
     *
     * @return true if the member was part of a team
     */
    public synchronized boolean removeMember(UUID member) {
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
    public synchronized void transferOwnership(UUID oldOwner, UUID newOwner) {
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
     * Returns an immutable snapshot of the member set of the team owned by
     * the given player, or an empty set when there is no such team.
     */
    public Set<UUID> membersOf(UUID owner) {
        Set<UUID> members = teamsByOwner.get(owner);
        return members == null ? Set.of() : Set.copyOf(members);
    }

    /**
     * Read-only snapshot of all teams for persistence.
     */
    public Set<Map.Entry<UUID, Set<UUID>>> entries() {
        Set<Map.Entry<UUID, Set<UUID>>> snapshot = new HashSet<>();
        for (Map.Entry<UUID, Set<UUID>> entry : teamsByOwner.entrySet()) {
            snapshot.add(Map.entry(entry.getKey(), Set.copyOf(entry.getValue())));
        }
        return Collections.unmodifiableSet(snapshot);
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
