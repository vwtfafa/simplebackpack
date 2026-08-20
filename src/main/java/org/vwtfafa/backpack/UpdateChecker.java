package org.vwtfafa.backpack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Checks for updates on GitHub and notifies operators.
 */
public class UpdateChecker {
    private final JavaPlugin plugin;
    private final String currentVersion;
    private String latestVersion = null;
    private boolean updateAvailable = false;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getDescription().getVersion();
    }

    /**
     * Loads the latest version from GitHub asynchronously and notifies ops
     */
    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Fetch the latest version from GitHub API
                URL url = new URL("https://api.github.com/repos/vwtfafa/SimpleBackpack/releases/latest");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setRequestProperty("User-Agent", "SimpleBackpack/" + currentVersion);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                if (connection.getResponseCode() / 100 != 2) {
                    throw new java.io.IOException("GitHub returned HTTP " + connection.getResponseCode());
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                JSONObject json = new JSONObject(response.toString());
                if (json.has("tag_name")) {
                    latestVersion = json.getString("tag_name");
                    updateAvailable = isNewerVersion(latestVersion, currentVersion);

                    if (updateAvailable) {
                        plugin.getLogger().info("========================================");
                        plugin.getLogger().info("SimpleBackpack update available!");
                        plugin.getLogger().info("Current version: " + currentVersion);
                        plugin.getLogger().info("New version: " + latestVersion);
                        plugin.getLogger().info("Release page: https://github.com/vwtfafa/SimpleBackpack/releases");
                        plugin.getLogger().info("========================================");

                        // Notify online operators
                        notifyOps();
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Update-Check fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    /**
     * Sends a chat notification to online operators about available updates
     */
    private void notifyOps() {
        boolean notifyOps = plugin.getConfig().getBoolean("update-checker.notify-ops", true);
        boolean notifyChat = plugin.getConfig().getBoolean("update-checker.notify-chat", true);

        if (!notifyOps || !notifyChat) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            String releaseUrl = "https://github.com/vwtfafa/SimpleBackpack/releases";
            Component message = Component.text("[SimpleBackpack] Update available: " + latestVersion + " - Open release page")
                    .color(net.kyori.adventure.text.format.NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.openUrl(releaseUrl))
                    .hoverEvent(HoverEvent.showText(Component.text("Open the latest release page")));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isOp() || player.hasPermission("simplebackpack.admin")) {
                    player.sendMessage(message);
                }
            }
        });
    }

    /**
     * Compares two version strings
     */
    private boolean isNewerVersion(String newVersion, String currentVersion) {
        try {
            // Remove 'v' prefix if present
            newVersion = newVersion.replaceFirst("^v", "");
            currentVersion = currentVersion.replaceFirst("^v", "");

            String[] newParts = newVersion.split("\\.");
            String[] currentParts = currentVersion.split("\\.");

            for (int i = 0; i < Math.max(newParts.length, currentParts.length); i++) {
                int newNum = i < newParts.length ? Integer.parseInt(newParts[i]) : 0;
                int currentNum = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;

                if (newNum > currentNum) return true;
                if (newNum < currentNum) return false;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }
}