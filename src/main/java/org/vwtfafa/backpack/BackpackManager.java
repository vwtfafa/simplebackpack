package org.vwtfafa.backpack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BackpackManager {
    private final JavaPlugin plugin;
    private final Map<UUID, Inventory> backpacks = new HashMap<>();
    private final File dataFolder;
    private final String backpackName;

    public BackpackManager(JavaPlugin plugin, String backpackName) {
        this.plugin = plugin;
        this.backpackName = backpackName;
        this.dataFolder = new File(plugin.getDataFolder(), "backpacks");
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    public void openBackpack(Player player) {
        Inventory inv = getBackpack(player);
        player.openInventory(inv);
    }

    public Inventory getBackpack(Player player) {
        return backpacks.computeIfAbsent(player.getUniqueId(), uuid -> loadBackpack(player));
    }

    private Inventory loadBackpack(Player player) {
        File file = new File(dataFolder, player.getUniqueId() + ".yml");
        Inventory inv = Bukkit.createInventory(player, 27, backpackName);
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (int i = 0; i < 27; i++) {
                inv.setItem(i, config.getItemStack("slot" + i));
            }
        }
        return inv;
    }

    public void saveBackpack(Player player) {
        Inventory inv = getBackpack(player);
        File file = new File(dataFolder, player.getUniqueId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < 27; i++) {
            config.set("slot" + i, inv.getItem(i));
        }
        try {
            config.save(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveAllBackpacks() {
        for (UUID uuid : backpacks.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                saveBackpack(player);
            }
        }
    }
}
