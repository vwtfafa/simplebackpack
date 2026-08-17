## 🔒 SimpleBackpack @TITLE@

@WARNING@

**Requirements:** Paper @PAPER@+, Java @JAVA@

### 📋 Changes since last release

@CHANGELOG@

### 📜 Commands

| Command | Description | Permission |
| ------- | ----------- | ---------- |
| `/backpack` | Open your personal backpack | `simplebackpack.use` |
| `/bp` | Alias for `/backpack` | `simplebackpack.use` |
| `/backpackconfig` | Open the backpack configuration GUI | `simplebackpack.config` |
| `/backpackreload` | Reload the SimpleBackpack config | `simplebackpack.reload` |
| `/invite <player>` | Invite a player to your team | `simplebackpack.team.invite` |
| `/team` | Show your team members | `simplebackpack.team` |
| `/leave` | Leave your current team | `simplebackpack.team.leave` |
| `/backpackadmin gui` | Open admin overview GUI | `simplebackpack.admin` |
| `/backpackshare <player> [minutes]` | Temporarily share your backpack | `simplebackpack.use` |

### 🔌 Integrations

- **PlaceholderAPI** — `%simplebackpack_enabled%`, `%simplebackpack_size%` (example)
- **LuckPerms** — Context `simplebackpack:team=true|false`
- **MiniMessage** — Full RGB & gradient message support

### 🔧 Configuration

`plugins/SimpleBackpack/config.yml`:

- Backpack name, size, color
- Team settings (enabled, max size)
- Admin settings (GUI, auto-snapshot)
- Command visibility (show team/admin commands)
- Feature flags (enable sharing)
- Messaging (enabled, language)
- Live config reload
- Keep contents on death

### 🧱 Compatibility

| Platform | Version | Support |
| -------- | ------- | ------- |
| **Paper** | @PAPER@+ | ✅ Recommended |
| **Purpur** | @PAPER@+ | ✅ Works |
| **Spigot** | @PAPER@+ | ✅ Works (via PaperAPI) |
| **Bukkit** | @PAPER@+ | ✅ Works (via PaperAPI) |

### 📦 Installation

1. Download `SimpleBackpack-@VERSION@.jar` below
2. Place it in your `plugins/` folder
3. Restart your server (Paper @PAPER@+, Java @JAVA@)

### 📚 Documentation

- [README](https://github.com/vwtfafa/SimpleBackpack/blob/@BRANCH@/README.md) – Full docs & examples
- [Modrinth](https://modrinth.com/plugin/simplebackpack) – Download on Modrinth

---

*Built from commit @GITHUB_SHA@ on @BUILD_DATE@*