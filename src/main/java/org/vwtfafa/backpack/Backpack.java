package org.vwtfafa.backpack;

import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.OfflinePlayer;

public class Backpack extends JavaPlugin {
    private BackpackManager backpackManager;
    private AdminGUI adminGui;
    private Map<UUID, Set<UUID>> teams = new HashMap<>();
    private Map<UUID, UUID> pendingInvites = new HashMap<>();
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
        // register commands and admin UI
        registerCommands();
        if (adminEnabled) adminGui = new AdminGUI(backpackManager);
        // Initialize update checker
        new UpdateChecker(this).checkForUpdates();
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
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(getMessage("no-permission"));
                        return true;
                    }
                    Player player = (Player) sender;
                    if (args.length < 1) {
                        player.sendMessage(getMessage("invite-usage"));
                        return true;
                    }
                    Player target = getServer().getPlayer(args[0]);
                    if (target == null) {
                        player.sendMessage(getMessage("share-player-offline"));
                        return true;
                    }
                    // Check if already in a team
                    if (teams.containsKey(player.getUniqueId()) && !teams.get(player.getUniqueId()).isEmpty()) {
                        player.sendMessage(getMessage("already-in-team"));
                        return true;
                    }
                    // Check if target already in a team
                    if (teams.containsKey(target.getUniqueId()) && !teams.get(target.getUniqueId()).isEmpty()) {
                        player.sendMessage(getMessage("target-in-team"));
                        return true;
                    }
                    pendingInvites.put(target.getUniqueId(), player.getUniqueId());
                    String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
                    String playerName = player.getName() != null ? player.getName() : player.getUniqueId().toString();
                    player.sendMessage(getMessage("team-invite").replace("{player}", targetName));
                    target.sendMessage(getMessage("team-invite-recv").replace("{player}", playerName));
                    return true;
                });
                registeredDynamicCommands.add("invite");
            }
            PluginCommand teamCmd = getCommand("team");
            if (teamCmd != null) {
                teamCmd.setExecutor((sender, command, label, args) -> {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(getMessage("no-permission"));
                        return true;
                    }
                    Player player = (Player) sender;
                    // Check if team accept is requested
                    if (args.length > 0 && args[0].equalsIgnoreCase("accept")) {
                        UUID targetId = player.getUniqueId();
                        if (pendingInvites.containsKey(targetId)) {
                            UUID inviterId = pendingInvites.remove(targetId);
                            // Create team with inviter as owner
                            Set<UUID> newTeam = new HashSet<>();
                            newTeam.add(inviterId);
                            newTeam.add(targetId);
                            teams.put(inviterId, newTeam);
                            OfflinePlayer inviterOffline = getServer().getOfflinePlayer(inviterId);
                            String inviterName = (inviterOffline != null && inviterOffline.getName() != null) ? inviterOffline.getName() : inviterId.toString();
                            player.sendMessage(getMessage("team-joined").replace("{player}", inviterName));
                            // Notify inviter if online
                            Player inviter = getServer().getPlayer(inviterId);
                            if (inviter != null && messagesEnabled) {
                                inviter.sendMessage(getMessage("team-joined").replace("{player}", player.getName()));
                            }
                        } else {
                            // Show team members or not in team
                            showTeamInfo(player);
                        }
                        return true;
                    }
                    // Default: show team info
                    showTeamInfo(player);
                    return true;
                });
                registeredDynamicCommands.add("team");
            }
            PluginCommand leaveCmd = getCommand("leave");
            if (leaveCmd != null) {
                leaveCmd.setExecutor((sender, command, label, args) -> {
                    if (!(sender instanceof Player)) {
                        sender.sendMessage(getMessage("no-permission"));
                        return true;
                    }
                    Player player = (Player) sender;
                    UUID uuid = player.getUniqueId();
                    // Check if player is in a team (as owner or member)
                    boolean isInTeam = false;
                    UUID teamOwner = null;
                    for (Map.Entry<UUID, Set<UUID>> entry : teams.entrySet()) {
                        if (entry.getValue().contains(uuid)) {
                            isInTeam = true;
                            teamOwner = entry.getKey();
                            break;
                        }
                    }
                    if (isInTeam) {
                        // Remove player from team
                        if (teams.containsKey(teamOwner)) {
                            teams.get(teamOwner).remove(uuid);
                            // If team is empty after removal, remove the team entirely
                            if (teams.get(teamOwner).isEmpty()) {
                                teams.remove(teamOwner);
                            }
                        }
                        player.sendMessage(getMessage("team-leave"));
                    } else {
                        player.sendMessage(getMessage("not-in-team"));
                    }
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
                    player.sendMessage(getMessage("admin-enabled"));
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
                    player.sendMessage(getMessage("share-usage"));
                    return true;
                }
                Player target = getServer().getPlayer(args[0]);
                if (target == null) {
                    player.sendMessage(getMessage("share-player-offline"));
                    return true;
                }
                long duration = 60L * 60L * 1000L; // default 1 hour
                if (args.length >= 2) {
                    try { duration = Long.parseLong(args[1]) * 60L * 1000L; } catch (NumberFormatException ignored) {}
                }
                backpackManager.shareBackpack(player.getUniqueId(), target.getUniqueId(), duration);
                player.sendMessage(getMessage("share-success").replace("{player}", target.getName()));
                target.sendMessage(getMessage("share-received").replace("{player}", player.getName()));
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

    private void showTeamInfo(Player player) {
        UUID uuid = player.getUniqueId();
        // Check if player is in a team (as owner or member)
        UUID teamOwner = null;
        Set<UUID> teamMembers = null;
        for (Map.Entry<UUID, Set<UUID>> entry : teams.entrySet()) {
            if (entry.getValue().contains(uuid)) {
                teamOwner = entry.getKey();
                teamMembers = entry.getValue();
                break;
            }
        }
        if (teamMembers == null || teamMembers.isEmpty()) {
            // Check if there's a pending invite
            if (pendingInvites.containsKey(uuid)) {
                UUID inviter = pendingInvites.get(uuid);
                String inviterName = getServer().getOfflinePlayer(inviter).getName();
                player.sendMessage(getMessage("team-pending").replace("{player}", inviterName));
            } else {
                player.sendMessage(getMessage("not-in-team"));
            }
            return;
        }
        // Build message
        StringBuilder sb = new StringBuilder();
        for (UUID member : teamMembers) {
            Player memberPlayer = getServer().getPlayer(member);
            String name = memberPlayer != null ? memberPlayer.getName() : member.toString();
            if (member.equals(teamOwner)) {
                name += " (L)";
            }
            sb.append(name).append(", ");
        }
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 2); // remove last comma and space
        }
        player.sendMessage(getMessage("team-members").replace("{members}", sb.toString()));
    }
}