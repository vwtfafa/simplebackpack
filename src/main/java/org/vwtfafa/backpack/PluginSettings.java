package org.vwtfafa.backpack;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * Snapshot of all configuration options, rebuilt by {@link #load} whenever
 * the configuration is (re)loaded. Only the global enable flag is mutable
 * at runtime; other changes require a reload or restart.
 */
final class PluginSettings {
    private static final String DEFAULT_BACKPACK_NAME = "<aqua>Simple Backpack";
    private static final int DEFAULT_BACKPACK_SIZE = 27;

    private String backpackName = DEFAULT_BACKPACK_NAME;
    private int backpackSize = DEFAULT_BACKPACK_SIZE;
    private Map<String, Integer> backpackPermissionSizes = Map.of();
    private boolean classicMode;
    private boolean teamEnabled = true;
    private boolean adminEnabled = true;
    private boolean adminGuiEnabled = true;
    private boolean adminAutoSnapshot = true;
    private boolean showTeamCommands = true;
    private boolean showAdminCommands = true;
    private boolean liveConfigReload = true;
    private boolean keepContentsOnDeath = true;
    private boolean autoSaveOnQuit = true;
    private boolean backpacksEnabled = true;
    private boolean allowInCreative;
    private boolean guiConfigurable = true;
    private boolean sharingEnabled = true;
    private int teamMaxSize = 5;
    private List<String> disabledWorlds = List.of();

    private PluginSettings() {
    }

    static PluginSettings load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        PluginSettings settings = new PluginSettings();

        settings.classicMode = config.getBoolean("classic-mode", false);
        settings.teamEnabled = config.getBoolean("team.enabled", true);
        settings.adminEnabled = config.getBoolean("admin.enabled", true);
        settings.adminGuiEnabled = config.getBoolean("admin.enable-gui", true);
        settings.adminAutoSnapshot = config.getBoolean("admin.auto-snapshot", true);
        settings.showTeamCommands = config.getBoolean("show-team-commands", true);
        settings.showAdminCommands = config.getBoolean("show-admin-commands", true);
        settings.liveConfigReload = config.getBoolean("live-config-reload", true);
        settings.keepContentsOnDeath = config.getBoolean("backpack.keep-on-death", true);
        settings.autoSaveOnQuit = config.getBoolean("backpack.auto-save-on-quit", true);
        settings.backpacksEnabled = config.getBoolean("backpacks-enabled", true);
        settings.allowInCreative = config.getBoolean("backpack.allow-in-creative", false);
        settings.guiConfigurable = config.getBoolean("backpack.gui-configurable", true);
        settings.sharingEnabled = config.getBoolean("enable-sharing", true);
        settings.teamMaxSize = Math.max(2, config.getInt("team.max-size", 5));
        Logger logger = plugin.getLogger();
        settings.backpackName = sanitizeBackpackName(logger, config.getString("backpack.name", settings.backpackName));
        settings.backpackSize = sanitizeBackpackSize(logger, config.getInt("backpack.size", settings.backpackSize));
        Map<String, Integer> permissionSizes = new HashMap<>();
        for (Map<?, ?> entry : config.getMapList("backpack.permission-sizes")) {
            Object permission = entry.get("permission");
            Object size = entry.get("size");
            if (permission instanceof String permissionName && size instanceof Number number) {
                permissionSizes.put(permissionName, number.intValue());
            } else {
                logger.warning("Ignoring invalid entry in backpack.permission-sizes; expected permission and size.");
            }
        }
        settings.backpackPermissionSizes = sanitizePermissionSizes(logger, permissionSizes);
        settings.disabledWorlds = List.copyOf(config.getStringList("backpack.disabled-worlds"));
        return settings;
    }

    /**
     * Rejects null, blank and otherwise unusable inventory titles at load
     * time so a bad {@code backpack.name} fails loudly on reload instead of
     * later inside Bukkit/Adventure calls.
     */
    static String sanitizeBackpackName(Logger logger, String name) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        logger.warning("Invalid backpack.name in config.yml; falling back to '%s'."
                .formatted(DEFAULT_BACKPACK_NAME));
        return DEFAULT_BACKPACK_NAME;
    }

    /**
     * Accepts only real inventory sizes (9-54 in steps of 9); anything else
     * falls back to the default instead of crashing {@code createInventory}.
     */
    static int sanitizeBackpackSize(Logger logger, int size) {
        if (isValidBackpackSize(size)) {
            return size;
        }
        logger.warning("Invalid backpack.size '%s' in config.yml; expected one of 9, 18, 27, 36, 45, 54. Falling back to %s."
                .formatted(size, DEFAULT_BACKPACK_SIZE));
        return DEFAULT_BACKPACK_SIZE;
    }

    static Map<String, Integer> sanitizePermissionSizes(Logger logger, Map<String, Integer> configuredSizes) {
        Map<String, Integer> sanitized = new HashMap<>();
        for (Map.Entry<String, Integer> entry : configuredSizes.entrySet()) {
            String permission = entry.getKey();
            Integer size = entry.getValue();
            if (permission == null || permission.isBlank() || size == null || !isValidBackpackSize(size)) {
                logger.warning(("Ignoring invalid backpack.permission-sizes entry for '%s'; expected a permission "
                        + "and a size of 9, 18, 27, 36, 45, or 54.").formatted(permission));
                continue;
            }
            sanitized.put(permission.strip(), size);
        }
        return Map.copyOf(sanitized);
    }

    static int resolveBackpackSize(int defaultSize, Map<String, Integer> permissionSizes,
                                   Predicate<String> hasPermission) {
        int resolvedSize = defaultSize;
        boolean hasSizePermission = false;
        for (Map.Entry<String, Integer> entry : permissionSizes.entrySet()) {
            if (hasPermission.test(entry.getKey()) && (!hasSizePermission || entry.getValue() > resolvedSize)) {
                resolvedSize = entry.getValue();
                hasSizePermission = true;
            }
        }
        return resolvedSize;
    }

    private static boolean isValidBackpackSize(int size) {
        return size >= 9 && size <= 54 && size % 9 == 0;
    }

    String backpackName() {
        return backpackName;
    }

    int backpackSize() {
        return backpackSize;
    }

    Map<String, Integer> backpackPermissionSizes() {
        return backpackPermissionSizes;
    }

    boolean classicMode() {
        return classicMode;
    }

    boolean teamEnabled() {
        return teamEnabled;
    }

    boolean adminEnabled() {
        return adminEnabled;
    }

    boolean adminGuiEnabled() {
        return adminGuiEnabled;
    }

    boolean adminAutoSnapshot() {
        return adminAutoSnapshot;
    }

    boolean showTeamCommands() {
        return showTeamCommands;
    }

    boolean showAdminCommands() {
        return showAdminCommands;
    }

    boolean liveConfigReload() {
        return liveConfigReload;
    }

    boolean keepContentsOnDeath() {
        return keepContentsOnDeath;
    }

    boolean autoSaveOnQuit() {
        return autoSaveOnQuit;
    }

    boolean backpacksEnabled() {
        return backpacksEnabled;
    }

    void setBackpacksEnabled(boolean enabled) {
        this.backpacksEnabled = enabled;
    }

    boolean allowInCreative() {
        return allowInCreative;
    }

    boolean guiConfigurable() {
        return guiConfigurable;
    }

    boolean sharingEnabled() {
        return sharingEnabled;
    }

    int teamMaxSize() {
        return teamMaxSize;
    }

    List<String> disabledWorlds() {
        return disabledWorlds;
    }

    /**
     * True when the world is listed by plain name or by namespaced key
     * ({@code minecraft:world_nether}) in {@code backpack.disabled-worlds}.
     */
    boolean isWorldDisabled(org.bukkit.World world) {
        if (world == null || disabledWorlds.isEmpty()) {
            return false;
        }
        String name = world.getName();
        String key = world.getKey().toString();
        for (String entry : disabledWorlds) {
            if (entry.equalsIgnoreCase(name) || entry.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }
}
