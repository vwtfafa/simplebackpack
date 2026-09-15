package org.vwtfafa.backpack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * Central access to localized player messages. Translations live in separate
 * files under {@code lang/messages_<code>.yml}; missing keys fall back to
 * English.
 */
public class Messages {
    private static final String LANG_DIR = "lang";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private final JavaPlugin plugin;
    private YamlConfiguration languageConfig;
    private YamlConfiguration fallbackConfig;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Re-reads the language settings and all translation files from disk.
     */
    public void reload() {
        FileConfiguration config = plugin.getConfig();
        migrateLegacyMessages(config);
        String configured = config.getString("language", DEFAULT_LANGUAGE);
        String language = configured != null ? configured.toLowerCase(Locale.ROOT) : DEFAULT_LANGUAGE;
        ensureLanguageFile(DEFAULT_LANGUAGE);
        ensureLanguageFile(language);
        languageConfig = load(language);
        fallbackConfig = load(DEFAULT_LANGUAGE);
    }

    /**
     * Deserializes a configured string that may use either MiniMessage tags
     * or legacy section-sign color codes into an Adventure component, keeping
     * configs from older versions working unchanged.
     */
    public static Component deserialize(String raw) {
        if (raw.indexOf('§') >= 0) {
            return LEGACY_SECTION.deserialize(raw);
        }
        return MINI_MESSAGE.deserialize(raw);
    }

    /**
     * Returns the localized message for a key, falling back to English,
     * or an empty string if the key is unknown.
     */
    public String get(String key) {
        String message = languageConfig != null ? languageConfig.getString(key) : null;
        if ((message == null || message.isEmpty()) && fallbackConfig != null) {
            message = fallbackConfig.getString(key);
        }
        return message != null ? message : "";
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("messages-enabled", true);
    }

    /**
     * Substitutes {placeholder} pairs in a raw message, in order.
     */
    private String format(String message, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    /**
     * Sends a localized message, replacing {placeholder} pairs in order.
     */
    public void send(CommandSender recipient, String key, String... replacements) {
        if (!isEnabled()) {
            return;
        }
        String message = get(key);
        if (message.isEmpty()) {
            return;
        }
        recipient.sendMessage(deserialize(format(message, replacements)));
    }

    /**
     * Builds a localized component for UI labels such as inventory titles.
     * Unlike {@link #send}, this ignores the global messages toggle so
     * interface elements keep working when chat messages are disabled.
     */
    public Component component(String key, String... replacements) {
        String message = get(key);
        if (message.isEmpty()) {
            return Component.empty();
        }
        return deserialize(format(message, replacements));
    }

    /**
     * Moves a legacy {@code messages} section from config.yml into
     * per-language files once, preserving customizations from older versions.
     */
    private void migrateLegacyMessages(FileConfiguration config) {
        ConfigurationSection legacy = config.getConfigurationSection("messages");
        if (legacy == null || !legacy.contains(DEFAULT_LANGUAGE)) {
            return;
        }
        for (String code : legacy.getKeys(false)) {
            ConfigurationSection entries = legacy.getConfigurationSection(code);
            File target = languageFile(code.toLowerCase(Locale.ROOT));
            if (entries == null || target.exists()) {
                continue;
            }
            YamlConfiguration out = new YamlConfiguration();
            for (String key : entries.getKeys(true)) {
                out.set(key, entries.get(key));
            }
            try {
                out.save(target);
                plugin.getLogger().info("Migrated legacy messages." + code + " to " + target.getPath());
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to migrate legacy messages." + code + ": " + e.getMessage());
            }
        }
        config.set("messages", null);
        plugin.saveConfig();
    }

    /**
     * Extracts the bundled translation for a language unless the file already
     * exists on disk or the language has no bundled file (custom languages).
     */
    private void ensureLanguageFile(String code) {
        if (languageFile(code).exists()) {
            return;
        }
        try {
            plugin.saveResource(LANG_DIR + "/messages_" + code + ".yml", false);
        } catch (IllegalArgumentException ignored) {
            // No bundled translation for this code; admins may add their own file
        }
    }

    private YamlConfiguration load(String code) {
        return YamlConfiguration.loadConfiguration(languageFile(code));
    }

    private File languageFile(String code) {
        return new File(plugin.getDataFolder(), LANG_DIR + "/messages_" + code + ".yml");
    }
}
