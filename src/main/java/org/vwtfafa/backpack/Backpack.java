package org.vwtfafa.backpack;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Backpack extends JavaPlugin implements Listener {
    static final long INVITE_EXPIRY_MILLIS = 5L * 60L * 1000L;
    private static final int BSTATS_PLUGIN_ID = 32528;

    private BackpackManager manager;
    private AdminGUI adminGui;
    private Messages messages;
    private PluginSettings settings;
    private final TeamRegistry teamRegistry = new TeamRegistry();
    private final Map<UUID, TeamInvite> pendingInvites = new HashMap<>();
    private File teamsFile;
    private NamespacedKey firstJoinKey;

    @Override
    public void onDisable() {
        if (manager != null) manager.saveAllBackpacks();
        saveTeams();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        firstJoinKey = new NamespacedKey(this, "first-join-message");
        teamsFile = new File(getDataFolder(), "teams.yml");
        loadTeams();
        settings = PluginSettings.load(this);
        new Metrics(this, BSTATS_PLUGIN_ID);
        getLogger().info("bStats metrics enabled (ID: " + BSTATS_PLUGIN_ID + ")");
        manager = new BackpackManager(this, messages, settings.backpackName(), settings.backpackSize(),
                teamRegistry, settings.teamEnabled(), settings.locale());
        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();
        if (settings.adminEnabled() && settings.adminGuiEnabled()) {
            adminGui = new AdminGUI(manager, messages);
        }
        // Initialize update checker
        new UpdateChecker(this).checkForUpdates();
    }

    /**
     * Registers all commands through Paper's Brigadier lifecycle API.
     */
    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands registrar = event.registrar();
            registrar.register(new BackpackCommand(this).node(), "Opens your personal backpack", List.of("bp"));
            registrar.register(new BackpackConfigCommand(this).node(),
                    "Open the backpack configuration GUI", List.of());
            registrar.register(new BackpackReloadCommand(this).node(),
                    "Reloads the SimpleBackpack config", List.of());
            if (settings.teamEnabled() && settings.showTeamCommands() && !settings.classicMode()) {
                registrar.register(new InviteCommand(this).node(), "Invite a player to your team", List.of());
                registrar.register(new TeamCommand(this).node(), "Show your team members or accept an invite",
                        List.of());
                registrar.register(new LeaveCommand(this).node(), "Leave your current team", List.of());
            }
            if (settings.adminEnabled() && settings.showAdminCommands()) {
                registrar.register(new BackpackAdminCommand(this).node(),
                        "Admin commands for SimpleBackpack", List.of());
            }
            registrar.register(new BackpackShareCommand(this).node(), "Temporarily share your backpack", List.of());
        });
    }

    private void loadConfigOptions() {
        settings = PluginSettings.load(this);
    }

    /**
     * Reloads the configuration, localized messages and manager settings.
     */
    void reloadConfiguration() {
        reloadConfig();
        messages.reload();
        loadConfigOptions();
        manager.setConfig(settings.backpackName(), settings.backpackSize(),
                settings.teamEnabled(), settings.locale());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!getConfig().getBoolean("backpack.first-join-message", true)) {
            return;
        }
        Player player = event.getPlayer();
        // Greet genuinely new players exactly once; the PDC flag also covers
        // players who joined before this option existed
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        if (player.hasPlayedBefore() || pdc.has(firstJoinKey, PersistentDataType.BYTE)) {
            return;
        }
        pdc.set(firstJoinKey, PersistentDataType.BYTE, (byte) 1);
        messages.send(player, "first-join");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Drop the quitting player's pending invite so it cannot be accepted later
        pendingInvites.remove(event.getPlayer().getUniqueId());
        if (settings.autoSaveOnQuit() && manager != null) {
            manager.saveBackpackAsync(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!settings.keepContentsOnDeath() && manager != null) {
            manager.clearBackpack(event.getEntity());
        }
    }

    Messages messages() {
        return messages;
    }

    BackpackManager manager() {
        return manager;
    }

    AdminGUI adminGui() {
        return adminGui;
    }

    TeamRegistry teamRegistry() {
        return teamRegistry;
    }

    Map<UUID, TeamInvite> pendingInvites() {
        return pendingInvites;
    }

    int teamMaxSize() {
        return settings.teamMaxSize();
    }

    boolean backpacksEnabled() {
        return settings.backpacksEnabled();
    }

    /**
     * Flips the global enable flag live and persists it to the config.
     */
    void setBackpacksEnabled(boolean enabled) {
        settings.setBackpacksEnabled(enabled);
        getConfig().set("backpacks-enabled", enabled);
        saveConfig();
    }

    boolean allowInCreative() {
        return settings.allowInCreative();
    }

    List<String> disabledWorlds() {
        return settings.disabledWorlds();
    }

    boolean guiConfigurable() {
        return settings.guiConfigurable();
    }

    boolean sharingEnabled() {
        return settings.sharingEnabled();
    }

    boolean liveConfigReload() {
        return settings.liveConfigReload();
    }

    void saveTeams() {
        if (teamsFile == null) return;
        YamlConfiguration config = new YamlConfiguration();
        List<String> owners = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Set<UUID>> entry : teamRegistry.entries()) {
            owners.add(entry.getKey().toString());
            List<String> members = new java.util.ArrayList<>();
            for (UUID member : entry.getValue()) members.add(member.toString());
            config.set("teams." + entry.getKey(), members);
        }
        config.set("teams.owners", owners);
        try {
            config.save(teamsFile);
        } catch (IOException e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to save teams", e);
        }
    }

    private void loadTeams() {
        teamRegistry.clear();
        if (teamsFile == null || !teamsFile.exists()) return;
        YamlConfiguration config = YamlConfiguration.loadConfiguration(teamsFile);
        for (String ownerKey : config.getStringList("teams.owners")) {
            try {
                UUID owner = UUID.fromString(ownerKey);
                Set<UUID> members = new HashSet<>();
                for (String memberKey : config.getStringList("teams." + ownerKey)) {
                    members.add(UUID.fromString(memberKey));
                }
                teamRegistry.createTeam(owner, members);
            } catch (IllegalArgumentException ignored) {
                getLogger().warning("Ignoring invalid team entry: " + ownerKey);
            }
        }
    }
}
