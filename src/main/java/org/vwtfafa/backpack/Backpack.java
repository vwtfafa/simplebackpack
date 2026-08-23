package org.vwtfafa.backpack;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Backpack extends JavaPlugin implements Listener {
    static final long INVITE_EXPIRY_MILLIS = 5L * 60L * 1000L;
    private static final int BSTATS_PLUGIN_ID = 32528;

    private BackpackManager manager;
    private AdminGUI adminGui;
    private Messages messages;
    private final TeamRegistry teamRegistry = new TeamRegistry();
    private final Map<UUID, TeamInvite> pendingInvites = new HashMap<>();
    private File teamsFile;

    // Configuration-driven state
    private Locale locale = Locale.ENGLISH;
    private boolean classicMode = false;
    private boolean teamEnabled = true;
    private boolean adminEnabled = true;
    private boolean adminGuiEnabled = true;
    private boolean showTeamCommands = true;
    private boolean showAdminCommands = true;
    private boolean liveConfigReload = true;
    private boolean keepContentsOnDeath = true;
    private boolean autoSaveOnQuit = true;
    private boolean backpacksEnabled = true;
    private boolean allowInCreative = false;
    private boolean guiConfigurable = true;
    private boolean sharingEnabled = true;
    private int teamMaxSize = 5;

    @Override
    public void onDisable() {
        if (manager != null) manager.saveAllBackpacks();
        saveTeams();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        teamsFile = new File(getDataFolder(), "teams.yml");
        loadTeams();
        loadConfigOptions();
        new Metrics(this, BSTATS_PLUGIN_ID);
        getLogger().info("bStats metrics enabled (ID: " + BSTATS_PLUGIN_ID + ")");
        manager = new BackpackManager(this, messages, getBackpackName(), getBackpackSize(), teamRegistry, teamEnabled, locale);
        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();
        if (adminEnabled && adminGuiEnabled) adminGui = new AdminGUI(manager);
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
            if (teamEnabled && showTeamCommands && !classicMode) {
                registrar.register(new InviteCommand(this).node(), "Invite a player to your team", List.of());
                registrar.register(new TeamCommand(this).node(), "Show your team members or accept an invite",
                        List.of());
                registrar.register(new LeaveCommand(this).node(), "Leave your current team", List.of());
            }
            if (adminEnabled && showAdminCommands) {
                registrar.register(new BackpackAdminCommand(this).node(),
                        "Admin commands for SimpleBackpack", List.of());
            }
            registrar.register(new BackpackShareCommand(this).node(), "Temporarily share your backpack", List.of());
        });
    }

    private void loadConfigOptions() {
        FileConfiguration config = getConfig();
        String lang = config.getString("language", "en");
        if (lang.equalsIgnoreCase("de")) locale = Locale.GERMAN;
        classicMode = config.getBoolean("classic-mode", false);
        teamEnabled = config.getBoolean("team.enabled", true);
        adminEnabled = config.getBoolean("admin.enabled", true);
        adminGuiEnabled = config.getBoolean("admin.enable-gui", true);
        showTeamCommands = config.getBoolean("show-team-commands", true);
        showAdminCommands = config.getBoolean("show-admin-commands", true);
        liveConfigReload = config.getBoolean("live-config-reload", true);
        keepContentsOnDeath = config.getBoolean("backpack.keep-on-death", true);
        autoSaveOnQuit = config.getBoolean("backpack.auto-save-on-quit", true);
        backpacksEnabled = config.getBoolean("backpacks-enabled", true);
        allowInCreative = config.getBoolean("backpack.allow-in-creative", false);
        guiConfigurable = config.getBoolean("backpack.gui-configurable", true);
        sharingEnabled = config.getBoolean("enable-sharing", true);
        teamMaxSize = Math.max(2, config.getInt("team.max-size", 5));
    }

    private String getBackpackName() {
        return getConfig().getString("backpack.name", "<aqua>Simple Backpack");
    }

    private int getBackpackSize() {
        return getConfig().getInt("backpack.size", 27);
    }

    /**
     * Reloads the configuration, localized messages and manager settings.
     */
    void reloadConfiguration() {
        reloadConfig();
        messages.reload();
        loadConfigOptions();
        manager.setConfig(getBackpackName(), getBackpackSize(), teamEnabled, locale);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Drop the quitting player's pending invite so it cannot be accepted later
        pendingInvites.remove(event.getPlayer().getUniqueId());
        if (autoSaveOnQuit && manager != null) {
            manager.saveBackpackAsync(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!keepContentsOnDeath && manager != null) {
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
        return teamMaxSize;
    }

    boolean backpacksEnabled() {
        return backpacksEnabled;
    }

    boolean allowInCreative() {
        return allowInCreative;
    }

    boolean guiConfigurable() {
        return guiConfigurable;
    }

    boolean sharingEnabled() {
        return sharingEnabled;
    }

    boolean liveConfigReload() {
        return liveConfigReload;
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
