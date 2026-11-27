package org.vwtfafa.backpack;

import org.bukkit.configuration.InvalidConfigurationException;
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
    private static final int STORAGE_VERSION = 1;

    private final File file;
    private final Logger logger;

    TeamStorage(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
    }

    void save(TeamRegistry registry) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("version", STORAGE_VERSION);
        List<String> owners = new ArrayList<>();
        for (Map.Entry<UUID, Set<UUID>> entry : registry.entries()) {
            owners.add(entry.getKey().toString());
            config.set("teams." + entry.getKey(),
                    entry.getValue().stream().map(UUID::toString).toList());
        }
        config.set("teams.owners", owners);
        // Write through a temp file with an atomic move so a crash mid-save
        // cannot corrupt teams.yml; keep the previous file as a backup, but
        // never let a corrupt file replace the last good backup.
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            config.save(temporary);
            keepOrQuarantinePrevious();
            moveAtomically(temporary, file);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save teams", e);
        }
    }

    /**
     * Backs up the current teams.yml before it is replaced; a file that
     * cannot be parsed is moved aside as {@code .corrupt-<ts>} instead so it
     * never overwrites the last good backup.
     */
    private void keepOrQuarantinePrevious() {
        if (!file.exists()) {
            return;
        }
        if (tryLoad(file) != null) {
            try {
                Files.copy(file.toPath(), backupFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to back up teams.yml", e);
            }
            return;
        }
        File quarantined = new File(file.getParentFile(),
                file.getName() + ".corrupt-" + System.currentTimeMillis());
        try {
            Files.move(file.toPath(), quarantined.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.warning("Preserved unreadable teams.yml as " + quarantined.getName() + ".");
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to preserve unreadable teams.yml", e);
        }
    }

    void load(TeamRegistry registry) {
        registry.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = tryLoad(file);
        if (config == null) {
            // An unreadable main file would silently drop every team; fall
            // back to the last good backup instead.
            config = tryLoad(backupFile());
            if (config != null) {
                logger.warning("teams.yml is unreadable; loaded teams from teams.yml.bak instead.");
            } else {
                logger.severe("teams.yml is unreadable and no usable backup exists; teams stay "
                        + "empty until the file is fixed (the file itself is kept for recovery).");
                return;
            }
        }
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
                    logger.warning("Ignoring invalid member '%s' of team %s".formatted(memberKey, ownerKey));
                }
            }
            registry.createTeam(owner, members);
        }
    }

    private YamlConfiguration tryLoad(File source) {
        if (!source.exists()) {
            return null;
        }
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.load(source);
            return config;
        } catch (IOException | InvalidConfigurationException e) {
            logger.log(Level.WARNING, "Failed to read " + source.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private File backupFile() {
        return new File(file.getParentFile(), file.getName() + ".bak");
    }

    private void moveAtomically(File source, File target) throws IOException {
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
