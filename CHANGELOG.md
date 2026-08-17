# Changelog

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