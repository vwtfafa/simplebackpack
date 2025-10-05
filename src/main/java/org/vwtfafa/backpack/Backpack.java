package org.vwtfafa.backpack;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;

public final class Backpack extends JavaPlugin implements Listener {
    private BackpackManager backpackManager;
    private String language;
    private final Map<UUID, Set<UUID>> teams = new HashMap<>();
    private final Set<UUID> firstJoinPlayers = new HashSet<>();
    private int teamMaxSize;
    private boolean teamEnabled;
    private boolean allowInCreative;
    private boolean autoSaveOnQuit;
    private boolean keepOnDeath;
    private boolean guiConfigurable;
    private boolean messagesEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        language = config.getString("language", "en");
        String backpackName = config.getString("backpack.name", "§bSimple Backpack");
        int backpackSize = config.getInt("backpack.size", 27);
        allowInCreative = config.getBoolean("backpack.allow-in-creative", false);
        autoSaveOnQuit = config.getBoolean("backpack.auto-save-on-quit", true);
        keepOnDeath = config.getBoolean("backpack.keep-on-death", true);
        guiConfigurable = config.getBoolean("backpack.gui-configurable", true);
        messagesEnabled = config.getBoolean("messages-enabled", true);
        teamEnabled = config.getBoolean("team.enabled", true);
        teamMaxSize = config.getInt("team.max-size", 5);
        backpackManager = new BackpackManager(this, backpackName, backpackSize, teams, teamEnabled);
        if (getCommand("backpack") != null) getCommand("backpack").setExecutor(this);
        if (getCommand("invite") != null) getCommand("invite").setExecutor(this);
        if (getCommand("team") != null) getCommand("team").setExecutor(this);
        if (getCommand("backpackconfig") != null) getCommand("backpackconfig").setExecutor(this);
    }

    @Override
    public void onDisable() {
        backpackManager.saveAllBackpacks();
    }

    private String getMessage(String key) {
        return getConfig().getString("messages." + language + "." + key,
                getConfig().getString("messages.en." + key, ""));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (messagesEnabled) sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "backpack":
            case "bp":
                if (!player.hasPermission("simplebackpack.use")) {
                    if (messagesEnabled) player.sendMessage(getMessage("no-permission"));
                    return true;
                }
                if (!allowInCreative && player.getGameMode().name().equalsIgnoreCase("CREATIVE")) {
                    if (messagesEnabled) player.sendMessage(getMessage("creative-not-allowed"));
                    return true;
                }
                backpackManager.openBackpack(player);
                if (messagesEnabled) player.sendMessage(getMessage("open-success"));
                return true;
            case "invite":
                if (!teamEnabled) return true;
                if (args.length != 1) return true;
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null || target == player) return true;
                Set<UUID> team = teams.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
                if (team.size() >= teamMaxSize) {
                    if (messagesEnabled) player.sendMessage(getMessage("team-full"));
                    return true;
                }
                team.add(target.getUniqueId());
                if (messagesEnabled) {
                    player.sendMessage(getMessage("team-invite").replace("{player}", target.getName()));
                    target.sendMessage(getMessage("team-joined"));
                }
                return true;
            case "team":
                if (!teamEnabled) return true;
                // Show team members
                Set<UUID> members = teams.getOrDefault(player.getUniqueId(), new HashSet<>());
                StringBuilder sb = new StringBuilder("§aTeam: ");
                for (UUID uuid : members) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) sb.append(p.getName()).append(" ");
                }
                player.sendMessage(sb.toString());
                return true;
            case "backpackconfig":
                if (!guiConfigurable) return true;
                backpackManager.openConfigGUI(player);
                return true;
        }
        return false;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (autoSaveOnQuit) {
            backpackManager.saveBackpack(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!firstJoinPlayers.contains(player.getUniqueId())) {
            firstJoinPlayers.add(player.getUniqueId());
            if (messagesEnabled) player.sendMessage(getMessage("first-join"));
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!keepOnDeath) {
            backpackManager.clearBackpack(event.getEntity());
        }
    }
}
