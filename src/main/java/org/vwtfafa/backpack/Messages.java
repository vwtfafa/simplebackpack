package org.vwtfafa.backpack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;

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
    private static final Pattern BRACE_PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");

    private final JavaPlugin plugin;
    private YamlConfiguration languageConfig;
    private YamlConfiguration fallbackConfig;
    private String language = DEFAULT_LANGUAGE;

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
        this.language = language;
        ensureLanguageFile(DEFAULT_LANGUAGE);
        ensureLanguageFile(language);
        languageConfig = load(language);
        fallbackConfig = load(DEFAULT_LANGUAGE);
    }

    /**
     * Deserializes a configured string that may use either MiniMessage tags
     * or legacy section-sign color codes into an Adventure component, keeping
     * configs from older versions working unchanged. Never throws: null, empty
     * and unparseable input degrade to an empty or plain-text component so a
     * single bad lang entry cannot break commands or inventory titles.
     */
    public static Component deserialize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }
        try {
            if (raw.indexOf('§') >= 0) {
                return LEGACY_SECTION.deserialize(raw);
            }
            return MINI_MESSAGE.deserialize(raw);
        } catch (RuntimeException e) {
            return Component.text(raw);
        }
    }

    /**
     * Returns the localized message for a key, falling back to English,
     * or an empty string if the key is unknown. Unknown keys are logged so
     * typos or outdated {@code messages_*.yml} files stay visible instead of
     * failing silently.
     */
    public String get(String key) {
        String message = languageConfig != null ? languageConfig.getString(key) : null;
        if ((message == null || message.isEmpty()) && fallbackConfig != null) {
            message = fallbackConfig.getString(key);
        }
        if (message == null) {
            plugin.getLogger().warning(
                    "Missing message key '%s' in lang/messages_%s.yml and fallback; check your lang files."
                            .formatted(key, configuredLanguage()));
            return "";
        }
        return message;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("messages-enabled", true);
    }

    /**
     * Substitutes {placeholder} pairs in a raw message, in order.
     * Only used for the legacy color-code path; MiniMessage templates go
     * through {@link #renderTemplate} with a {@link TagResolver} instead.
     */
    private static String substitute(String message, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        return message;
    }

    /**
     * Renders a message template with {placeholder} values. MiniMessage
     * templates are parsed with the values bound as unparsed placeholders, so
     * user-supplied text (e.g. player names containing {@code <...>}) can
     * never inject formatting. Legacy color-code templates keep the previous
     * plain string substitution. Never throws.
     */
    static Component renderTemplate(String template, String... replacements) {
        if (template.indexOf('§') >= 0) {
            return LEGACY_SECTION.deserialize(substitute(template, replacements));
        }
        try {
            String tagged = BRACE_PLACEHOLDER.matcher(template).replaceAll("<$1>");
            return MINI_MESSAGE.deserialize(tagged, toResolver(replacements));
        } catch (RuntimeException e) {
            return deserialize(substitute(template, replacements));
        }
    }

    private static TagResolver toResolver(String... replacements) {
        TagResolver.Builder tags = TagResolver.builder();
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String name = replacements[i];
            if (name.startsWith("{") && name.endsWith("}") && name.length() > 2) {
                name = name.substring(1, name.length() - 1);
            }
            tags.resolver(Placeholder.unparsed(name, replacements[i + 1]));
        }
        return tags.build();
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
        recipient.sendMessage(renderTemplate(message, replacements));
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
        return renderTemplate(message, replacements);
    }

    private String configuredLanguage() {
        return language;
    }

    /**
     * Moves a legacy {@code messages} section from config.yml into
     * per-language files once, preserving customizations from older versions.
     */
    private void migrateLegacyMessages(FileConfiguration config) {
        ConfigurationSection legacy = config.getConfigurationSection("messages");
        if (legacy == null) {
            return;
        }
        for (String code : legacy.getKeys(false)) {
            ConfigurationSection entries = legacy.getConfigurationSection(code);
            String normalized = code.toLowerCase(Locale.ROOT);
            if (entries == null) {
                legacy.set(code, null);
                continue;
            }
            File target = languageFile(normalized);
            if (target.exists()) {
                // Keep the legacy section so customized entries are not lost;
                // they can be merged into the lang file manually.
                plugin.getLogger().warning("Legacy messages.%s kept in config.yml because %s already exists."
                        .formatted(code, target.getPath()));
                continue;
            }
            YamlConfiguration out = new YamlConfiguration();
            for (String key : entries.getKeys(true)) {
                out.set(key, entries.get(key));
            }
            try {
                out.save(target);
                plugin.getLogger().info("Migrated legacy messages.%s to %s".formatted(code, target.getPath()));
                legacy.set(code, null);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to migrate legacy messages.%s: %s".formatted(code, e.getMessage()));
            }
        }
        if (legacy.getKeys(false).isEmpty()) {
            config.set("messages", null);
        }
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
