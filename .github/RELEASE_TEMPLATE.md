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

- **bStats** — Anonymous metrics (can be disabled server-side)
- **GitHub** — Built-in update checker with chat notifications for operators/admins
- **Adventure API** — All player-facing messages and titles are sent as components; legacy `§` color codes in configs keep working

### 🔧 Configuration

`plugins/SimpleBackpack/config.yml`:

- Backpack name, size, color
- Team settings (enabled, max size)
- Admin settings (GUI, auto-snapshot)
- Command visibility (classic mode, show team/admin commands — requires restart)
- Feature flags (enable sharing)
- Messaging (enabled, language)
- Live config reload (`live-config-reload=false` disables `/backpackreload`)
- Keep contents on death

### 🧱 Compatibility

| Platform | Version | Support |
| -------- | ------- | ------- |
| **Paper** | @PAPER@+ | ✅ Recommended |
| **Purpur** | @PAPER@+ | ✅ Works (Paper fork) |

> Commands use Paper's Brigadier lifecycle API — Spigot/Bukkit servers are not supported.

### 📦 Installation

1. Download `SimpleBackpack-@VERSION@.jar` below
2. Place it in your `plugins/` folder
3. Restart your server (Paper @PAPER@+, Java @JAVA@)

### 📚 Documentation

- [README](https://github.com/vwtfafa/SimpleBackpack/blob/@BRANCH@/README.md) – Full docs & examples
- [Modrinth](https://modrinth.com/plugin/simplebackpack) – Download on Modrinth

---

*Built from commit @GITHUB_SHA@ on @BUILD_DATE@*