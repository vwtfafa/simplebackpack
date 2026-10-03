package org.vwtfafa.backpack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class BackpackManager implements Listener {
    private static final int MIN_BACKPACK_SIZE = 9;
    private static final int MAX_BACKPACK_SIZE = 54;
    private static final long AUDIT_LOG_MAX_BYTES = 5L * 1024L * 1024L;
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final List<NamedTextColor> TITLE_COLOR_CYCLE =
            List.of(NamedTextColor.AQUA, NamedTextColor.GREEN, NamedTextColor.RED);

    private final JavaPlugin plugin;
    private final Messages messages;
    private final Map<UUID, Inventory> backpacks = new ConcurrentHashMap<>();
    private final TeamRegistry teamRegistry;
    private boolean teamEnabled;
    private final File dataFolder;
    private volatile String backpackName;
    private volatile int backpackSize;
    // Usage policy snapshot (worlds, creative, global toggle): enforced on
    // open and re-checked when players change world or game mode, so an open
    // backpack cannot be carried into a restricted context.
    private volatile boolean usageEnabled = true;
    private volatile boolean usageAllowCreative;
    private volatile List<String> usageDisabledWorlds = List.of();
    Map<UUID, SharedSession> sharedSessions = new ConcurrentHashMap<>();
    // Owners whose backpack an inventory-close event just persisted; lets the
    // quit-time autosave skip the redundant second write on disconnects
    private final Map<UUID, Long> recentSaveOwners = new ConcurrentHashMap<>();
    private static final long RECENT_SAVE_WINDOW_MILLIS = 2000L;
    private final File auditLogFile;
    // Guards all inventory file writes: prevents interleaved temp-file writes
    // without keeping an ever-growing per-owner lock map
    private final Object saveIoLock = new Object();
    // Monotonic write sequence per owner: async writes carry the sequence from
    // scheduling time, so a stale write (e.g. a close-save snapshotted before
    // a death-clear) is dropped instead of resurrecting deleted items, and the
    // synchronous shutdown save always wins over still-running async writes.
    private final Map<UUID, Long> saveSequences = new ConcurrentHashMap<>();

    public BackpackManager(JavaPlugin plugin, Messages messages, String backpackName, int backpackSize, TeamRegistry teamRegistry, boolean teamEnabled) {
        this.plugin = plugin;
        this.messages = messages;
        this.backpackName = backpackName;
        this.teamRegistry = teamRegistry;
        this.teamEnabled = teamEnabled;
        this.dataFolder = new File(plugin.getDataFolder(), "backpacks");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("Failed to create backpack data folder: " + dataFolder);
        }
        // Remove temp files left behind by an interrupted save so they do not
        // accumulate; the real file was never replaced by them.
        File[] leftovers = dataFolder.listFiles((dir, name) -> name.endsWith(".yml.tmp"));
        if (leftovers != null) {
            for (File leftover : leftovers) {
                if (leftover.delete()) {
                    plugin.getLogger().info("Removed leftover temp save file: " + leftover.getName());
                }
            }
        }
        this.auditLogFile = new File(plugin.getDataFolder(), "backpack-audit.log");
        try {
            if (!this.auditLogFile.exists()) this.auditLogFile.createNewFile();
        } catch (Exception ignored) {}
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.backpackSize = validateBackpackSize(backpackSize);
    }

    public void openBackpack(Player player) {
        String blocked = usageBlockReason(player);
        if (blocked != null) {
            messages.send(player, blocked);
            return;
        }
        Inventory inv = getBackpack(player);
        player.openInventory(inv);
        if (plugin.getConfig().getBoolean("backpack.open-sound", true)) {
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
        }
    }

    public Inventory getBackpack(Player player) {
        UUID uuid = player.getUniqueId();
        // Clean up expired shared sessions
        cleanupExpiredSessions();
        // check if this player has a temporary share to another owner's backpack
        SharedSession session = sharedSessions.get(uuid);
        if (session != null && !session.isExpired()) {
            UUID owner = session.owner();
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
            List<Player> staleAdmins = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!(online.getOpenInventory().getTopInventory().getHolder()
                        instanceof BackpackInventoryHolder holder)) {
                    continue;
                }
                if (oldInv.equals(online.getOpenInventory().getTopInventory())) {
                    viewers.add(online);
                } else if (holder.getType() == BackpackInventoryHolder.Type.ADMIN
                        && effectiveOwner.equals(holder.getOwner())) {
                    // Admin copies are separate inventories; without closing
                    // them a later admin save would overwrite the resized one.
                    staleAdmins.add(online);
                }
            }
            for (Player viewer : viewers) {
                viewer.closeInventory();
            }
            for (Player admin : staleAdmins) {
                admin.closeInventory();
                messages.send(admin, "admin-edit-conflict");
                logAudit("ADMIN_CONFLICT " + admin.getName() + " -> " + effectiveOwner
                        + " (backpack resized, stale admin view closed)");
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
            // Hand items from removed slots back so nothing is lost. The owner
            // receives them when online; otherwise they go to the overflow
            // sidecar instead of a random clicking team member.
            Player ownerPlayer = Bukkit.getPlayer(effectiveOwner);
            List<ItemStack> homeless = new ArrayList<>();
            for (int i = keptSlots; i < oldInv.getSize(); i++) {
                ItemStack item = oldInv.getItem(i);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                if (ownerPlayer != null) {
                    Map<Integer, ItemStack> leftover =
                            ownerPlayer.getInventory().addItem(item);
                    for (ItemStack rest : leftover.values()) {
                        ownerPlayer.getWorld().dropItemNaturally(ownerPlayer.getLocation(), rest);
                    }
                } else {
                    homeless.add(item);
                }
            }
            if (!homeless.isEmpty()) {
                storeOverflow(effectiveOwner, homeless);
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
        // Removed slots must fit the owner's storage, not necessarily the
        // clicking player's (team/shared backpacks resolve to another owner).
        Player owner = Bukkit.getPlayer(effectiveOwner);
        Player storage = owner != null ? owner : player;
        int freeSlots = 0;
        for (ItemStack content : storage.getInventory().getStorageContents()) {
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
            return session.owner();
        }
        // Check team ownership
        if (teamEnabled) {
            UUID teamOwner = getTeamOwner(playerId);
            if (!teamOwner.equals(playerId)) return teamOwner;
        }
        return playerId;
    }

    /**
     * Reads the owner's backpack file off the main thread and installs it
     * later, so the first open never blocks on disk I/O. Silently skipped
     * when the backpack got loaded meanwhile or when the file contains items
     * in slots beyond the configured size (needs the full recovery path).
     */
    void preloadBackpack(UUID ownerId) {
        if (backpacks.containsKey(ownerId)) {
            return;
        }
        File file = new File(dataFolder, ownerId + ".yml");
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            ItemStack[] contents = readInventoryFile(file);
            Bukkit.getGlobalRegionScheduler().run(plugin,
                    t -> applyPreloadedContents(ownerId, contents));
        });
    }

    /**
     * Reads stored slots without touching inventories; returns null when the
     * stored size exceeds the configured one and recovery would be needed.
     */
    private ItemStack[] readInventoryFile(File file) {
        ItemStack[] contents = new ItemStack[MAX_BACKPACK_SIZE];
        if (!file.exists()) {
            return contents;
        }
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (int i = 0; i < MAX_BACKPACK_SIZE; i++) {
                contents[i] = config.getItemStack("slot" + i);
                if (i >= backpackSize && contents[i] != null && !contents[i].getType().isAir()) {
                    return null;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to preload backpack " + file.getName()
                    + ": " + e.getMessage());
            return null;
        }
        return contents;
    }

    private void applyPreloadedContents(UUID ownerId, ItemStack[] contents) {
        if (contents == null || backpacks.containsKey(ownerId)) {
            return;
        }
        BackpackInventoryHolder holder = BackpackInventoryHolder.backpack(ownerId);
        Inventory inv = Bukkit.createInventory(holder, backpackSize, titleComponent(backpackName));
        holder.setInventory(inv);
        for (int i = 0; i < backpackSize; i++) {
            inv.setItem(i, contents[i]);
        }
        backpacks.put(ownerId, inv);
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
        Map<Integer, ItemStack> leftover = overflow.isEmpty()
                ? Map.of()
                : inv.addItem(overflow.toArray(new ItemStack[0]));
        if (!leftover.isEmpty()) {
            storeOverflow(uuid, leftover.values());
            return inv;
        }
        // Nothing homeless from the main file: drain a previous sidecar now
        // that space may have freed up (e.g. size was increased again).
        restoreOverflow(uuid, inv);
        return inv;
    }

    /**
     * Re-ingests a previously written {@code *.overflow.yml} sidecar into a
     * freshly loaded backpack. Items that fit are restored, the sidecar is
     * deleted only when fully drained; anything still not fitting is kept in
     * a rewritten sidecar so repeated shrinks never silently eat items.
     */
    private void restoreOverflow(UUID uuid, Inventory inv) {
        File sidecar = new File(dataFolder, uuid + ".overflow.yml");
        if (!sidecar.exists()) {
            return;
        }
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(sidecar);
        List<ItemStack> stored = new ArrayList<>();
        for (int i = 0; ; i++) {
            ItemStack item = saved.getItemStack("slot" + i);
            if (item == null) {
                break;
            }
            if (!item.getType().isAir()) {
                stored.add(item);
            }
        }
        if (stored.isEmpty()) {
            if (sidecar.delete()) {
                plugin.getLogger().info("Removed empty overflow file for backpack " + uuid + ".");
            }
            return;
        }
        Map<Integer, ItemStack> rest = inv.addItem(stored.toArray(new ItemStack[0]));
        if (rest.isEmpty()) {
            if (sidecar.delete()) {
                plugin.getLogger().info("Restored " + stored.size()
                        + " overflow item(s) into backpack " + uuid + ".");
                logAudit("OVERFLOW_RESTORED " + uuid + " (" + stored.size() + " items)");
            }
            return;
        }
        YamlConfiguration rewritten = new YamlConfiguration();
        int slot = 0;
        for (ItemStack item : rest.values()) {
            rewritten.set("slot" + slot++, item);
        }
        try {
            rewritten.save(sidecar);
            plugin.getLogger().warning("Restored " + (stored.size() - rest.size())
                    + " overflow item(s) into backpack " + uuid + "; " + rest.size()
                    + " item(s) remain in " + sidecar.getName() + ".");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to rewrite overflow file for backpack "
                    + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Persists items that no longer fit into a shrunken backpack in a sidecar
     * file so they can be recovered instead of being lost. Existing sidecar
     * contents are merged, never overwritten.
     */
    private void storeOverflow(UUID owner, Collection<ItemStack> items) {
        List<ItemStack> fresh = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                fresh.add(item);
            }
        }
        if (fresh.isEmpty()) {
            return;
        }
        File overflowFile = new File(dataFolder, owner + ".overflow.yml");
        YamlConfiguration overflowConfig = new YamlConfiguration();
        int slot = 0;
        if (overflowFile.exists()) {
            YamlConfiguration existing = YamlConfiguration.loadConfiguration(overflowFile);
            while (true) {
                ItemStack kept = existing.getItemStack("slot" + slot);
                if (kept == null) {
                    break;
                }
                overflowConfig.set("slot" + slot, kept);
                slot++;
            }
        }
        for (ItemStack item : fresh) {
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

    /**
     * Saves a player's backpack without blocking the caller: the contents
     * are snapshotted synchronously and written on an async task. Used for
     * quit-time autosave so disconnects never cause main-thread disk I/O.
     * Skipped when the inventory-close save triggered by this very
     * disconnect already finished persisting the same backpack; in that
     * case {@code afterWrite} runs immediately on the current thread.
     * Otherwise {@code afterWrite} runs on the main thread once the write
     * has completed, so cache eviction can never race a pending write.
     */
    public void saveBackpackAsync(Player player, Runnable afterWrite) {
        UUID owner = resolveEffectiveOwner(player.getUniqueId());
        Long lastSave = recentSaveOwners.remove(owner);
        if (lastSave != null && System.currentTimeMillis() - lastSave <= RECENT_SAVE_WINDOW_MILLIS) {
            afterWrite.run();
            return;
        }
        Inventory inv = getBackpack(player);
        saveInventoryAsync(owner, snapshot(inv), "quit", afterWrite);
    }

    public void saveAllBackpacks() {
        for (UUID uuid : new HashSet<>(backpacks.keySet())) {
            Inventory inv = backpacks.get(uuid);
            if (inv != null) writeInventory(uuid, snapshot(inv), "shutdown", nextSaveSequence(uuid));
        }
    }

    // Audit logging
    private void logAudit(String line) {
        String timestamped = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.now())
                + " - " + line;
        // File I/O must not block the main thread; while the plugin is
        // disabling, scheduled tasks are never run, so write directly
        if (plugin.isEnabled()) {
            plugin.getServer().getAsyncScheduler().runNow(plugin, task -> writeAuditLine(timestamped));
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
        File[] files = dataFolder.listFiles((d, name) ->
                name.endsWith(".yml") && !name.endsWith(".overflow.yml"));
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
        String ownerName = Bukkit.getOfflinePlayer(owner).getName();
        Component title = messages.component("gui-admin-view-title",
                "{player}", ownerName != null ? ownerName : owner.toString());
        Inventory view = Bukkit.createInventory(holder, inv.getSize(), title);
        holder.setInventory(view);
        for (int i = 0; i < inv.getSize(); i++) view.setItem(i, inv.getItem(i));
        admin.openInventory(view);
        logAudit("ADMIN_OPEN " + admin.getName() + " -> " + owner.toString() + " preview=" + preview);
    }

    // Share a backpack temporarily: target can view owner's backpack until expiryMillis since now.
    // Returns false when the share was rejected (self-share or non-positive
    // duration); the duration is clamped to one minute .. seven days so direct
    // callers cannot create permanent or overflowing sessions.
    public boolean shareBackpack(UUID owner, UUID target, long durationMillis) {
        if (owner.equals(target)) {
            return false;
        }
        long clamped = Math.min(Math.max(durationMillis, 60_000L), 7L * 24L * 60L * 60_000L);
        long expiry = System.currentTimeMillis() + clamped;
        sharedSessions.put(target, new SharedSession(owner, expiry));
        logAudit("SHARE " + owner.toString() + " -> " + target.toString() + " until=" + expiry);
        return true;
    }


    public void clearBackpack(Player player) {
        // Only wipe backpacks the dying player owns themselves; one member
        // must not empty a shared team or temporarily shared backpack
        UUID ownerId = player.getUniqueId();
        if (!ownerId.equals(resolveEffectiveOwner(ownerId))) {
            return;
        }
        Inventory inv = getBackpack(player);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
        saveInventoryAsync(ownerId, snapshot(inv), "death", () -> { });
    }

    /**
     * Wipes the backpack of the given effective owner on behalf of an admin
     * and persists the empty state asynchronously.
     */
    public void clearForAdmin(UUID ownerId, Player admin) {
        Inventory inv = backpacks.computeIfAbsent(ownerId, u -> loadBackpack(ownerId));
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
        saveInventoryAsync(ownerId, snapshot(inv), "admin-clear", () -> { });
        logAudit("ADMIN_CLEAR " + admin.getName() + " -> " + ownerId.toString());
    }

    public void setConfig(String backpackName, int backpackSize, boolean teamEnabled) {
        if (backpackName != null && !backpackName.isBlank()) {
            this.backpackName = backpackName;
        } else {
            plugin.getLogger().warning("Ignoring invalid backpack name from config reload; keeping previous value.");
        }
        this.backpackSize = validateBackpackSize(backpackSize);
        this.teamEnabled = teamEnabled;
    }

    /**
     * Refreshes the usage policy snapshot (global toggle, creative mode,
     * disabled worlds) after (re)loads; callers pass the already validated
     * {@link PluginSettings} values.
     */
    public void setUsagePolicy(boolean enabled, boolean allowInCreative, List<String> disabledWorlds) {
        this.usageEnabled = enabled;
        this.usageAllowCreative = allowInCreative;
        this.usageDisabledWorlds = disabledWorlds != null ? List.copyOf(disabledWorlds) : List.of();
    }

    /**
     * Returns the message key explaining why the player may not use their
     * backpack right now, or null when usage is allowed.
     */
    private String usageBlockReason(Player player) {
        return usageBlockReason(player, player.getGameMode());
    }

    private String usageBlockReason(Player player, GameMode gameMode) {
        if (!usageEnabled) {
            return "backpacks-disabled";
        }
        if (usageDisabledWorlds.contains(player.getWorld().getName())) {
            return "not-allowed-world";
        }
        if (!usageAllowCreative && gameMode == GameMode.CREATIVE) {
            return "creative-not-allowed";
        }
        return null;
    }

    /**
     * Closes an open backpack view that just became illegal (world change or
     * game mode change) and tells the player why.
     */
    private void enforceUsagePolicy(Player player, GameMode gameMode) {
        if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof BackpackInventoryHolder holder)
                || holder.getType() != BackpackInventoryHolder.Type.BACKPACK) {
            return;
        }
        String blocked = usageBlockReason(player, gameMode);
        if (blocked != null) {
            player.closeInventory();
            messages.send(player, blocked);
        }
    }

    @org.bukkit.event.EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        enforceUsagePolicy(event.getPlayer(), event.getPlayer().getGameMode());
    }

    @org.bukkit.event.EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        // The event fires before the change applies, so check the new mode.
        enforceUsagePolicy(event.getPlayer(), event.getNewGameMode());
    }

    public void openConfigGUI(Player player) {
        String blocked = usageBlockReason(player);
        if (blocked != null) {
            messages.send(player, blocked);
            return;
        }
        BackpackInventoryHolder holder = BackpackInventoryHolder.config();
        Inventory gui = Bukkit.createInventory(holder, 9, messages.component("gui-config-title"));
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
                    this.backpackName = MINI_MESSAGE.serialize(messages.component("gui-default-backpack-name"));
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
                // Never overwrite concurrent edits made by the owner or a team
                // member viewing the live backpack right now; their version wins
                if (isBackpackViewed(owner)) {
                    messages.send(viewer, "admin-edit-conflict");
                    logAudit("ADMIN_CONFLICT " + viewer.getName() + " -> " + owner.toString()
                            + " (live view open, changes discarded)");
                    return;
                }
                Inventory top = event.getInventory();
                // apply contents back to owner's stored backpack
                Inventory stored = backpacks.computeIfAbsent(owner, u -> loadBackpack(owner));
                if (stored.getSize() != top.getSize()) {
                    // The backpack was resized while the admin view was open;
                    // a partial copy would silently drop or resurrect slots.
                    messages.send(viewer, "admin-edit-conflict");
                    logAudit("ADMIN_CONFLICT " + viewer.getName() + " -> " + owner.toString()
                            + " (size changed from " + top.getSize() + " to "
                            + stored.getSize() + ", changes discarded)");
                    return;
                }
                for (int i = 0; i < Math.min(stored.getSize(), top.getSize()); i++) {
                    stored.setItem(i, top.getItem(i));
                }
                // optionally create a snapshot before saving (config: admin.auto-snapshot)
                boolean doSnapshot = plugin.getConfig().getBoolean("admin.auto-snapshot", true);
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

                saveInventoryAsync(owner, snapshot(stored), "admin", () -> { });
                logAudit("ADMIN_SAVE " + viewer.getName() + " -> " + owner.toString());
                return;
        }
        // If this was a normal backpack view, save owner's backpack on close;
        // the recent-save marker is only recorded once the write finished so
        // quit-time deduplication never skips while a write is still pending
        if (holder.getType() == BackpackInventoryHolder.Type.BACKPACK) {
            UUID owner = holder.getOwner();
            saveInventoryAsync(owner, snapshot(event.getInventory()), "close",
                    () -> recentSaveOwners.put(owner, System.currentTimeMillis()));
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

    /**
     * Removes the quitting player's backpacks from memory so long uptimes
     * don't accumulate inventories. A backpack stays cached while any other
     * online player still resolves to it (team or temporary share usage).
     */
    void unloadQuitBackpacks(Player quitting) {
        UUID quitId = quitting.getUniqueId();
        Set<UUID> candidates = new HashSet<>(Set.of(quitId, resolveEffectiveOwner(quitId)));
        for (UUID candidate : candidates) {
            if (!backpacks.containsKey(candidate)) {
                continue;
            }
            boolean used = false;
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(quitting)
                        && resolveEffectiveOwner(online.getUniqueId()).equals(candidate)) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                backpacks.remove(candidate);
            }
        }
    }

    /**
     * Returns whether any online player currently has the owner's live
     * backpack open in an editable view.
     */
    private boolean isBackpackViewed(UUID owner) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof BackpackInventoryHolder holder
                    && holder.getType() == BackpackInventoryHolder.Type.BACKPACK
                    && owner.equals(holder.getOwner())) {
                return true;
            }
        }
        return false;
    }

    private void saveInventoryAsync(UUID owner, ItemStack[] contents, String source, Runnable onComplete) {
        // The contents were snapshotted synchronously by the caller, so the
        // async write never races inventory mutations
        long sequence = nextSaveSequence(owner);
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            try {
                writeInventory(owner, contents, source, sequence);
            } catch (RuntimeException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Failed to save backpack " + owner + " from " + source, e);
            } finally {
                runOnMainThread(onComplete);
            }
        });
    }

    private long nextSaveSequence(UUID owner) {
        return saveSequences.merge(owner, 1L, Long::sum);
    }

    /**
     * Runs the callback on the main thread (global region scheduler) once a
     * pending async write finished; skipped while the plugin is disabling
     * because shutdown saves everything synchronously anyway.
     */
    private void runOnMainThread(Runnable callback) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().run(plugin, task -> callback.run());
    }

    private void writeInventory(UUID owner, ItemStack[] contents, String source, long sequence) {
        synchronized (saveIoLock) {
            Long latest = saveSequences.get(owner);
            if (latest != null && sequence < latest) {
                plugin.getLogger().fine("Skipping stale " + source + " save for backpack " + owner
                        + " (sequence " + sequence + " < " + latest + ").");
                return;
            }
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
                saveSequences.put(owner, sequence);
            } catch (IOException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Failed to save backpack " + owner + " from " + source, e);
            }
        }
    }

    /**
     * Cycles the title color through aqua, green and red. The input may be a
     * legacy or MiniMessage string; the result is always re-serialized as
     * MiniMessage.
     */
    private String cycleColor(String name) {
        Component current = Messages.deserialize(name);
        TextColor color = current.color();
        int index = color instanceof NamedTextColor named ? TITLE_COLOR_CYCLE.indexOf(named) : -1;
        NamedTextColor next = index < 0 ? TITLE_COLOR_CYCLE.get(0)
                : TITLE_COLOR_CYCLE.get((index + 1) % TITLE_COLOR_CYCLE.size());
        String baseName = PlainTextComponentSerializer.plainText().serialize(current);
        return MINI_MESSAGE.serialize(Component.text(baseName, next));
    }

    private void saveConfigValue(String path, Object value) {
        FileConfiguration config = plugin.getConfig();
        config.set(path, value);
        plugin.saveConfig();
    }

    /**
     * Converts a configured title (legacy or MiniMessage format) into an
     * Adventure component for inventory titles.
     */
    private Component titleComponent(String title) {
        return Messages.deserialize(title);
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
