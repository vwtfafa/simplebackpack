package org.vwtfafa.backpack;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists the {@link TeamRegistry} to a YAML file.
 */
final class TeamStorage {
    private final File file;
    private final Logger logger;

    TeamStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    void save(TeamRegistry registry) {
        YamlConfiguration config = new YamlConfiguration();
        List<String> owners = new ArrayList<>();
        for (Map.Entry<UUID, Set<UUID>> entry : registry.entries()) {
            owners.add(entry.getKey().toString());
            List<String> members = new ArrayList<>();
            for (UUID member : entry.getValue()) {
                members.add(member.toString());
            }
            config.set("teams." + entry.getKey(), members);
        }
        config.set("teams.owners", owners);
        try {
            config.save(file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save teams", e);
        }
    }

    void load(TeamRegistry registry) {
        registry.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (String ownerKey : config.getStringList("teams.owners")) {
            try {
                UUID owner = UUID.fromString(ownerKey);
                Set<UUID> members = new HashSet<>();
                for (String memberKey : config.getStringList("teams." + ownerKey)) {
                    members.add(UUID.fromString(memberKey));
                }
                registry.createTeam(owner, members);
            } catch (IllegalArgumentException ignored) {
                logger.warning("Ignoring invalid team entry: " + ownerKey);
            }
        }
    }
}
