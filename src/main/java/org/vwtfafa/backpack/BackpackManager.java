package org.vwtfafa.backpack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class BackpackManager implements Listener {
    private static final int MIN_BACKPACK_SIZE = 9;
    private static final int MAX_BACKPACK_SIZE = 54;
    private static final int MAX_NAME_LENGTH = 64;
    private static final int STORAGE_VERSION = 1;
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
    // Cached admin.auto-snapshot flag; snapshots are taken inside the async
    // write itself so admin edits never block the main thread with file I/O.
    private volatile boolean adminSnapshotEnabled = true;
    Map<UUID, SharedSession> sharedSessions = new ConcurrentHashMap<>();
    // Owners whose backpack an inventory-close event just persisted; lets the
    // quit-time autosave skip the redundant second write on disconnects
    private final Map<UUID, Long> recentSaveOwners = new ConcurrentHashMap<>();
    private static final long RECENT_SAVE_WINDOW_MILLIS = 2000L;
    // Highest write sequence that finished writing per owner; lets the
    // quit-time deduplication verify that no write is still in flight
    private final Map<UUID, Long> completedSaveSequences = new ConcurrentHashMap<>();
    private final File auditLogFile;
    // Guards all inventory file writes: prevents interleaved temp-file writes
    // without keeping an ever-growing per-owner lock map
    private final Object saveIoLock = new Object();
    // Monotonic write sequence per owner: async writes carry the sequence from
    // scheduling time, so a stale write (e.g. a close-save snapshotted before
    // a death-clear) is dropped instead of resurrecting deleted items, and the
    // synchronous shutdown save always wins over still-running async writes.
    private final Map<UUID, Long> saveSequences = new ConcurrentHashMap<>();
    // Monotonic revision per owner, bumped whenever the stored backpack
    // changes (saves, resizes, clears, transfers). Admin edit views remember
    // the revision they opened with and discard their changes when it moved
    // on, so a stale copy can never roll back newer contents.
    private final Map<UUID, Long> backpackRevisions = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNameCache = new ConcurrentHashMap<>();

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
        // Opening makes the inventory editable again; the dedup marker of a
        // previous close save must not skip the save of later changes.
        recentSaveOwners.remove(resolveEffectiveOwner(player.getUniqueId()));
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
            return loadBackpackSafely(session.owner());
        }
        if (teamEnabled) {
            UUID teamOwner = getTeamOwner(uuid);
            if (!teamOwner.equals(uuid)) {
                return loadBackpackSafely(teamOwner);
            }
        }
        return loadBackpackSafely(uuid);
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
        sharedSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    public void updateBackpackGUI(Player player) {
        // Recreate inventory with new size or name
        // Resolve the effective owner to maintain team/share integrity
        UUID effectiveOwner = resolveEffectiveOwner(player.getUniqueId());
        Inventory oldInv = loadBackpackSafely(effectiveOwner);
        // Rebuilding the backpack changes the stored state: admin edit
        // copies opened earlier are invalidated and discard their changes
        // when they are closed.
        nextRevision(effectiveOwner);
        // Close every open view of the old inventory before swapping so no
        // viewer keeps editing a detached inventory that would later be saved
        // over the resized one (split-brain data loss)
        List<Player> viewers = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (oldInv.equals(online.getOpenInventory().getTopInventory())) {
                viewers.add(online);
            }
        }
        for (Player viewer : viewers) {
            viewer.closeInventory();
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
        Inventory oldInv = loadBackpackSafely(effectiveOwner);
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
     * Resolves the backpack a player owns: their team's backpack when they
     * are in a team, otherwise their own. Unlike {@link #resolveEffectiveOwner}
     * this ignores temporary share sessions, so admin actions like clearing
     * never touch a backpack the player may only be viewing through a share.
     */
    public UUID resolveOwnedBackpack(UUID playerId) {
        if (teamEnabled) {
            UUID teamOwner = getTeamOwner(playerId);
            if (!teamOwner.equals(playerId)) {
                return teamOwner;
            }
        }
        return playerId;
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
     * when the backpack got loaded meanwhile.
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
     * Reads all stored slots off the main thread without touching
     * inventories; returns null only when the file cannot be read at all
     * (the caller then falls back to the blocking load path).
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
        backpacks.put(ownerId, installContents(ownerId, contents));
    }

    /**
     * Builds the runtime inventory of a backpack from stored contents:
     * items up to the configured size are placed directly, items from
     * removed slots are re-added to free slots or the overflow sidecar.
     * The join preload and the on-demand load share this path so recovery
     * never depends on which one ran first.
     */
    private Inventory installContents(UUID ownerId, ItemStack[] contents) {
        BackpackInventoryHolder holder = BackpackInventoryHolder.backpack(ownerId);
        Inventory inv = Bukkit.createInventory(holder, backpackSize, titleComponent(backpackName));
        holder.setInventory(inv);
        List<ItemStack> homeless = new ArrayList<>();
        for (int i = 0; i < Math.min(contents.length, MAX_BACKPACK_SIZE); i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (i < backpackSize) {
                inv.setItem(i, item);
            } else {
                homeless.add(item);
            }
        }
        // Recover items stored in slots that no longer exist because the
        // configured size was reduced; never silently drop them
        Map<Integer, ItemStack> leftover = homeless.isEmpty()
                ? Map.of()
                : inv.addItem(homeless.toArray(new ItemStack[0]));
        if (!leftover.isEmpty()) {
            storeOverflow(ownerId, leftover.values());
        } else {
            // Nothing homeless from the main file: drain a previous sidecar
            // now that space may have freed up (e.g. size was increased
            // again).
            restoreOverflow(ownerId, inv);
        }
        nextRevision(ownerId);
        return inv;
    }

    /**
     * Returns the owner's backpack, loading it from disk when it is not
     * cached yet. GUI updates and shrink checks must never treat a
     * not-yet-loaded backpack as empty, otherwise a fresh empty inventory
     * would be stored over the player's items.
     */
    private Inventory loadBackpackSafely(UUID owner) {
        Inventory cached = backpacks.get(owner);
        if (cached != null) {
            return cached;
        }
        Inventory loaded = loadBackpack(owner);
        Inventory winner = backpacks.putIfAbsent(owner, loaded);
        return winner != null ? winner : loaded;
    }

    private Inventory loadBackpack(UUID uuid) {
        File file = new File(dataFolder, uuid + ".yml");
        ItemStack[] contents = readInventoryFile(file);
        if (contents == null) {
            contents = new ItemStack[MAX_BACKPACK_SIZE];
        }
        return installContents(uuid, contents);
    }

    /**
     * Re-ingests a previously written {@code *.overflow.yml} sidecar into a
     * freshly loaded backpack. Items that fit are restored and persisted
     * before the sidecar is dropped; anything still not fitting is kept in
     * a rewritten sidecar so repeated shrinks never silently eat items.
     * Unreadable sidecars are preserved as {@code .corrupt-<ts>} files
     * instead of being deleted.
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
            if (sidecar.length() == 0 && sidecar.delete()) {
                plugin.getLogger().info("Removed empty overflow file for backpack " + uuid + ".");
            } else if (sidecar.length() > 0) {
                // A non-empty file that yields no items may be corrupt or
                // half-written; keep it for manual recovery instead of
                // silently deleting stored items.
                File kept = new File(dataFolder, uuid + ".overflow.yml.corrupt-" + System.currentTimeMillis());
                try {
                    Files.move(sidecar.toPath(), kept.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().warning("Unreadable overflow file for backpack %s kept as %s."
                            .formatted(uuid, kept.getName()));
                    logAudit("OVERFLOW_CORRUPT " + uuid + " -> " + kept.getName());
                } catch (IOException e) {
                    plugin.getLogger().severe("Failed to preserve unreadable overflow file for backpack %s: %s"
                            .formatted(uuid, e.getMessage()));
                }
            }
            return;
        }
        Map<Integer, ItemStack> rest = inv.addItem(stored.toArray(new ItemStack[0]));
        if (rest.isEmpty()) {
            // Persist the restored items before dropping the sidecar so a
            // crash cannot lose them.
            long sequence = nextSaveSequence(uuid);
            if (writeInventory(uuid, snapshot(inv), "overflow-restore", sequence) && sidecar.delete()) {
                plugin.getLogger().info("Restored %s overflow item(s) into backpack %s."
                        .formatted(stored.size(), uuid));
                logAudit("OVERFLOW_RESTORED " + uuid + " (" + stored.size() + " items)");
            }
            return;
        }
        Player ownerPlayer = Bukkit.getPlayer(uuid);
        if (ownerPlayer != null) {
            messages.send(ownerPlayer, "backpack-full", "{size}", String.valueOf(rest.size()));
        }
        YamlConfiguration rewritten = new YamlConfiguration();
        rewritten.set("version", STORAGE_VERSION);
        int slot = 0;
        for (ItemStack item : rest.values()) {
            rewritten.set("slot" + slot++, item);
        }
        try {
            writeYamlFile(rewritten, sidecar);
            plugin.getLogger().warning("Restored %s overflow item(s) into backpack %s; %s item(s) remain in %s."
                    .formatted(stored.size() - rest.size(), uuid, rest.size(), sidecar.getName()));
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to rewrite overflow file for backpack %s: %s"
                    .formatted(uuid, e.getMessage()));
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
        overflowConfig.set("version", STORAGE_VERSION);
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
            writeYamlFile(overflowConfig, overflowFile);
            plugin.getLogger().warning("Backpack %s shrank below its stored contents; %s item(s) saved to %s."
                    .formatted(owner, items.size(), overflowFile.getName()));
            logAudit("OVERFLOW " + owner.toString() + " -> " + overflowFile.getName()
                    + " (" + items.size() + " items)");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to store overflowing backpack contents of %s: %s"
                    .formatted(owner, e.getMessage()));
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
        Long lastSave = recentSaveOwners.get(owner);
        Long completed = completedSaveSequences.get(owner);
        Long latest = saveSequences.get(owner);
        // Skip only when the previous close save is known to have finished
        // and nothing was written since; otherwise newer changes would never
        // reach disk when the server stops before the next save runs.
        if (lastSave != null && completed != null && completed.equals(latest)
                && System.currentTimeMillis() - lastSave <= RECENT_SAVE_WINDOW_MILLIS) {
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
                try (FileWriter fw = new FileWriter(auditLogFile, java.nio.charset.StandardCharsets.UTF_8, true);
                     PrintWriter pw = new PrintWriter(fw)) {
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

    /**
     * Resolves a player name with a small in-memory cache so team info and
     * the admin GUI do not hit the offline-player store dozens of times per
     * page. Never returns null: unknown players fall back to their UUID.
     * Refreshed on every player join.
     */
    public String playerName(UUID uuid) {
        String cached = playerNameCache.get(uuid);
        if (cached != null) {
            return cached;
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null) {
            return uuid.toString();
        }
        playerNameCache.put(uuid, name);
        return name;
    }

    /**
     * Refreshes a cached player name, e.g. after a rename.
     */
    public void refreshPlayerName(UUID uuid, String name) {
        if (name != null) {
            playerNameCache.put(uuid, name);
        }
    }

    // Admin opens a target backpack; preview=true -> read-only
    public void openForAdmin(UUID owner, Player admin, boolean preview) {
        Inventory inv = loadBackpackSafely(owner);
        // open a new inventory view for admin with same contents; the
        // revision ties the view to the state it was created from
        BackpackInventoryHolder holder = BackpackInventoryHolder.admin(owner, preview, currentRevision(owner));
        String ownerName = playerName(owner);
        Component title = messages.component("gui-admin-view-title",
                "{player}", ownerName);
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
     * Wipes the backpack the given player actually owns on behalf of an
     * admin (the team backpack for team members, never a backpack the
     * player only has temporary share access to) and persists the empty
     * state asynchronously. Returns the owner whose backpack was cleared
     * so callers can report the correct name.
     */
    public UUID clearForAdmin(UUID targetId, org.bukkit.command.CommandSender admin) {
        UUID ownerId = resolveOwnedBackpack(targetId);
        Inventory inv = loadBackpackSafely(ownerId);
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
        saveInventoryAsync(ownerId, snapshot(inv), "admin-clear", () -> { });
        logAudit("ADMIN_CLEAR " + admin.getName() + " -> " + ownerId.toString());
        return ownerId;
    }

    /**
     * Moves the team backpack from the leaving owner to their successor:
     * open views of both involved backpacks are closed, the newest cached
     * state is persisted, and the storage files are rekeyed so the team
     * keeps its contents. A pre-existing file of the successor is preserved
     * as a {@code .bak} backup before it is replaced.
     */
    public void transferBackpackOwnership(UUID oldOwner, UUID newOwner) {
        if (oldOwner == null || newOwner == null || oldOwner.equals(newOwner)) {
            return;
        }
        // Views hold detached copies; close them so no later save can
        // resurrect a backpack under its previous owner (the successor's
        // stale personal backpack included).
        Inventory cached = backpacks.get(oldOwner);
        Inventory successorStale = backpacks.get(newOwner);
        for (Player online : Bukkit.getOnlinePlayers()) {
            Inventory top = online.getOpenInventory().getTopInventory();
            if ((cached != null && cached.equals(top))
                    || (successorStale != null && successorStale.equals(top))) {
                online.closeInventory();
            }
        }
        synchronized (saveIoLock) {
            // Persist the newest cached state under the old owner first, then
            // invalidate queued writes for both owners so they cannot
            // recreate the old files or overwrite the moved one.
            long sequence = nextSaveSequence(oldOwner);
            nextSaveSequence(newOwner);
            nextRevision(oldOwner);
            nextRevision(newOwner);
            Inventory stored = backpacks.remove(oldOwner);
            backpacks.remove(newOwner);
            if (stored != null) {
                writeInventory(oldOwner, snapshot(stored), "transfer", sequence);
            }
            moveStorageFile(new File(dataFolder, oldOwner + ".yml"),
                    new File(dataFolder, newOwner + ".yml"));
            moveStorageFile(new File(dataFolder, oldOwner + ".overflow.yml"),
                    new File(dataFolder, newOwner + ".overflow.yml"));
        }
        logAudit("TRANSFER " + oldOwner + " -> " + newOwner);
    }

    /**
     * Moves a backpack storage file (main or overflow) to the new owner,
     * keeping an existing target file as a {@code .bak} first. Missing
     * sources are ignored.
     */
    private void moveStorageFile(File source, File target) {
        if (!source.exists()) {
            return;
        }
        try {
            if (target.exists()) {
                Files.move(target.toPath(),
                        new File(target.getParentFile(), target.getName() + ".bak").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(source.toPath(), target.toPath());
            }
        } catch (IOException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Failed to move backpack file " + source.getName() + " to " + target.getName(), e);
        }
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
        // The policy may just have become stricter (disable command, reload
        // with new disabled worlds); close open user views that are illegal
        // now instead of letting them stay usable.
        for (Player online : Bukkit.getOnlinePlayers()) {
            String blocked = usageBlockReason(online);
            if (blocked == null
                    || !(online.getOpenInventory().getTopInventory().getHolder()
                    instanceof BackpackInventoryHolder holder)) {
                continue;
            }
            if (holder.getType() == BackpackInventoryHolder.Type.BACKPACK
                    || holder.getType() == BackpackInventoryHolder.Type.CONFIG) {
                online.closeInventory();
                messages.send(online, blocked);
            }
        }
    }

    /**
     * Refreshes the cached admin.auto-snapshot flag after (re)loads.
     */
    public void setAdminSnapshotEnabled(boolean enabled) {
        this.adminSnapshotEnabled = enabled;
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
                case 0 -> {
                    // Rename via dialog text input; the dialog replaces the
                    // inventory screen, so skip the trailing closeInventory
                    openNameDialog(player);
                    return;
                }
                case 1 -> {
                    // Farbe ändern (cycle: Aqua -> Green -> Red -> Aqua ...)
                    this.backpackName = cycleColor(this.backpackName);
                    saveConfigValue("backpack.name", this.backpackName);
                    updateBackpackGUI(player);
                    messages.send(player, "config-changed-color");
                }
                case 2 -> {
                    // Größe ändern (cycle)
                    int newSize = (this.backpackSize == MAX_BACKPACK_SIZE)
                        ? MIN_BACKPACK_SIZE
                        : this.backpackSize + MIN_BACKPACK_SIZE;
                    if (canShrinkSafely(player, validateBackpackSize(newSize))) {
                        this.backpackSize = validateBackpackSize(newSize);
                        saveConfigValue("backpack.size", this.backpackSize);
                        updateBackpackGUI(player);
                        messages.send(player, "config-changed-size");
                    } else {
                        messages.send(player, "resize-no-space");
                    }
                }
                default -> { }
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
        if (!(event.getPlayer() instanceof Player viewer)) {
            return;
        }
        if (!(event.getView().getTopInventory().getHolder() instanceof BackpackInventoryHolder holder)) return;
        if (holder.getType() == BackpackInventoryHolder.Type.CONFIG || holder.isPreview()) return;
        // If admin was editing a "Backpack: <uuid>" view, persist changes
        if (holder.getType() == BackpackInventoryHolder.Type.ADMIN) {
                UUID owner = holder.getOwner();
                // Never overwrite concurrent edits made by the owner or a team
                // member viewing the live backpack right now; their version wins.
                // The revision check covers changes that were already stored
                // while this edit view was open (close saves, resizes, clears).
                if (isBackpackViewed(owner) || holder.getRevision() != currentRevision(owner)) {
                    messages.send(viewer, "admin-edit-conflict", "{player}", playerName(owner));
                    logAudit("ADMIN_CONFLICT " + viewer.getName() + " -> " + owner.toString()
                            + " (newer state exists, changes discarded)");
                    return;
                }
                Inventory top = event.getInventory();
                // apply contents back to owner's stored backpack
                Inventory stored = loadBackpackSafely(owner);
                if (stored.getSize() != top.getSize()) {
                    // The backpack was resized while the admin view was open;
                    // a partial copy would silently drop or resurrect slots.
                    messages.send(viewer, "admin-edit-conflict", "{player}", playerName(owner));
                    logAudit("ADMIN_CONFLICT " + viewer.getName() + " -> " + owner.toString()
                            + " (size changed from " + top.getSize() + " to "
                            + stored.getSize() + ", changes discarded)");
                    return;
                }
                for (int i = 0; i < Math.min(stored.getSize(), top.getSize()); i++) {
                    stored.setItem(i, top.getItem(i));
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
                completedSaveSequences.remove(candidate);
                backpackRevisions.remove(candidate);
                recentSaveOwners.remove(candidate);
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
        // The stored state changes as of now; invalidate admin edit views
        nextRevision(owner);
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

    private long nextRevision(UUID owner) {
        return backpackRevisions.merge(owner, 1L, Long::sum);
    }

    private long currentRevision(UUID owner) {
        return backpackRevisions.getOrDefault(owner, 0L);
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

    /**
     * Writes the backpack file through a temp file and an atomic move.
     * Returns false when the write was skipped because a newer write already
     * happened, or when it failed.
     */
    private boolean writeInventory(UUID owner, ItemStack[] contents, String source, long sequence) {
        synchronized (saveIoLock) {
            Long latest = saveSequences.get(owner);
            if (latest != null && sequence < latest) {
                plugin.getLogger().fine("Skipping stale " + source + " save for backpack " + owner
                        + " (sequence " + sequence + " < " + latest + ").");
                return false;
            }
            File file = new File(dataFolder, owner + ".yml");
            try {
                if (adminSnapshotEnabled && source.startsWith("admin")) {
                    createSnapshot(owner);
                }
                YamlConfiguration config = new YamlConfiguration();
                config.set("version", STORAGE_VERSION);
                for (int i = 0; i < contents.length; i++) config.set("slot" + i, contents[i]);
                writeYamlFile(config, file);
                saveSequences.put(owner, sequence);
                completedSaveSequences.merge(owner, sequence, Math::max);
                return true;
            } catch (IOException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Failed to save backpack " + owner + " from " + source, e);
                return false;
            }
        }
    }

    /**
     * Keeps a timestamped copy of the current backpack file before an admin
     * write replaces it. Runs inside the save lock (on the async write
     * thread) so the snapshot always captures the state before this write.
     */
    private void createSnapshot(UUID owner) {
        File source = new File(dataFolder, owner + ".yml");
        if (!source.exists()) {
            return;
        }
        File snapshotsDir = new File(plugin.getDataFolder(), "backups/snapshots");
        if (!snapshotsDir.exists() && !snapshotsDir.mkdirs()) {
            plugin.getLogger().warning("Failed to create snapshot directory: " + snapshotsDir);
            return;
        }
        File target = new File(snapshotsDir, owner + "-" + System.currentTimeMillis() + ".yml");
        try {
            Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logAudit("SNAPSHOT " + owner + " -> " + target.getName());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to create snapshot for backpack " + owner
                    + ": " + e.getMessage());
        }
    }

    /**
     * Writes a YAML file through a temp file and an atomic move so a crash
     * mid-write cannot corrupt the previous contents.
     */
    private void writeYamlFile(YamlConfiguration config, File file) throws IOException {
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        config.save(temporary);
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Opens a dialog with a text input for the backpack name, prefilled with
     * the current plain-text title so MiniMessage tags are not exposed raw.
     */
    private void openNameDialog(Player player) {
        String plainCurrent =
                PlainTextComponentSerializer.plainText().serialize(Messages.deserialize(backpackName));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(messages.component("gui-dialog-name-title"))
                        .inputs(List.of(DialogInput.text("name", 200,
                                messages.component("gui-dialog-name-label"), false,
                                plainCurrent, MAX_NAME_LENGTH, null)))
                        .build())
                .type(DialogType.confirmation(
                        ActionButton.create(messages.component("gui-dialog-confirm"), null, 100,
                                DialogAction.customClick(
                                        (response, audience) -> applyDialogName(player, response),
                                        ClickCallback.Options.builder().uses(1).build())),
                        ActionButton.create(messages.component("gui-dialog-cancel"), null, 100, null))));
        player.closeInventory();
        player.showDialog(dialog);
    }

    /**
     * Applies a confirmed dialog name: blank input is rejected, anything else
     * is stored raw (MiniMessage and legacy colors keep working in titles).
     */
    private void applyDialogName(Player player, DialogResponseView response) {
        if (!player.isOnline()) {
            return;
        }
        String input = response == null ? null : response.getText("name");
        if (input == null || input.isBlank()) {
            messages.send(player, "config-invalid-name");
            return;
        }
        String name = input.strip();
        if (name.length() > MAX_NAME_LENGTH) {
            name = name.substring(0, MAX_NAME_LENGTH);
        }
        this.backpackName = name;
        saveConfigValue("backpack.name", this.backpackName);
        updateBackpackGUI(player);
        messages.send(player, "config-changed-name");
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
