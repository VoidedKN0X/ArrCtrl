# Changelog

What changed in each ArrCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/ArrCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.1.0 (2026-10-18)

The first build of ArrCtrl: the app's frame, ready for your servers. Nothing connects to a server yet; adding Radarr, Sonarr, qBittorrent and the rest comes in the next update.

### New

- **Five tabs** in a floating bar at the bottom: Movies, Series, Calendar, Activity and More. On tablets and unfolded foldables they move to a rail on the side
- **Movies, Series, Calendar and Activity** each explain what will show there once the matching server is added
- **More** lists the tools that will live there (Prowlarr, Bazarr, autobrr, Cleanuparr and system health), each greyed out until its server is set up
- **Account and settings** opens from the button at the top right of every tab (there is no separate tab for it). It lists the seven supported servers, links to settings, and has an About section with the version, the source code, the bug tracker and a link to support development on GitHub Sponsors
- **Appearance settings**: follow the system theme, or pick dark, light or pure black (for OLED screens)
- **Amber accent** by default, with a **Use wallpaper colours** switch that swaps it for Material You colours from your wallpaper
- **Same look as the other Ctrl apps**: Manrope type, the Graphite dark theme and a frosted-glass tab bar
- Needs Android 13 or newer
