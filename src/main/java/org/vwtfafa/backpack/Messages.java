package org.vwtfafa.backpack;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Central access to localized player messages with English fallback.
 */
public class Messages {
    private final JavaPlugin plugin;
    private String language;
    private boolean enabled;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Re-reads the language and messaging settings from the config.
     */
    public void reload() {
        enabled = plugin.getConfig().getBoolean("messages-enabled", true);
        language = "de".equalsIgnoreCase(plugin.getConfig().getString("language", "en")) ? "de" : "en";
    }

    /**
     * Returns the raw localized message for a key, falling back to English,
     * or an empty string if messaging is disabled or the key is unknown.
     */
    public String get(String key) {
        if (!enabled) {
            return "";
        }
        String message = plugin.getConfig().getString("messages." + language + "." + key);
        if (message == null || message.isEmpty()) {
            message = plugin.getConfig().getString("messages.en." + key, "");
        }
        return message != null ? message : "";
    }

    /**
     * Sends a localized message, replacing {placeholder} pairs in order.
     */
    public void send(CommandSender recipient, String key, String... replacements) {
        String message = get(key);
        if (message.isEmpty()) {
            return;
        }
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }
        recipient.sendMessage(message);
    }
}
