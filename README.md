# SimpleBackpack

SimpleBackpack is a persistent, configurable backpack plugin for Minecraft (Paper/Spigot/Bukkit/Purpur).

Features
- Personal persistent backpacks with configurable size, name and color
- Team backpacks with invite/share functionality
- Classic minimal mode
- Admin tools: admin GUI, admin edits, audit logging
- Live config GUI and instant reload
- Multi-language (English/German)

Supported Minecraft versions
- Tested against Paper 1.21.10 (plugin compiled with Paper API 1.21.10)
- Expected compatible with Minecraft 1.20.x and 1.21.x servers

Installation
1. Build the plugin using Gradle: `./gradlew build`
2. Copy the generated JAR from `build/libs/` into the server `plugins/` folder
3. Start the server and adjust `config.yml` as needed

Configuration highlights
- `admin.auto-snapshot` (default `true`): saves a snapshot of a backpack before admin edits
- `admin.enable-gui` (default `true`): enable admin GUI and `/backpackadmin gui`
- `enable-sharing` (default `true`): enable `/backpackshare` command

Commands
See `plugin.yml` for a full list of commands and permissions.

Author: vwtfafa
