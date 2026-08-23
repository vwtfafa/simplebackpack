# Changelog

## 7.0.1 - 2026-08-23

### Fixed
- `/team accept` no longer loses the accepting player: when the inviter had no team yet, the created team previously contained only the inviter while the accepting player stayed teamless.
- Team size limit (`team.max-size`) is now re-checked when an invite is accepted; accepting after the team filled up no longer exceeds the cap and reports `team-full` instead.
- Fixed a race where a quick reconnect after quitting could serve stale backpack data: the cached inventory was evicted while its quit-time save write was still in flight. Cache eviction is now chained onto write completion, and the recent-save deduplication marker is only recorded after the inventory-close save actually finished.
- Console and other non-player senders running player-only commands now receive the dedicated `players-only` message instead of being told they lack permission.

### Changed
- The config GUI "Change Name" reset uses the new `gui-default-backpack-name` lang key instead of hardcoded German/English defaults.
- Known-backpack listing explicitly excludes `*.overflow.yml` sidecar files instead of relying on UUID parsing to reject them.

### Internals
- `BackpackManager` fields `plugin`, `dataFolder` and `teamRegistry` are final; a failure to create the backpacks data folder is logged as a warning.
- Removed dead locale plumbing (unused `Locale` field/parameter in `BackpackManager`, unused `locale()` accessor in `PluginSettings`).
- Removed the unused `mockito-core` test dependency and the unused `share-usage` / `invite-usage` lang keys.
- `UpdateChecker.isNewerVersion` is package-private static; its test now exercises the real method instead of a duplicated copy of the logic. Added cases for `v` prefixes and unparseable versions.
- plugin.yml description no longer mentions the non-existent "admin insert" feature.
- New message key: `players-only` (en/de).

### Verification
- `./gradlew build` passes: compilation, JUnit tests, Checkstyle and SpotBugs.
- Command API usage verified against the Paper 26.2 Javadocs (`ArgumentTypes.player()`, Brigadier lifecycle registration, Folia-style schedulers).

## 7.0 - 2026-08-22

### Changed (Breaking)
- Commands are now registered through Paper's Brigadier lifecycle API (`BasicCommand`); the `commands:` section was removed from plugin.yml. Paper 26.2+ is required — Spigot/Bukkit servers are no longer supported.
- Command visibility flags (`classic-mode`, `show-team-commands`, `show-admin-commands`) apply at registration time; changing them requires a server restart.
- Update checker semantics: `update-checker.notify-chat` is the master switch for chat notifications, `update-checker.notify-ops` only controls whether operators without the admin permission receive them.
- `live-config-reload=false` now disables `/backpackreload`.
- Player-facing messages and inventory titles are sent as Adventure components; legacy `§` color codes in existing configs keep working via `LegacyComponentSerializer`.

### Added
- Tab completion: player names for `/invite` and `/backpackshare`, plus `accept` and `gui` subcommand suggestions.
- Admin GUI pagination (45 entries per page) with previous/next buttons and a page info item.
- Team invites expire after 5 minutes with a dedicated message when accepting too late.
- New configurable messages: `backpack-full`, `resize-no-space`, `team-invite-expired`, `admin-gui-disabled`.

### Fixed
- Fixed silent item loss when shrinking the backpack: items from removed slots move to the player inventory; the resize aborts safely if there is not enough space.
- Fixed drag events bypassing the click cancellation of preview views, the config GUI and the admin list.
- Fixed a death wiping the whole shared team or temporarily shared backpack when `backpack.keep-on-death` was disabled; only personally owned backpacks are cleared now.
- Fixed team owner succession being random (HashSet order); the member with the lowest UUID now takes over deterministically.
- Removed the custom shift-click handler that duplicated vanilla behavior and risked item duplication; backpack views rely on vanilla inventory behavior.
- The documented `admin.enable-gui` option is now honored; `/backpackadmin gui` reports when it is disabled instead of doing nothing.
- `clearBackpack` uses the inventory's real size instead of the configured size, which could mismatch after size changes.
- Null-safe inviter names in team info output.

### Performance & Internals
- O(1) team ownership lookups through a new `TeamRegistry` reverse index; duplicated owner-search code unified between classes.
- Quit-time autosave no longer performs main-thread disk I/O (snapshot is taken synchronously, written asynchronously); shutdown saves stay synchronous by design.
- Audit log writes asynchronously with 5 MB rotation to `backpack-audit.log.old`.
- Replaced the unbounded per-owner save-lock map with a single IO lock, which also prevents interleaved temp-file writes.
- Admin GUI stores owner UUIDs in the item's PersistentDataContainer instead of parsing lore text; only clicks inside the admin list are processed.
- Inventory holders now return their actual inventory instead of null (`InventoryHolder` contract).
- Localized messaging centralized in a new `Messages` class; hardcoded DE/EN ternaries replaced with config message keys.
- Dead code removed (`leaveTeam`, `giveItemToAll`, `setBackpacksEnabled`, `isInTeam`) and manager config fields that were never read dropped; `BackpackManager` constructor shrunk from 12 to 6 parameters.
- Update checker uses `java.net.http.HttpClient`, `getPluginMeta()` and Gson; the org.json dependency was removed because it was never included in the shaded jar (runtime NoClassDefFoundError risk).
- Obsolete mockito-inline test dependency removed; plugin.yml version is now derived from the Gradle project version.

### Verification
- `./gradlew.bat clean check` passes: compilation, JUnit tests, Checkstyle and SpotBugs.

## 6.0 - 2026-08-20

### Added
- Added typed inventory holders for personal backpacks, admin views, preview mode, configuration GUIs, and the admin overview.
- Added persistent team storage in `teams.yml`, including loading on startup and saving on shutdown and team changes.
- Added team owner transfer when the current owner leaves.
- Added support for adding players to an existing team and enforcement of the configured maximum team size.
- Added automatic backpack saving when a player quits.
- Added configurable death handling: backpack contents are cleared when `backpack.keep-on-death` is disabled.
- Added command permission checks for backpack, configuration, reload, team, sharing, and admin commands.

### Changed
- Backpack and admin inventories are now identified by their holder type instead of display-title text.
- Preview inventories are now tied to the specific inventory view and cannot be modified or saved accidentally.
- Backpack saves now create a Main-thread snapshot before asynchronous file work begins.
- Backpack files are written through a temporary file and atomically replaced where supported.
- Per-backpack write locks prevent concurrent saves from overwriting each other.
- Sharing now validates its duration and rejects invalid values or durations longer than seven days.
- Creative-mode access, configuration GUI access, and sharing now honor their respective configuration options.
- The GitHub update checker now uses structured JSON parsing, HTTP status validation, a User-Agent, and proper reader handling.
- README compatibility information now reflects the Paper 26.2 target platform instead of claiming Bukkit/Spigot support.

### Fixed
- Fixed team members being assigned their personal backpack instead of the shared team backpack after loading or owner transfer.
- Fixed preview-mode changes being persisted when the admin closed the inventory.
- Fixed asynchronous save tasks reading mutable Bukkit inventories directly.
- Fixed the previously empty `BackpackManager` owner-resolution test.

### Verification
- `./gradlew.bat clean check` passes successfully.
- Checkstyle, SpotBugs, compilation, and the JUnit test suite pass successfully.

## 5.0 - 2026-08-17
- Bumped plugin version to 5.0
- Added unit tests for SharedSession, UpdateChecker, and BackpackManager
- Added JUnit 5 (5.10.0) and Mockito (5.0.0) test dependencies with JUnit Platform launcher
- Made BackpackManager's sharedSessions field package-private for test access
- Added release workflow (from lock-end stuff) with config (.github/release-config.yml) and template (.github/RELEASE_TEMPLATE.md)
- Added UpdateChecker for GitHub release notifications (checks SimpleBackpack/releases/latest)
- Added update-checker section to config.yml (notify-ops, notify-chat)
- Added CLAUDE.md to .gitignore to exclude from repository
- Fixed build.gradle SpotBugs configuration (using Effort.MAX and Confidence.LOW enums)
- Added testImplementation for paper-api to enable Bukkit mocking in tests
- Fixed deprecation warnings and import issues in test and main source
- Removed der plan file (was empty and not needed)
- CI: GitHub Actions workflow to build on push/PR (updated to use actions/checkout@v7, setup-java@v5)
- Updated run-paper plugin to version 3.1.0
- Updated shadowJar, checkstyle, and spotbugs plugins to latest compatible versions
- Updated dependencies: org.json:json to 20260814, bstats-bukkit to 3.2.1
- Implemented proper error handling and logging in UpdateChecker
- Ensured build passes all checks (compile, test, jar, shadowJar, checkstyle, spotbugs)

## 4.0 - 2025-12-07
- Bumped plugin version to 4.0
- Compiled against Paper API 1.21.10
- Added Admin GUI (`/backpackadmin gui`) and admin preview mode
- Added `/backpackshare` for temporary sharing of backpacks
- Implemented audit logging (`backpack-audit.log`)
- Added automatic admin snapshots (`admin.auto-snapshot`) before admin edits
- Added feature flags in `config.yml` for admin GUI and sharing
- CI: GitHub Actions workflow to build on push/PR