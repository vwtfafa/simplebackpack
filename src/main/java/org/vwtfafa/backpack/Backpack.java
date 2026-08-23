package org.vwtfafa.backpack;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.NamespacedKey;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private TeamStorage teamStorage;
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
        teamStorage = new TeamStorage(new File(getDataFolder(), "teams.yml"), getLogger());
        teamStorage.load(teamRegistry);
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
        teamStorage.save(teamRegistry);
    }
}
