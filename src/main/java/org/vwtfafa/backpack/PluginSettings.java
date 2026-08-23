package org.vwtfafa.backpack;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Snapshot of all configuration options, rebuilt by {@link #load} whenever
 * the configuration is (re)loaded. Only the global enable flag is mutable
 * at runtime; other changes require a reload or restart.
 */
final class PluginSettings {
    private Locale locale = Locale.ENGLISH;
    private String backpackName = "<aqua>Simple Backpack";
    private int backpackSize = 27;
    private boolean classicMode;
    private boolean teamEnabled = true;
    private boolean adminEnabled = true;
    private boolean adminGuiEnabled = true;
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

        String language = config.getString("language", "en");
        if ("de".equalsIgnoreCase(language)) {
            settings.locale = Locale.GERMAN;
        }
        settings.classicMode = config.getBoolean("classic-mode", false);
        settings.teamEnabled = config.getBoolean("team.enabled", true);
        settings.adminEnabled = config.getBoolean("admin.enabled", true);
        settings.adminGuiEnabled = config.getBoolean("admin.enable-gui", true);
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
        settings.backpackName = config.getString("backpack.name", settings.backpackName);
        settings.backpackSize = config.getInt("backpack.size", settings.backpackSize);
        settings.disabledWorlds = List.copyOf(config.getStringList("backpack.disabled-worlds"));
        return settings;
    }

    Locale locale() {
        return locale;
    }

    String backpackName() {
        return backpackName;
    }

    int backpackSize() {
        return backpackSize;
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
}
