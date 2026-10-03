package org.vwtfafa.backpack;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
        // Write through a temp file with an atomic move so a crash mid-save
        // cannot corrupt teams.yml; keep the previous file as a backup.
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            config.save(temporary);
            if (file.exists()) {
                Files.copy(file.toPath(),
                        new File(file.getParentFile(), file.getName() + ".bak").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
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
            UUID owner;
            try {
                owner = UUID.fromString(ownerKey);
            } catch (IllegalArgumentException ignored) {
                logger.warning("Ignoring invalid team entry: " + ownerKey);
                continue;
            }
            Set<UUID> members = new HashSet<>();
            for (String memberKey : config.getStringList("teams." + ownerKey)) {
                try {
                    members.add(UUID.fromString(memberKey));
                } catch (IllegalArgumentException ignored) {
                    logger.warning("Ignoring invalid member '" + memberKey
                            + "' of team " + ownerKey);
                }
            }
            registry.createTeam(owner, members);
        }
    }
}
