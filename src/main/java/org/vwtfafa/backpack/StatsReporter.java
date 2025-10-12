package org.vwtfafa.backpack;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

public class StatsReporter {

    private final JavaPlugin plugin;
    private final String apiUrl = "https://plugin-dashboard.onrender.com/api/plugin-stats"; // 👉 deine Dashboard-URL

    public StatsReporter(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        // Check if stats are enabled in config before scheduling
        if (!plugin.getConfig().getBoolean("stats-enabled", true)) {
            plugin.getLogger().info("Stats reporting is disabled in config.");
            return;
        }

        // direkt beim Start senden
        sendStats();

        // alle 30 Sekunden (20 Ticks = 1 Sekunde, also 20*30 = 600)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::sendStats, 0L, 600L);
    }

    private void sendStats() {
        if (!plugin.getConfig().getBoolean("stats-enabled", true)) return;
        try {
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String json = String.format(
                    "{\"server_uuid\":\"%s\",\"plugin\":\"%s\",\"plugin_version\":\"%s\",\"server_version\":\"%s\",\"players_online\":%d}",
                    getServerUUID(),
                    plugin.getDescription().getName(),
                    plugin.getDescription().getVersion(),
                    Bukkit.getBukkitVersion(),
                    Bukkit.getOnlinePlayers().size()
            );

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                plugin.getLogger().warning("Failed to send stats: " + responseCode);
            }
            // ✅ Erfolgsmeldung entfernt

        } catch (Exception e) {
            plugin.getLogger().warning("❌ Konnte Stats nicht senden: " + e.getMessage());
        }
    }

    public void sendUpdateToDashboard() {
        try {
            URL url = new URL("https://plugin-dashboard.onrender.com/api/update");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            String data = String.format(
                    "{\"name\":\"%s\",\"ip\":\"%s\",\"plugin_version\":\"%s\",\"server_version\":\"%s\",\"players\":%d}",
                    Bukkit.getServer().getName(),
                    Bukkit.getServer().getIp(),
                    plugin.getDescription().getVersion(),
                    Bukkit.getVersion(),
                    Bukkit.getOnlinePlayers().size()
            );

            try (OutputStream os = conn.getOutputStream()) {
                os.write(data.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                plugin.getLogger().warning("Failed to send dashboard data: " + responseCode);
            }
            // Erfolgsmeldung entfernt

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send dashboard data: " + e.getMessage());
        }
    }

    private String getServerUUID() {
        File f = new File(plugin.getDataFolder(), "server-id.txt");
        try {
            if (!f.exists()) {
                if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
                String id = UUID.randomUUID().toString();
                Files.write(f.toPath(), id.getBytes(StandardCharsets.UTF_8));
                return id;
            } else {
                return Files.readString(f.toPath()).trim();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return "unknown";
        }
    }
}
