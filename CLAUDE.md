# ArrCtrl

## Working on this repo

- Don't commit. Leave changes uncommitted for review. Push to `main` only when asked, never to another branch and never as a pull request.
- Every push needs a detailed entry in `CHANGELOG.md` covering everything in it.
- Every user-visible string goes in `app/src/main/res/values/strings.xml`.
- Read a service's API (OpenAPI spec, docs or source) before writing or changing its client. Don't guess endpoint names or field shapes.
- No Firebase, Google Play Services or other proprietary dependencies: the app must stay buildable for F-Droid.

## Encryption at rest

- What the notification checks remember (`NotifyChecker.Seen` per server) is kept in `filesDir/notify_seen.json` by `data/notify/SeenStore`, sealed with AES-256-GCM (`data/crypto/SealedFile`, marker `ARRCTRL-REST1`) under the data key (`DataKeyStore`: 32 random bytes in `filesDir/data.key`, wrapped by the Keystore key `arrctrl_data_at_rest` through `KeystoreWrapper`/`KeystoreAes`; no user authentication, so background checks run with the screen locked). It's independent of the app lock, `KeystoreCrypto` (API keys and passwords) and `LockVault`: don't mix them.
- Not encrypted, on purpose: the DataStore settings including the server list (names, addresses, user names), `recent_paths`, the Coil image cache, the `notify_state` timestamps, the ntfy registration and WorkManager's database.
- `SeenStore` reads both sealed and ordinary files. Without a data key it writes an ordinary file. It's a re-creatable memory: a file that can't be opened is deleted and every server starts fresh. The old `seen_<id>` preference entries are only removed after the file was written, read back and compared.
- `SeenStore.load`/`save`/`retain` may ask the Keystore for the key: background threads only. `clear()` never does (it's called from the UI). Never replace an existing `data.key`, and never create a Keystore key on the decrypt path (`KeystoreAes.existing`).
- `SealedFileTest` covers the format and the key rules (`./gradlew :app:testDebugUnitTest`; CI doesn't run unit tests).

## Dependency verification

Gradle checks every library and plugin download against its SHA-256 in `gradle/verification-metadata.xml` (checksums only, no PGP). When a dependency or plugin changes, run the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/ArrCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), decode the file from its log, check the diff (only Maven Central, Google's Maven and the Gradle plugin portal) and commit it in the same push. Never turn verification off or make it lenient.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "ArrCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.
