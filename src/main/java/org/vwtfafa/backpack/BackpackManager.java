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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.ChatColor;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BackpackManager implements Listener {
    private JavaPlugin plugin;
    private final Map<UUID, Inventory> backpacks = new ConcurrentHashMap<>();
    private Map<UUID, Set<UUID>> teams;
    private boolean teamEnabled;
    private File dataFolder;
    private volatile String backpackName;
    private volatile int backpackSize;
    private boolean classicMode;
    private boolean adminEnabled;
    private boolean liveConfigReload;
    private boolean showTeamCommands;
    private boolean showAdminCommands;
    private boolean keepContentsOnDeath;
    private Locale locale;
    private FileConfiguration configCache;
    Map<UUID, SharedSession> sharedSessions = new ConcurrentHashMap<>();
    private final Set<UUID> readOnlyViewers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final File auditLogFile;

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
        this.auditLogFile = new File(plugin.getDataFolder(), "backpack-audit.log");
        try {
            if (!this.auditLogFile.exists()) this.auditLogFile.createNewFile();
        } catch (Exception ignored) {}
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.configCache = plugin.getConfig();
    }

    public void openBackpack(Player player) {
        Inventory inv = getBackpack(player);
        player.openInventory(inv);
    }

    public Inventory getBackpack(Player player) {
        UUID uuid = player.getUniqueId();
        // Clean up expired shared sessions
        cleanupExpiredSessions();
        // check if this player has a temporary share to another owner's backpack
        SharedSession session = sharedSessions.get(uuid);
        if (session != null && !session.isExpired()) {
            UUID owner = session.getOwner();
            return backpacks.computeIfAbsent(owner, u -> loadBackpack(owner));
        }
        if (teamEnabled && teams.containsKey(uuid) && !teams.get(uuid).isEmpty()) {
            // Shared team backpack: find the actual team owner (first member or stored owner)
            UUID teamOwner = getTeamOwner(uuid);
            Inventory teamInv = backpacks.computeIfAbsent(teamOwner, u -> loadBackpack(teamOwner));
            return teamInv;
        }
        return backpacks.computeIfAbsent(uuid, u -> loadBackpack(uuid));
    }

    /**
     * Returns the team owner UUID for a given team member.
     * The owner is the map key whose set contains the member.
     */
    private UUID getTeamOwner(UUID member) {
        for (Map.Entry<UUID, Set<UUID>> entry : teams.entrySet()) {
            if (entry.getValue().contains(member)) {
                return entry.getKey();
            }
        }
        return member; // fallback
    }

    /**
     * Removes expired shared sessions from the map.
     */
    void cleanupExpiredSessions() {
        Iterator<Map.Entry<UUID, SharedSession>> it = sharedSessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, SharedSession> entry = it.next();
            if (entry.getValue().isExpired()) {
                it.remove();
            }
        }
    }

    public void updateBackpackGUI(Player player) {
        // Recreate inventory with new size or name
        // Resolve the effective owner to maintain team/share integrity
        UUID effectiveOwner = resolveEffectiveOwner(player.getUniqueId());
        Inventory oldInv = backpacks.get(effectiveOwner);
        Inventory newInv = Bukkit.createInventory(null, backpackSize, backpackName);
        if (oldInv != null) {
            for (int i = 0; i < Math.min(oldInv.getSize(), newInv.getSize()); i++) {
                newInv.setItem(i, oldInv.getItem(i));
            }
        }
        backpacks.put(effectiveOwner, newInv);
        player.openInventory(newInv);
    }

    /**
     * Resolves the effective owner of a player's backpack.
     * Returns the team owner if the player is in a team, or the session owner if they have a share.
     * Otherwise returns the player's own UUID.
     */
    public UUID resolveEffectiveOwner(UUID playerId) {
        // Check shared session first
        SharedSession session = sharedSessions.get(playerId);
        if (session != null && !session.isExpired()) {
            return session.getOwner();
        }
        // Check team ownership
        if (teamEnabled && teams.containsKey(playerId) && !teams.get(playerId).isEmpty()) {
            return getTeamOwner(playerId);
        }
        return playerId;
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
        // Resolve the effective owner to save to the correct file
        UUID owner = resolveEffectiveOwner(player.getUniqueId());
        Inventory inv = getBackpack(player);
        File file = new File(dataFolder, owner + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        for (int i = 0; i < backpackSize; i++) {
            config.set("slot" + i, inv.getItem(i));
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save backpack for player " + player.getName() + ": " + e.getMessage());
        }
    }

    public void saveAllBackpacks() {
        for (UUID uuid : new HashSet<>(backpacks.keySet())) {
            File file = new File(dataFolder, uuid + ".yml");
            YamlConfiguration config = new YamlConfiguration();
            Inventory inv = backpacks.get(uuid);
            for (int i = 0; i < backpackSize; i++) {
                config.set("slot" + i, inv.getItem(i));
            }
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to save backpack for UUID " + uuid + ": " + e.getMessage());
            }
        }
    }

    // Audit logging
    private void logAudit(String line) {
        try (FileWriter fw = new FileWriter(auditLogFile, true); PrintWriter pw = new PrintWriter(fw)) {
            String ts = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now());
            pw.println(ts + " - " + line);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write to audit log: " + e.getMessage());
        }
    }

    public JavaPlugin getPlugin() {
        return plugin;
    }

    public Set<UUID> listKnownBackpacks() {
        // list files in dataFolder
        Set<UUID> result = new HashSet<>();
        File[] files = dataFolder.listFiles((d, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                try {
                    String n = f.getName();
                    String uuid = n.substring(0, n.length() - 4);
                    result.add(UUID.fromString(uuid));
                } catch (Exception ignored) {}
            }
        }
        // also include in-memory keys (iterate over copy to avoid concurrent modification)
        result.addAll(new HashSet<>(backpacks.keySet()));
        return result;
    }

    public int getBackpackSizeFor(UUID uuid) {
        // simple: return current configured size (could be extended to per-player)
        return backpackSize;
    }

    // Admin opens a target backpack; preview=true -> read-only
    public void openForAdmin(UUID owner, Player admin, boolean preview) {
        Inventory inv = backpacks.computeIfAbsent(owner, u -> loadBackpack(owner));
        // open a new inventory view for admin with same contents
        Inventory view = Bukkit.createInventory(admin, inv.getSize(), "Backpack: " + owner.toString());
        for (int i = 0; i < inv.getSize(); i++) view.setItem(i, inv.getItem(i));
        // register read-only if preview
        if (preview) readOnlyViewers.add(admin.getUniqueId());
        admin.openInventory(view);
        logAudit("ADMIN_OPEN " + admin.getName() + " -> " + owner.toString() + " preview=" + preview);
    }

    // Share a backpack temporarily: target can view owner's backpack until expiryMillis since now
    public void shareBackpack(UUID owner, UUID target, long durationMillis) {
        long expiry = System.currentTimeMillis() + durationMillis;
        sharedSessions.put(target, new SharedSession(owner, expiry));
        logAudit("SHARE " + owner.toString() + " -> " + target.toString() + " until=" + expiry);
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
        if (ChatColor.stripColor(event.getView().getTitle()).equals("Backpack Config")) {
            event.setCancelled(true);
            Player player = (Player) event.getWhoClicked();
            switch (event.getSlot()) {
                case 0:
                    // Name ändern (Dialog oder Standard)
                    this.backpackName = locale == Locale.GERMAN ? "§bRucksack" : "§bBackpack";
                    saveConfigValue("backpack.name", this.backpackName);
                    updateBackpackGUI(player);
                    player.sendMessage(locale == Locale.GERMAN ? "§aRucksack-Name geändert." : "§aBackpack name changed.");
                    break;
                case 1:
                    // Farbe ändern (cycle: Aqua -> Green -> Red -> Aqua ...)
                    this.backpackName = cycleColor(this.backpackName);
                    saveConfigValue("backpack.name", this.backpackName);
                    updateBackpackGUI(player);
                    player.sendMessage(locale == Locale.GERMAN ? "§aRucksack-Farbe geändert." : "§aBackpack color changed.");
                    break;
                case 2:
                    // Größe ändern (cycle)
                    this.backpackSize = (this.backpackSize == 54) ? 9 : this.backpackSize + 9;
                    saveConfigValue("backpack.size", this.backpackSize);
                    updateBackpackGUI(player);
                    player.sendMessage(locale == Locale.GERMAN ? "§aRucksack-Größe geändert." : "§aBackpack size changed.");
                    break;
            }
            player.closeInventory();
        }
    }

    @org.bukkit.event.EventHandler
    public void onInventoryClickGlobal(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null) return;
        Player viewer = (Player) event.getWhoClicked();
        // read-only enforcement
        if (readOnlyViewers.contains(viewer.getUniqueId())) {
            event.setCancelled(true);
            viewer.sendMessage(locale == Locale.GERMAN ? "§cNur Vorschau - keine Änderungen erlaubt." : "§cPreview mode - changes are not allowed.");
            return;
        }

        // Use plain title for comparison (color codes are stripped from inventory titles in Paper)
        String plainTitle = ChatColor.stripColor(title);
        String plainBackpackName = ChatColor.stripColor(backpackName);

        // Backpack interactions
        if (plainTitle.equals(plainBackpackName) || plainTitle.startsWith("Backpack: ")) {
            // shift-click one-click transfer: if clicked in player's inventory and shift-click -> move to backpack
            if (event.isShiftClick()) {
                Inventory clicked = event.getClickedInventory();
                Inventory top = event.getView().getTopInventory();
                if (clicked != null && clicked.equals(viewer.getInventory()) && top != null) {
                    ItemStack moving = event.getCurrentItem();
                    if (moving == null || moving.getType() == Material.AIR) return;
                    // find first empty slot in top inventory
                    for (int i = 0; i < top.getSize(); i++) {
                        if (top.getItem(i) == null || top.getItem(i).getType() == Material.AIR) {
                            top.setItem(i, moving.clone());
                            // Remove from the exact clicked slot using index
                            int clickedSlot = event.getSlot();
                            if (clickedSlot >= 0 && clickedSlot < clicked.getSize()) {
                                clicked.setItem(clickedSlot, null);
                            }
                            event.setCancelled(true);
                            return;
                        }
                    }
                    // If no empty slot found, notify player
                    viewer.sendMessage(locale == Locale.GERMAN ?
                        "§cDer Rucksack ist voll!" :
                        "§cThe backpack is full!");
                }
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onInventoryCloseEvent(InventoryCloseEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        if (title == null) return;
        Player viewer = (Player) event.getPlayer();
        // clean up preview markers
        if (readOnlyViewers.contains(viewer.getUniqueId())) {
            readOnlyViewers.remove(viewer.getUniqueId());
        }
        // If admin was editing a "Backpack: <uuid>" view, persist changes
        if (title.startsWith("Backpack: ")) {
            String uuidStr = title.substring("Backpack: ".length()).trim();
            try {
                UUID owner = UUID.fromString(uuidStr);
                Inventory top = event.getInventory();
                // apply contents back to owner's stored backpack
                Inventory stored = backpacks.computeIfAbsent(owner, u -> loadBackpack(owner));
                for (int i = 0; i < Math.min(stored.getSize(), top.getSize()); i++) {
                    stored.setItem(i, top.getItem(i));
                }
                // optionally create a snapshot before saving (config: admin.auto-snapshot)
                boolean doSnapshot = true;
                try {
                    if (configCache != null) doSnapshot = configCache.getBoolean("admin.auto-snapshot", true);
                } catch (Exception ignored) {}
                if (doSnapshot) {
                    try {
                        File snapshotsDir = new File(plugin.getDataFolder(), "backups/snapshots");
                        if (!snapshotsDir.exists()) snapshotsDir.mkdirs();
                        File src = new File(dataFolder, owner + ".yml");
                        if (src.exists()) {
                            String ts = String.valueOf(System.currentTimeMillis());
                            File dest = new File(snapshotsDir, owner + "-" + ts + ".yml");
                            java.nio.file.Files.copy(src.toPath(), dest.toPath());
                            logAudit("SNAPSHOT " + owner.toString() + " -> " + dest.getName());
                        }
                    } catch (IOException ignored) {}
                }

                // save asynchronously
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            File file = new File(dataFolder, owner + ".yml");
                            YamlConfiguration cfg = new YamlConfiguration();
                            for (int i = 0; i < stored.getSize(); i++) cfg.set("slot" + i, stored.getItem(i));
                            cfg.save(file);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }.runTaskAsynchronously(plugin);
                logAudit("ADMIN_SAVE " + viewer.getName() + " -> " + owner.toString());
            } catch (IllegalArgumentException ignored) {}
        }
        // If this was a normal backpack view, save owner's backpack on close
        String plainTitle = ChatColor.stripColor(title);
        String plainBackpackName = ChatColor.stripColor(backpackName);
        if (plainTitle.equals(plainBackpackName)) {
            // find which owner this view represented (shared session, team, or viewer himself)
            UUID owner = resolveEffectiveOwner(viewer.getUniqueId());
            // save asynchronously
            final UUID saveOwner = owner;
            new BukkitRunnable() {
                @Override
                public void run() {
                    try {
                        Inventory stored = backpacks.computeIfAbsent(saveOwner, u -> loadBackpack(saveOwner));
                        File file = new File(dataFolder, saveOwner + ".yml");
                        YamlConfiguration cfg = new YamlConfiguration();
                        for (int i = 0; i < stored.getSize(); i++) cfg.set("slot" + i, stored.getItem(i));
                        cfg.save(file);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }.runTaskAsynchronously(plugin);
        }
    }

    /**
     * Cycles the color prefix of the backpack name.
     * Cycle order: §b (aqua) -> §a (green) -> §c (red) -> §b (aqua) ...
     */
    private String cycleColor(String name) {
        // Define the color cycle order
        String[] colors = {"§b", "§a", "§c"};
        // Find current color index
        int currentIdx = -1;
        for (int i = 0; i < colors.length; i++) {
            if (name.startsWith(colors[i])) {
                currentIdx = i;
                break;
            }
        }
        // Determine next color
        int nextIdx;
        if (currentIdx == -1) {
            nextIdx = 0; // default to aqua if no color found
        } else {
            nextIdx = (currentIdx + 1) % colors.length;
        }
        // Remove any existing color code prefix and prepend the new one
        String baseName = name;
        if (baseName.startsWith("§")) {
            baseName = baseName.substring(2);
        }
        return colors[nextIdx] + baseName;
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
            player.sendMessage(locale == Locale.GERMAN ? "§aDu hast das Team verlassen." : "§aYou have left the team.");
        } else {
            player.sendMessage(locale == Locale.GERMAN ? "§cDu bist in keinem Team." : "§cYou are not in a team.");
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
