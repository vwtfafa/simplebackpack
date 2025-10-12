package org.vwtfafa.backpack;

import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Backpack extends JavaPlugin {
    private BackpackManager backpackManager;
    private Map<UUID, Set<UUID>> teams = new HashMap<>();
    private Locale locale = Locale.ENGLISH;
    private boolean messagesEnabled = true;
    private boolean classicMode = false;
    private boolean teamEnabled = true;
    private boolean adminEnabled = true;
    private boolean showTeamCommands = true;
    private boolean showAdminCommands = true;
    private boolean liveConfigReload = true;
    private boolean keepContentsOnDeath = true;
    private boolean backpacksEnabled = true;
    private boolean statsEnabled = true;

    private final Set<String> registeredDynamicCommands = new HashSet<>();

    @Override
    public void onEnable() {
        getLogger().info(locale == Locale.GERMAN ? "SimpleBackpack wurde aktiviert!" : "SimpleBackpack enabled!");
        saveDefaultConfig();
        reloadConfig();
        loadConfigOptions();
        if (statsEnabled) {
            getLogger().info(locale == Locale.GERMAN ? "Statistiken werden an das Dashboard gesendet (siehe config.yml: stats-enabled)." : "Stats are being sent to the dashboard (see config.yml: stats-enabled). ");
            new StatsReporter(this).start();
        } else {
            getLogger().info(locale == Locale.GERMAN ? "Statistiken sind deaktiviert (siehe config.yml: stats-enabled)." : "Stats reporting is disabled (see config.yml: stats-enabled). ");
        }
        backpackManager = new BackpackManager(this, getBackpackName(), getBackpackSize(), teams, teamEnabled, classicMode, adminEnabled, liveConfigReload, showTeamCommands, showAdminCommands, keepContentsOnDeath, locale);
        registerCommands();
        getServer().getPluginManager().registerEvents(backpackManager, this);
    }

    @Override
    public void onDisable() {
        if (backpackManager != null) backpackManager.saveAllBackpacks();
        unregisterDynamicCommands();
    }

    private void unregisterDynamicCommands() {
        for (String cmd : registeredDynamicCommands) {
            try {
                getCommand(cmd).setExecutor(null);
            } catch (Exception ignored) {}
        }
        registeredDynamicCommands.clear();
    }

    private void loadConfigOptions() {
        FileConfiguration config = getConfig();
        String lang = config.getString("language", "en");
        if (lang.equalsIgnoreCase("de")) locale = Locale.GERMAN;
        classicMode = config.getBoolean("classic-mode", false);
        teamEnabled = config.getBoolean("team.enabled", true);
        adminEnabled = config.getBoolean("admin.enabled", true);
        showTeamCommands = config.getBoolean("show-team-commands", true);
        showAdminCommands = config.getBoolean("show-admin-commands", true);
        liveConfigReload = config.getBoolean("live-config-reload", true);
        keepContentsOnDeath = config.getBoolean("backpack.keep-on-death", true);
        messagesEnabled = config.getBoolean("messages-enabled", true);
        backpacksEnabled = config.getBoolean("backpacks-enabled", true);
        statsEnabled = config.getBoolean("stats-enabled", true);
    }

    private String getBackpackName() {
        return getConfig().getString("backpack.name", "§bSimple Backpack");
    }
    private int getBackpackSize() {
        return getConfig().getInt("backpack.size", 27);
    }

    private void registerCommands() {
        unregisterDynamicCommands();
        PluginCommand backpackCmd = getCommand("backpack");
        if (backpackCmd != null) {
            backpackCmd.setExecutor((sender, command, label, args) -> {
                if (!backpacksEnabled) {
                    sender.sendMessage(getMessage("backpacks-disabled"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(getMessage("no-permission"));
                    return true;
                }
                Player player = (Player) sender;
                if (messagesEnabled) player.sendMessage(getMessage("open-success"));
                backpackManager.openBackpack(player);
                return true;
            });
            registeredDynamicCommands.add("backpack");
        }
        // /bp als Alias für /backpack
        PluginCommand bpCmd = getCommand("bp");
        if (bpCmd != null) {
            bpCmd.setExecutor((sender, command, label, args) -> {
                if (!backpacksEnabled) {
                    sender.sendMessage(getMessage("backpacks-disabled"));
                    return true;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(getMessage("no-permission"));
                    return true;
                }
                Player player = (Player) sender;
                if (messagesEnabled) player.sendMessage(getMessage("open-success"));
                backpackManager.openBackpack(player);
                return true;
            });
            registeredDynamicCommands.add("bp");
        }
        PluginCommand configCmd = getCommand("backpackconfig");
        if (configCmd != null) {
            configCmd.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(getMessage("no-permission"));
                    return true;
                }
                Player player = (Player) sender;
                backpackManager.openConfigGUI(player);
                return true;
            });
            registeredDynamicCommands.add("backpackconfig");
        }
        PluginCommand reloadCmd = getCommand("backpackreload");
        if (reloadCmd != null) {
            reloadCmd.setExecutor((sender, command, label, args) -> {
                reloadConfig();
                loadConfigOptions();
                backpackManager.setConfig(getBackpackName(), getBackpackSize(), teamEnabled, classicMode, adminEnabled, liveConfigReload, showTeamCommands, showAdminCommands, keepContentsOnDeath, locale);
                registerCommands();
                if (messagesEnabled) sender.sendMessage(getMessage("reload-success"));
                return true;
            });
            registeredDynamicCommands.add("backpackreload");
        }
        if (teamEnabled && showTeamCommands && !classicMode) {
            PluginCommand inviteCmd = getCommand("invite");
            if (inviteCmd != null) {
                inviteCmd.setExecutor((sender, command, label, args) -> {
                    // Team Invite Logic
                    sender.sendMessage(getMessage(locale == Locale.GERMAN ? "team-invite" : "team-invite"));
                    return true;
                });
                registeredDynamicCommands.add("invite");
            }
            PluginCommand teamCmd = getCommand("team");
            if (teamCmd != null) {
                teamCmd.setExecutor((sender, command, label, args) -> {
                    // Team Info Logic
                    sender.sendMessage(getMessage(locale == Locale.GERMAN ? "team-share" : "team-share"));
                    return true;
                });
                registeredDynamicCommands.add("team");
            }
            PluginCommand leaveCmd = getCommand("leave");
            if (leaveCmd != null) {
                leaveCmd.setExecutor((sender, command, label, args) -> {
                    // Team Leave Logic
                    sender.sendMessage(getMessage(locale == Locale.GERMAN ? "team-leave" : "team-leave"));
                    return true;
                });
                registeredDynamicCommands.add("leave");
            }
        }
        if (adminEnabled && showAdminCommands) {
            PluginCommand adminCmd = getCommand("backpackadmin");
            if (adminCmd != null) {
                adminCmd.setExecutor((sender, command, label, args) -> {
                    // Admin Logic (enable/disable, give items, etc)
                    sender.sendMessage(getMessage(locale == Locale.GERMAN ? "admin-enabled" : "admin-enabled"));
                    return true;
                });
                registeredDynamicCommands.add("backpackadmin");
            }
        }
    }

    private String getMessage(String key) {
        if (!messagesEnabled) return "";
        String lang = locale == Locale.GERMAN ? "de" : "en";
        String msg = getConfig().getString("messages." + lang + "." + key, "");
        if (msg == null || msg.isEmpty()) {
            // Fallback auf Englisch, falls Übersetzung fehlt
            msg = getConfig().getString("messages.en." + key, "");
        }
        return msg != null ? msg : "";
    }
}
