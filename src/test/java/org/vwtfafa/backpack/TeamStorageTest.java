package org.vwtfafa.backpack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

class TeamStorageTest {
    private static final Logger LOGGER = Logger.getLogger("TeamStorageTest");

    @Test
    void saveLoadRoundTrip(@TempDir Path dir) {
        File file = dir.resolve("teams.yml").toFile();
        TeamRegistry saved = new TeamRegistry();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        saved.createTeam(owner, Set.of(member));
        new TeamStorage(file, LOGGER).save(saved);

        TeamRegistry loaded = new TeamRegistry();
        new TeamStorage(file, LOGGER).load(loaded);

        assertEquals(owner, loaded.findOwner(member));
        assertEquals(Set.of(owner, member), loaded.membersOf(owner));
    }

    @Test
    void invalidEntriesAreSkippedIndividually(@TempDir Path dir) throws Exception {
        File file = dir.resolve("teams.yml").toFile();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        YamlConfiguration raw = new YamlConfiguration();
        raw.set("teams.owners", List.of(owner.toString(), "not-a-uuid"));
        raw.set("teams." + owner, List.of(member.toString(), "bogus-member"));
        raw.save(file);

        TeamRegistry loaded = new TeamRegistry();
        new TeamStorage(file, LOGGER).load(loaded);

        assertEquals(owner, loaded.findOwner(member));
        assertEquals(Set.of(owner, member), loaded.membersOf(owner));
    }

    @Test
    void missingFileLoadsEmpty(@TempDir Path dir) {
        TeamRegistry loaded = new TeamRegistry();
        new TeamStorage(dir.resolve("absent.yml").toFile(), LOGGER).load(loaded);

        assertNull(loaded.findOwner(UUID.randomUUID()));
    }
}
