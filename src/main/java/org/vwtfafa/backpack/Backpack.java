package org.vwtfafa.backpack;

import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Backpack extends JavaPlugin {
    private BackpackManager backpackManager;
    private AdminGUI adminGui;
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

    private final Set<String> registeredDynamicCommands = new HashSet<>();


    @Override
    public void onDisable() {
        if (backpackManager != null) backpackManager.saveAllBackpacks();
        unregisterDynamicCommands();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigOptions();
        new Metrics(this, 32528);
        backpackManager = new BackpackManager(this, getBackpackName(), getBackpackSize(), teams, teamEnabled, classicMode, adminEnabled, liveConfigReload, showTeamCommands, showAdminCommands, keepContentsOnDeath, locale);
        backpackManager.setConfig(getBackpackName(), getBackpackSize(), teamEnabled, classicMode, adminEnabled, liveConfigReload, showTeamCommands, showAdminCommands, keepContentsOnDeath, locale);
        // register commands and admin UI
        registerCommands();
        if (adminEnabled) adminGui = new AdminGUI(backpackManager);
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
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(getMessage("no-permission"));
                        return true;
                    }
                    Player player = (Player) sender;
                    if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
                        if (!player.hasPermission("simplebackpack.admin")) {
                            player.sendMessage(getMessage("no-permission"));
                            return true;
                        }
                        // open admin GUI
                        backpackManager.getPlugin().getServer().getScheduler().runTask(backpackManager.getPlugin(), () -> {
                            if (adminGui != null) adminGui.openAdminGUI(player);
                        });
                        return true;
                    }
                    player.sendMessage(getMessage(locale == Locale.GERMAN ? "admin-enabled" : "admin-enabled"));
                    return true;
                });
                registeredDynamicCommands.add("backpackadmin");
            }
        }

        PluginCommand shareCmd = getCommand("backpackshare");
        if (shareCmd != null) {
            shareCmd.setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(getMessage("no-permission"));
                    return true;
                }
                Player player = (Player) sender;
                if (args.length < 1) {
                    player.sendMessage("Usage: /backpackshare <player> [durationMinutes]");
                    return true;
                }
                Player target = getServer().getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage("Player not online");
                    return true;
                }
                long duration = 60L * 60L * 1000L; // default 1 hour
                if (args.length >= 2) {
                    try { duration = Long.parseLong(args[1]) * 60L * 1000L; } catch (NumberFormatException ignored) {}
                }
                backpackManager.shareBackpack(player.getUniqueId(), target.getUniqueId(), duration);
                player.sendMessage("Shared backpack with " + target.getName());
                target.sendMessage("You have been granted temporary access to " + player.getName() + "'s backpack");
                return true;
            });
            registeredDynamicCommands.add("backpackshare");
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
