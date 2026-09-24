# PocketForge 1.1.0 Release Readiness

Status: **GitHub source release ready; APK build must be produced by CI/local environment.**

## Blocking issue fixed
The previous Claude permission watcher could automatically answer `allow`, including on parse failure. PocketForge now classifies requests and routes approval-required actions to the existing approval UI. Malformed requests are denied by default.

## Permission UX
- SAFE requests: automatic.
- WRITE/EDIT/NOTEBOOK edits: first approval in a session, then remembered for that tool type during the session.
- HIGH-risk shell/system actions: explicit approval remains required.
- Pending requests are de-duplicated while waiting for the user's response.

## Release identity
- App name: PocketForge
- Package: `com.pocketforge.mobile`
- Version: 1.1.0
- Version code: 5
- ABI: arm64-v8a

## Build note
The current execution environment could not download Gradle 8.14, so an APK/AAB was not fabricated or claimed as tested. GitHub Actions is configured to run tests, lint, assemble the online release APK, and publish the APK plus SHA-256 checksum on a `v*` tag.
