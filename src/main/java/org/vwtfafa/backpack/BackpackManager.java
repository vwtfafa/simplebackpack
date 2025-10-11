package org.vwtfafa.backpack;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BackpackManager implements Listener {
    private JavaPlugin plugin;
    private Map<UUID, Inventory> backpacks = new HashMap<>();
    private Map<UUID, Set<UUID>> teams;
    private boolean teamEnabled;
    private File dataFolder;
    private String backpackName;
    private int backpackSize;
    private boolean classicMode;
    private boolean adminEnabled;
    private boolean liveConfigReload;
    private boolean showTeamCommands;
    private boolean showAdminCommands;
    private boolean keepContentsOnDeath;
    private Locale locale;
    private FileConfiguration configCache;

    public BackpackManager(JavaPlugin plugin, String backpackName, int backpackSize, Map<UUID, Set<UUID>> teams, boolean teamEnabled, boolean classicMode, boolean adminEnabled, boolean liveConfigReload, boolean showTeamCommands, boolean showAdminCommands, boolean keepContentsOnDeath, Locale locale) {
        this.plugin = plugin;
        this.backpackName = backpackName;
        this.backpackSize = backpackSize;
        this.teams = teams;
        this.teamEnabled = teamEnabled;
        this.classicMode = classicMode;
        this.adminEnabled = adminEnabled;
        this.liveConfigReload = liveConfigReload;
        this.showTeamCommands = showTeamCommands;
        this.showAdminCommands = showAdminCommands;
        this.keepContentsOnDeath = keepContentsOnDeath;
        this.locale = locale;
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

    public void updateBackpackGUI(Player player) {
        // Recreate inventory with new size or name
        Inventory oldInv = backpacks.get(player.getUniqueId());
        Inventory newInv = Bukkit.createInventory(null, backpackSize, backpackName);
        if (oldInv != null) {
            for (int i = 0; i < Math.min(oldInv.getSize(), newInv.getSize()); i++) {
                newInv.setItem(i, oldInv.getItem(i));
            }
        }
        backpacks.put(player.getUniqueId(), newInv);
        player.openInventory(newInv);
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

    public void setConfig(String backpackName, int backpackSize, boolean teamEnabled, boolean classicMode, boolean adminEnabled, boolean liveConfigReload, boolean showTeamCommands, boolean showAdminCommands, boolean keepContentsOnDeath, Locale locale) {
        this.backpackName = backpackName;
        this.backpackSize = backpackSize;
        this.teamEnabled = teamEnabled;
        this.classicMode = classicMode;
        this.adminEnabled = adminEnabled;
        this.liveConfigReload = liveConfigReload;
        this.showTeamCommands = showTeamCommands;
        this.showAdminCommands = showAdminCommands;
        this.keepContentsOnDeath = keepContentsOnDeath;
        this.locale = locale;
        this.configCache = plugin.getConfig();
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

    @org.bukkit.event.EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals("Backpack Config")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            switch (event.getSlot()) {
                case 0:
                    // Name ändern (Dialog oder Standard)
                    this.backpackName = locale == Locale.GERMAN ? "§bRucksack" : "§bBackpack";
                    saveConfigValue("backpack.name", this.backpackName);
                    updateBackpackGUI(player);
                    player.sendMessage(locale == Locale.GERMAN ? "Backpack-Name geändert." : "Backpack name changed.");
                    break;
                case 1:
                    // Farbe ändern (cycle: Aqua -> Green -> Red -> Aqua ...)
                    if (this.backpackName.contains("§b")) this.backpackName = this.backpackName.replace("§b", "§a");
                    else if (this.backpackName.contains("§a")) this.backpackName = this.backpackName.replace("§a", "§c");
                    else this.backpackName = "§b" + this.backpackName.replaceAll("§[a-z0-9]", "");
                    saveConfigValue("backpack.name", this.backpackName);
                    updateBackpackGUI(player);
                    player.sendMessage(locale == Locale.GERMAN ? "Backpack-Farbe geändert." : "Backpack color changed.");
                    break;
                case 2:
                    // Größe ändern (cycle)
                    this.backpackSize = (this.backpackSize == 54) ? 9 : this.backpackSize + 9;
                    saveConfigValue("backpack.size", this.backpackSize);
                    updateBackpackGUI(player);
                    player.sendMessage(locale == Locale.GERMAN ? "Backpack-Größe geändert." : "Backpack size changed.");
                    break;
            }
            player.closeInventory();
        }
    }

    private void saveConfigValue(String path, Object value) {
        FileConfiguration config = plugin.getConfig();
        config.set(path, value);
        plugin.saveConfig();
    }

    // Team verlassen
    public void leaveTeam(Player player) {
        UUID uuid = player.getUniqueId();
        if (teams.containsKey(uuid)) {
            teams.remove(uuid);
            player.sendMessage(locale == Locale.GERMAN ? "Du hast das Team verlassen." : "You have left the team.");
        } else {
            player.sendMessage(locale == Locale.GERMAN ? "Du bist in keinem Team." : "You are not in a team.");
        }
    }

    // Admin: Items in alle Backpacks legen
    public void giveItemToAll(ItemStack item) {
        for (UUID uuid : backpacks.keySet()) {
            Inventory inv = backpacks.get(uuid);
            for (int i = 0; i < inv.getSize(); i++) {
                if (inv.getItem(i) == null) {
                    inv.setItem(i, item.clone());
                    break;
                }
            }
        }
    }

    // Admin: Backpacks global aktivieren/deaktivieren
    public void setBackpacksEnabled(boolean enabled) {
        // Diese Logik wird in der Main-Klasse umgesetzt, hier nur Platzhalter
    }

    // Team-Backpack nur anzeigen, wenn Spieler in Team ist
    public boolean isInTeam(Player player) {
        return teams.containsKey(player.getUniqueId()) && !teams.get(player.getUniqueId()).isEmpty();
    }
}
