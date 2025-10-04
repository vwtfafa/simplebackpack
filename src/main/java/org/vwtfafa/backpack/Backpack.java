package org.vwtfafa.backpack;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.List;

public final class Backpack extends JavaPlugin implements Listener {
    private BackpackManager backpackManager;
    private String backpackName;
    private List<String> allowedWorlds;
    private boolean allowInCreative;
    private boolean autoSaveOnQuit;
    private boolean messagesEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        FileConfiguration config = getConfig();
        backpackName = config.getString("backpack.name", "§bSimple Backpack");
        allowedWorlds = config.getStringList("backpack.allowed-worlds");
        allowInCreative = config.getBoolean("backpack.allow-in-creative", false);
        autoSaveOnQuit = config.getBoolean("backpack.auto-save-on-quit", true);
        messagesEnabled = config.getBoolean("messages-enabled", true);
        backpackManager = new BackpackManager(this, backpackName);
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("backpack").setExecutor(this);
    }

    @Override
    public void onDisable() {
        backpackManager.saveAllBackpacks();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            if (messagesEnabled) sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("simplebackpack.use")) {
            if (messagesEnabled) player.sendMessage(getConfig().getString("messages.no-permission", "§cYou do not have permission to use this command."));
            return true;
        }
        if (!allowedWorlds.contains(player.getWorld().getName())) {
            if (messagesEnabled) player.sendMessage(getConfig().getString("messages.not-allowed-world", "§cYou cannot use your backpack in this world."));
            return true;
        }
        if (!allowInCreative && player.getGameMode().name().equalsIgnoreCase("CREATIVE")) {
            if (messagesEnabled) player.sendMessage(getConfig().getString("messages.creative-not-allowed", "§cBackpack is not available in Creative mode."));
            return true;
        }
        backpackManager.openBackpack(player);
        if (messagesEnabled) player.sendMessage(getConfig().getString("messages.open-success", "§bYour backpack is now open!"));
        return true;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (autoSaveOnQuit) {
            backpackManager.saveBackpack(event.getPlayer());
        }
    }
}
