package org.vwtfafa.backpack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Checks for updates on GitHub and notifies operators.
 */
public class UpdateChecker {
    private static final String RELEASES_API_URL =
            "https://api.github.com/repos/vwtfafa/SimpleBackpack/releases/latest";
    private static final String RELEASE_PAGE_URL =
            "https://github.com/vwtfafa/SimpleBackpack/releases";

    private final JavaPlugin plugin;
    private final String currentVersion;
    private volatile String latestVersion = null;
    private volatile boolean updateAvailable = false;

    public UpdateChecker(JavaPlugin plugin) {
        this.plugin = plugin;
        this.currentVersion = plugin.getPluginMeta().getVersion();
    }

    /**
     * Loads the latest version from GitHub asynchronously and notifies operators
     */
    public void checkForUpdates() {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                HttpRequest request = HttpRequest.newBuilder(URI.create(RELEASES_API_URL))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "SimpleBackpack/" + currentVersion)
                        .header("Accept", "application/vnd.github+json")
                        .GET()
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("GitHub returned HTTP " + response.statusCode());
                }
                parseRelease(response.body());

                if (updateAvailable) {
                    logUpdateAvailable();
                    notifyAdmins();
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                plugin.getLogger().warning("Update-Check fehlgeschlagen: " + e.getMessage());
            }
        });
    }

    private void parseRelease(String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (!json.has("tag_name")) {
            return;
        }
        latestVersion = json.get("tag_name").getAsString();
        updateAvailable = isNewerVersion(latestVersion, currentVersion);
    }

    private void logUpdateAvailable() {
        plugin.getLogger().info("========================================");
        plugin.getLogger().info("SimpleBackpack update available!");
        plugin.getLogger().info("Current version: " + currentVersion);
        plugin.getLogger().info("New version: " + latestVersion);
        plugin.getLogger().info("Release page: " + RELEASE_PAGE_URL);
        plugin.getLogger().info("========================================");
    }

    /**
     * Sends a chat notification about the available update.
     * Recipients are players with the admin permission and, when
     * {@code update-checker.notify-ops} is enabled, operators.
     */
    private void notifyAdmins() {
        boolean notifyOps = plugin.getConfig().getBoolean("update-checker.notify-ops", true);
        boolean notifyChat = plugin.getConfig().getBoolean("update-checker.notify-chat", true);
        if (!notifyChat) {
            return;
        }
        Bukkit.getGlobalRegionScheduler().run(plugin, task -> {
            Component message = Component.text(
                            "[SimpleBackpack] Update available: " + latestVersion + " - Open release page")
                    .color(NamedTextColor.GOLD)
                    .clickEvent(ClickEvent.openUrl(RELEASE_PAGE_URL))
                    .hoverEvent(HoverEvent.showText(Component.text("Open the latest release page")));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if ((notifyOps && player.isOp()) || player.hasPermission("simplebackpack.admin")) {
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
