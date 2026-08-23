package org.vwtfafa.backpack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.Listener;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class BackpackManager implements Listener {
    private static final int MIN_BACKPACK_SIZE = 9;
    private static final int MAX_BACKPACK_SIZE = 54;
    private static final long AUDIT_LOG_MAX_BYTES = 5L * 1024L * 1024L;

    private JavaPlugin plugin;
    private final Messages messages;
    private final Map<UUID, Inventory> backpacks = new ConcurrentHashMap<>();
    private TeamRegistry teamRegistry;
    private boolean teamEnabled;
    private File dataFolder;
    private volatile String backpackName;
    private volatile int backpackSize;
    private Locale locale;
    private FileConfiguration configCache;
    Map<UUID, SharedSession> sharedSessions = new ConcurrentHashMap<>();
    private final File auditLogFile;
    // Guards all inventory file writes: prevents interleaved temp-file writes
    // without keeping an ever-growing per-owner lock map
    private final Object saveIoLock = new Object();

    public BackpackManager(JavaPlugin plugin, Messages messages, String backpackName, int backpackSize, TeamRegistry teamRegistry, boolean teamEnabled, Locale locale) {
        this.plugin = plugin;
        this.messages = messages;
        this.backpackName = backpackName;
        this.teamRegistry = teamRegistry;
        this.teamEnabled = teamEnabled;
        this.locale = locale;
        this.dataFolder = new File(plugin.getDataFolder(), "backpacks");
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.auditLogFile = new File(plugin.getDataFolder(), "backpack-audit.log");
        try {
            if (!this.auditLogFile.exists()) this.auditLogFile.createNewFile();
        } catch (Exception ignored) {}
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.configCache = plugin.getConfig();
        this.backpackSize = validateBackpackSize(backpackSize);
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
        if (teamEnabled) {
            UUID teamOwner = getTeamOwner(uuid);
            if (!teamOwner.equals(uuid)) {
                return backpacks.computeIfAbsent(teamOwner, u -> loadBackpack(teamOwner));
            }
        }
        return backpacks.computeIfAbsent(uuid, u -> loadBackpack(uuid));
    }

    /**
     * Returns the team owner UUID for a given team member,
     * or the member itself when they have no team.
     */
    private UUID getTeamOwner(UUID member) {
        UUID owner = teamRegistry.findOwner(member);
        return owner != null ? owner : member;
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
        // Close every open view of the old inventory before swapping so no
        // viewer keeps editing a detached inventory that would later be saved
        // over the resized one (split-brain data loss)
        if (oldInv != null) {
            List<Player> viewers = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (oldInv.equals(online.getOpenInventory().getTopInventory())) {
                    viewers.add(online);
                }
            }
            for (Player viewer : viewers) {
                viewer.closeInventory();
            }
        }
        BackpackInventoryHolder holder = BackpackInventoryHolder.backpack(effectiveOwner);
        Inventory newInv = Bukkit.createInventory(holder, backpackSize, titleComponent(backpackName));
        holder.setInventory(newInv);
        if (oldInv != null) {
            int keptSlots = Math.min(oldInv.getSize(), newInv.getSize());
            for (int i = 0; i < keptSlots; i++) {
                newInv.setItem(i, oldInv.getItem(i));
            }
            // Hand items from removed slots back to the player so nothing is lost;
            // anything that no longer fits is dropped instead of vanishing
            for (int i = keptSlots; i < oldInv.getSize(); i++) {
                ItemStack item = oldInv.getItem(i);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                for (ItemStack rest : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
        }
        backpacks.put(effectiveOwner, newInv);
        player.openInventory(newInv);
    }

    /**
     * Checks whether shrinking to newSize is safe: every item from the slots
     * that would be removed must fit into the player's storage (one slot per
     * item, conservative). Returns true when nothing blocks the resize.
     */
    private boolean canShrinkSafely(Player player, int newSize) {
        UUID effectiveOwner = resolveEffectiveOwner(player.getUniqueId());
        Inventory oldInv = backpacks.get(effectiveOwner);
        if (oldInv == null || oldInv.getSize() <= newSize) {
            return true;
        }
        List<ItemStack> overflow = new ArrayList<>();
        for (int i = newSize; i < oldInv.getSize(); i++) {
            ItemStack item = oldInv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                overflow.add(item);
            }
        }
        if (overflow.isEmpty()) {
            return true;
        }
        int freeSlots = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType().isAir()) {
                freeSlots++;
            }
        }
        return freeSlots >= overflow.size();
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
        if (teamEnabled) {
            UUID teamOwner = getTeamOwner(playerId);
            if (!teamOwner.equals(playerId)) return teamOwner;
        }
        return playerId;
    }

    private Inventory loadBackpack(UUID uuid) {
        File file = new File(dataFolder, uuid + ".yml");
        BackpackInventoryHolder holder = BackpackInventoryHolder.backpack(uuid);
        Inventory inv = Bukkit.createInventory(holder, backpackSize, titleComponent(backpackName));
        holder.setInventory(inv);
        if (!file.exists()) {
            return inv;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        for (int i = 0; i < backpackSize; i++) {
            inv.setItem(i, config.getItemStack("slot" + i));
        }
        // Recover items stored in slots that no longer exist because the
        // configured size was reduced; never silently drop them
        List<ItemStack> overflow = new ArrayList<>();
        for (int i = backpackSize; i < MAX_BACKPACK_SIZE; i++) {
            ItemStack item = config.getItemStack("slot" + i);
            if (item != null && !item.getType().isAir()) {
                overflow.add(item);
            }
        }
        if (overflow.isEmpty()) {
            return inv;
        }
        Map<Integer, ItemStack> leftover = inv.addItem(overflow.toArray(new ItemStack[0]));
        if (!leftover.isEmpty()) {
            storeOverflow(uuid, leftover.values());
        }
        return inv;
    }

    /**
     * Persists items that no longer fit into a shrunken backpack in a sidecar
     * file so they can be recovered manually instead of being lost.
     */
    private void storeOverflow(UUID owner, Collection<ItemStack> items) {
        File overflowFile = new File(dataFolder, owner + ".overflow.yml");
        YamlConfiguration overflowConfig = new YamlConfiguration();
        int slot = 0;
        for (ItemStack item : items) {
            overflowConfig.set("slot" + slot++, item);
        }
        try {
            overflowConfig.save(overflowFile);
            plugin.getLogger().warning("Backpack " + owner + " shrank below its stored contents; "
                    + items.size() + " item(s) saved to " + overflowFile.getName());
            logAudit("OVERFLOW " + owner.toString() + " -> " + overflowFile.getName()
                    + " (" + items.size() + " items)");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to store overflowing backpack contents of "
                    + owner + ": " + e.getMessage());
        }
    }

    public void saveBackpack(Player player) {
        // Resolve the effective owner to save to the correct file
        UUID owner = resolveEffectiveOwner(player.getUniqueId());
        Inventory inv = getBackpack(player);
        writeInventory(owner, snapshot(inv), "player " + player.getName());
    }

    /**
     * Saves a player's backpack without blocking the caller: the contents
     * are snapshotted synchronously and written on an async task. Used for
     * quit-time autosave so disconnects never cause main-thread disk I/O.
     */
    public void saveBackpackAsync(Player player) {
        UUID owner = resolveEffectiveOwner(player.getUniqueId());
        Inventory inv = getBackpack(player);
        saveInventoryAsync(owner, snapshot(inv), "quit");
    }

    public void saveAllBackpacks() {
        for (UUID uuid : new HashSet<>(backpacks.keySet())) {
            Inventory inv = backpacks.get(uuid);
            if (inv != null) writeInventory(uuid, snapshot(inv), "shutdown");
        }
    }

    // Audit logging
    private void logAudit(String line) {
        String timestamped = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now())
                + " - " + line;
        // File I/O must not block the main thread; while the plugin is
        // disabling, scheduled async tasks are never run, so write directly
        if (plugin.isEnabled()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> writeAuditLine(timestamped));
        } else {
            writeAuditLine(timestamped);
        }
    }

    private void writeAuditLine(String line) {
        synchronized (this) {
            try {
                if (auditLogFile.length() > AUDIT_LOG_MAX_BYTES) {
                    File rotated = new File(auditLogFile.getParentFile(), auditLogFile.getName() + ".old");
                    Files.move(auditLogFile.toPath(), rotated.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    auditLogFile.createNewFile();
                }
                try (FileWriter fw = new FileWriter(auditLogFile, true); PrintWriter pw = new PrintWriter(fw)) {
                    pw.println(line);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to write to audit log: " + e.getMessage());
            }
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
        BackpackInventoryHolder holder = BackpackInventoryHolder.admin(owner, preview);
        Inventory view = Bukkit.createInventory(holder, inv.getSize(),
                Component.text("Backpack: " + owner));
        holder.setInventory(view);
        for (int i = 0; i < inv.getSize(); i++) view.setItem(i, inv.getItem(i));
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
        // Only wipe backpacks the dying player owns themselves; one member
        // must not empty a shared team or temporarily shared backpack
        UUID playerId = player.getUniqueId();
        if (!playerId.equals(resolveEffectiveOwner(playerId))) {
            return;
        }
        Inventory inv = getBackpack(player);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
        saveBackpack(player);
    }

    public void setConfig(String backpackName, int backpackSize, boolean teamEnabled, Locale locale) {
        this.backpackName = backpackName;
        this.backpackSize = backpackSize;
        this.teamEnabled = teamEnabled;
        this.locale = locale;
        this.configCache = plugin.getConfig();
    }

    public void openConfigGUI(Player player) {
        BackpackInventoryHolder holder = BackpackInventoryHolder.config();
        Inventory gui = Bukkit.createInventory(holder, 9, Component.text("Backpack Config"));
        holder.setInventory(gui);
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
        if (event.getView().getTopInventory().getHolder() instanceof BackpackInventoryHolder holder
            && holder.getType() == BackpackInventoryHolder.Type.CONFIG) {
            // Cancel everything in this view so no items can be shifted into the
            // config GUI, but only react to clicks on the GUI itself
            event.setCancelled(true);
            if (!event.getView().getTopInventory().equals(event.getClickedInventory())
                    || !(event.getWhoClicked() instanceof Player player)) {
                return;
            }
            switch (event.getSlot()) {
                case 0:
                    // Name ändern (Dialog oder Standard)
                    this.backpackName = locale == Locale.GERMAN ? "§bRucksack" : "§bBackpack";
                    saveConfigValue("backpack.name", this.backpackName);
                    updateBackpackGUI(player);
                    messages.send(player, "config-changed-name");
                    break;
                case 1:
                    // Farbe ändern (cycle: Aqua -> Green -> Red -> Aqua ...)
                    this.backpackName = cycleColor(this.backpackName);
                    saveConfigValue("backpack.name", this.backpackName);
                    updateBackpackGUI(player);
                    messages.send(player, "config-changed-color");
                    break;
                case 2:
                    // Größe ändern (cycle)
                    int newSize = (this.backpackSize == MAX_BACKPACK_SIZE)
                        ? MIN_BACKPACK_SIZE
                        : this.backpackSize + MIN_BACKPACK_SIZE;
                    if (!canShrinkSafely(player, validateBackpackSize(newSize))) {
                        messages.send(player, "resize-no-space");
                        break;
                    }
                    this.backpackSize = validateBackpackSize(newSize);
                    saveConfigValue("backpack.size", this.backpackSize);
                    updateBackpackGUI(player);
                    messages.send(player, "config-changed-size");
                    break;
            }
            player.closeInventory();
        }
    }

    @org.bukkit.event.EventHandler
    /**
     * Keeps preview views read-only. Clicks inside the player's own inventory
     * stay untouched so viewers can still organize their items; only actions
     * that would move items into or out of the previewed inventory are
     * cancelled. Editable backpack and admin-edit views rely on vanilla
     * inventory behavior, including shift-click transfers; the previous
     * manual transfer duplicated that behavior and risked item duplication.
     */
    public void onInventoryClickGlobal(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackInventoryHolder holder)
                || !holder.isPreview()) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        boolean clickedPreview = top.equals(event.getClickedInventory());
        boolean transfersWithPreview = event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR;
        if (!clickedPreview && !transfersWithPreview) {
            return;
        }
        event.setCancelled(true);
        if (clickedPreview && event.getWhoClicked() instanceof Player viewer) {
            messages.send(viewer, "preview-mode");
        }
    }

    /**
     * Cancels drags that would place items into protected inventories
     * (config GUI, admin list, preview views). Without this, drag events
     * bypass the InventoryClickEvent cancellation.
     */
    @org.bukkit.event.EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackInventoryHolder holder)) {
            return;
        }
        boolean editable = (holder.getType() == BackpackInventoryHolder.Type.BACKPACK
                || holder.getType() == BackpackInventoryHolder.Type.ADMIN)
                && !holder.isPreview();
        if (editable) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onInventoryCloseEvent(InventoryCloseEvent event) {
        Player viewer = (Player) event.getPlayer();
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackInventoryHolder holder)) return;
        if (holder.getType() == BackpackInventoryHolder.Type.CONFIG || holder.isPreview()) return;
        // If admin was editing a "Backpack: <uuid>" view, persist changes
        if (holder.getType() == BackpackInventoryHolder.Type.ADMIN) {
                UUID owner = holder.getOwner();
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

                saveInventoryAsync(owner, snapshot(stored), "admin");
                logAudit("ADMIN_SAVE " + viewer.getName() + " -> " + owner.toString());
                return;
        }
        // If this was a normal backpack view, save owner's backpack on close
        if (holder.getType() == BackpackInventoryHolder.Type.BACKPACK) {
            saveInventoryAsync(holder.getOwner(), snapshot(event.getInventory()), "close");
        }
    }

    private ItemStack[] snapshot(Inventory inventory) {
        ItemStack[] contents = inventory.getContents();
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private void saveInventoryAsync(UUID owner, ItemStack[] contents, String source) {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                try {
                    writeInventory(owner, contents, source);
                } catch (RuntimeException e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to save backpack " + owner + " from " + source, e);
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    private void writeInventory(UUID owner, ItemStack[] contents, String source) {
        synchronized (saveIoLock) {
            File file = new File(dataFolder, owner + ".yml");
            File temporaryFile = new File(dataFolder, owner + ".yml.tmp");
            try {
                YamlConfiguration config = new YamlConfiguration();
                for (int i = 0; i < contents.length; i++) config.set("slot" + i, contents[i]);
                config.save(temporaryFile);
                try {
                    Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                    Files.move(temporaryFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Failed to save backpack " + owner + " from " + source, e);
            }
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

    /**
     * Converts a configured legacy title (with § color codes) into an
     * Adventure component for inventory titles.
     */
    private Component titleComponent(String legacyTitle) {
        return LegacyComponentSerializer.legacySection().deserialize(legacyTitle);
    }

    /**
     * Validates that backpack size is a multiple of 9 and within reasonable bounds.
     * @param size the proposed size
     * @return validated size (multiple of 9, between 9 and 54)
     */
    private int validateBackpackSize(int size) {
        // Ensure size is multiple of 9 (valid inventory sizes)
        int validated = Math.max(MIN_BACKPACK_SIZE, (size / MIN_BACKPACK_SIZE) * MIN_BACKPACK_SIZE);
        // Cap at 6 rows (54 slots) as that's the maximum for player inventories
        return Math.min(validated, MAX_BACKPACK_SIZE);
    }
}
