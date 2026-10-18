# ArrCtrl

## Working on this repo

- Don't commit. Leave changes uncommitted for review. Push to `main` only when asked, never to another branch and never as a pull request.
- Every push needs a detailed entry in `CHANGELOG.md` covering everything in it.
- Every user-visible string goes in `app/src/main/res/values/strings.xml`.
- Read a service's API (OpenAPI spec, docs or source) before writing or changing its client. Don't guess endpoint names or field shapes.
- No Firebase, Google Play Services or other proprietary dependencies: the app must stay buildable for F-Droid.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "ArrCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.
