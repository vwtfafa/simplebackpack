# Changelog

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