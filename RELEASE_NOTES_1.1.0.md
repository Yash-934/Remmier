# PocketForge 1.1.0 — GitHub Release

## What changed

- Renamed the app to **PocketForge**.
- Changed Android application ID and namespace to `com.pocketforge.mobile`.
- Bumped release version to `1.1.0` / version code `5`.
- Hardened Claude Code permission handling: the runtime no longer auto-approves unknown/malformed permission requests.
- Safe tool requests can continue automatically; file-edit approval can be remembered for the current session; HIGH-risk actions remain individually gated.
- Release/debug logs no longer dump raw Claude Code streaming output in release builds.
- Added a GitHub Actions release workflow for the ARM64 online APK plus SHA-256 checksums.
- Updated F-Droid metadata and release-facing documentation for the new identity.

## Verification

Static verification completed for the source snapshot:

- No stale `com.jarves.mh` package references.
- No stale `Mobile Harness` / `MobileHarness` / `PocketDevApp` references in release source/docs.
- Android resource XML parses successfully.
- No obvious API-key/private-key patterns found in the source tree.
- Package directories and Kotlin package declarations are aligned.

The container could not execute the Gradle build because Gradle 8.14 was not cached and `services.gradle.org` was unreachable. The included `.github/workflows/release.yml` performs unit tests, lint, and the online release APK build on GitHub Actions.

## Known scope

This release is the **hardened Claude Code/API GitHub edition**. The previously discussed local GGUF/Hugging Face engine and user-assisted web-chat provider are not yet implemented in this snapshot.
