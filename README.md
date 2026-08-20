# SimpleBackpack

A persistent, configurable backpack plugin for Minecraft Paper.

## Features

- **Personal Persistent Backpacks** - Per-player backpacks with configurable size, name and color
- **Team Backpacks** - Create/invite/share backpacks with simple team commands
- **Classic Mode** - Minimal mode with only `/backpack` and `/bp`
- **Admin Tools** - Admin GUI, admin edits, audit logging
- **Live Config GUI** - Change backpack configuration in-game with instant apply
- **Multi-language** - English and German included; messages fully editable
- **Update Checker** - Notifies operators when updates are available on GitHub
- **bStats Metrics** - Anonymous statistics tracking (plugin ID: 32528)
- **Unit Tests** - JUnit 5 tests for core functionality

## Installation

1. Build the plugin using Gradle: `./gradlew build`
2. Copy the generated JAR from `build/libs/` into the server `plugins/` folder
3. Start the server and adjust `config.yml` as needed

## Commands

| Command | Description | Permission |
| ------- | ----------- | ---------- |
| `/backpack`, `/bp` | Open your personal backpack | `simplebackpack.use` |
| `/backpackconfig` | Open the backpack configuration GUI | `simplebackpack.config` |
| `/backpackreload` | Reload the plugin configuration | `simplebackpack.reload` |
| `/invite <player>` | Invite a player to your team | `simplebackpack.team.invite` |
| `/team` | Show your team members | `simplebackpack.team` |
| `/team accept` | Accept a team invitation | `simplebackpack.team` |
| `/leave` | Leave your current team | `simplebackpack.team.leave` |
| `/backpackadmin gui` | Open admin overview GUI | `simplebackpack.admin` |
| `/backpackshare <player> [minutes]` | Temporarily share your backpack | `simplebackpack.use` |

## Permissions

| Permission | Default | Description |
| ---------- | ------- | ----------- |
| `simplebackpack.use` | true | Allows using `/backpack` and `/bp` |
| `simplebackpack.config` | op | Allows using `/backpackconfig` |
| `simplebackpack.reload` | op | Allows using `/backpackreload` |
| `simplebackpack.team` | true | Allows using `/team` |
| `simplebackpack.team.invite` | true | Allows inviting players to teams |
| `simplebackpack.team.leave` | true | Allows leaving teams |
| `simplebackpack.admin` | op | Allows using admin commands |

## Configuration

See `plugins/SimpleBackpack/config.yml` for all configuration options:

### General Features
- `language` - Language setting (`en` or `de`)
- `classic-mode` - Disable all features except basic backpack
- `backpacks-enabled` - Enable/disable all backpack features globally
- `live-config-reload` - Allow live reload with `/backpackreload`
- `messages-enabled` - Enable/disable player messages

### Backpack Settings
- `backpack.name` - Inventory name (supports color codes)
- `backpack.size` - Backpack size (9, 18, 27, 36, 45, or 54)
- `backpack.allow-in-creative` - Allow backpack use in creative mode
- `backpack.auto-save-on-quit` - Auto-save when player disconnects
- `backpack.keep-on-death` - Keep contents on death
- `backpack.gui-configurable` - Allow in-game configuration

### Team Settings
- `team.enabled` - Enable team functionality
- `team.max-size` - Maximum players per team

### Admin Settings
- `admin.enabled` - Enable admin features
- `admin.enable-gui` - Enable admin GUI (`/backpackadmin gui`)
- `admin.auto-snapshot` - Auto-create snapshots before admin edits

### Feature Flags
- `show-team-commands` - Show/hide team commands
- `show-admin-commands` - Show/hide admin commands
- `enable-sharing` - Enable `/backpackshare` command

### Update Checker
- `update-checker.notify-ops` - Notify operators about updates
- `update-checker.notify-chat` - Show update notification in chat

### Messaging
Messages are organized by language (`en`/`de`) under `messages.<lang>.<key>`.

## Development

### Building

```bash
./gradlew clean build
```

### Running Tests

```bash
./gradlew test
```

### Release

The plugin uses a GitHub Actions workflow for automated releases. To create a release:

1. Update version in `build.gradle`
2. Update `CHANGELOG.md`
3. Commit and push to `master` (or `main`)
4. The workflow will automatically create a GitHub Release

### Releases Configuration

Edit `.github/release-config.yml` to configure release types per branch:

```yaml
branches:
  master:
    type: stable
    paper: "26.2"
    java: "25"
  beta:
    type: beta
    paper: "26.2"
    java: "25"
```

## Compatibility

| Platform | Version | Support |
| -------- | ------- | ------- |
| **Paper** | 26.2+ | ✅ Supported |
| **Purpur** | 26.2+ | ⚠️ Expected to work, not separately tested |

## Requirements

- Java 25
- Paper 26.2+
- GitHub access (for update checker)

## Author

vwtfafa

## License

This plugin is provided as-is for Minecraft server use.

## Links

- [GitHub Repository](https://github.com/vwtfafa/SimpleBackpack)
- [bStats](https://bstats.org/plugin/SimpleBackpack/32528)
- [Modrinth](https://modrinth.com/plugin/simplebackpack)