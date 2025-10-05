package org.vwtfafa.backpack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BackpackManager implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Inventory> backpacks = new HashMap<>();
    private final Map<UUID, Set<UUID>> teams;
    private final boolean teamEnabled;
    private final File dataFolder;
    private String backpackName;
    private int backpackSize;

    public BackpackManager(JavaPlugin plugin, String backpackName, int backpackSize, Map<UUID, Set<UUID>> teams, boolean teamEnabled) {
        this.plugin = plugin;
        this.backpackName = backpackName;
        this.backpackSize = backpackSize;
        this.teams = teams;
        this.teamEnabled = teamEnabled;
        this.dataFolder = new File(plugin.getDataFolder(), "backpacks");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openBackpack(Player player) {
        Inventory inv = getBackpack(player);
        player.openInventory(inv);
    }

    public Inventory getBackpack(Player player) {
        if (teamEnabled && teams.containsKey(player.getUniqueId()) && !teams.get(player.getUniqueId()).isEmpty()) {
            // Shared team backpack
            UUID teamOwner = player.getUniqueId();
            Inventory teamInv = backpacks.computeIfAbsent(teamOwner, uuid -> loadBackpack(teamOwner));
            return teamInv;
        }
        return backpacks.computeIfAbsent(player.getUniqueId(), uuid -> loadBackpack(player.getUniqueId()));
    }

    private Inventory loadBackpack(UUID uuid) {
        File file = new File(dataFolder, uuid + ".yml");
        Inventory inv = Bukkit.createInventory(null, backpackSize, backpackName);
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (int i = 0; i < backpackSize; i++) {
                inv.setItem(i, config.getItemStack("slot" + i));
            }
        }
        return inv;
    }

    public void saveBackpack(Player player) {
        Inventory inv = getBackpack(player);
        File file = new File(dataFolder, player.getUniqueId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < backpackSize; i++) {
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
            File file = new File(dataFolder, uuid + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            Inventory inv = backpacks.get(uuid);
            for (int i = 0; i < backpackSize; i++) {
                config.set("slot" + i, inv.getItem(i));
            }
            try {
                config.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void clearBackpack(Player player) {
        Inventory inv = getBackpack(player);
        for (int i = 0; i < backpackSize; i++) {
            inv.setItem(i, null);
        }
        saveBackpack(player);
    }

    public void openConfigGUI(Player player) {
        Inventory gui = Bukkit.createInventory(player, 9, "Backpack Config");
        // Slot 0: Change Name
        ItemStack nameItem = new ItemStack(Material.NAME_TAG);
        gui.setItem(0, nameItem);
        // Slot 1: Change Color
        ItemStack colorItem = new ItemStack(Material.LIME_DYE);
        gui.setItem(1, colorItem);
        // Slot 2: Change Size
        ItemStack sizeItem = new ItemStack(Material.CHEST);
        gui.setItem(2, sizeItem);
        player.openInventory(gui);
    }

    // Handle GUI clicks (simplified)
    @org.bukkit.event.EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("Backpack Config")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            switch (event.getSlot()) {
                case 0:
                    // Change name (example: set to default)
                    this.backpackName = "§bSimple Backpack";
                    player.sendMessage("Backpack name set to default.");
                    break;
                case 1:
                    // Change color (example: set to green)
                    this.backpackName = "§aSimple Backpack";
                    player.sendMessage("Backpack color set to green.");
                    break;
                case 2:
                    // Change size (cycle through sizes)
                    this.backpackSize = (this.backpackSize == 54) ? 9 : this.backpackSize + 9;
                    player.sendMessage("Backpack size set to " + this.backpackSize);
                    break;
            }
            player.closeInventory();
        }
    }
}
