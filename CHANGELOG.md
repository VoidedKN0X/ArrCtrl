# Changelog

What changed in each ArrCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/ArrCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v0.2.0 (2026-10-19)

You can now add your servers. Every service ArrCtrl supports can be set up and tested; the screens that use them arrive in the next updates, starting with Activity.

### New

- **Add servers** from the button at the top right of any tab → **Add a server**, then pick Radarr, Sonarr, qBittorrent, Prowlarr, Bazarr, autobrr or Cleanuparr. Each takes a name, an address and an API key. The form shows where to find the key in that service's web UI
- **qBittorrent** signs in with your Web UI username and password, or with an API key on recent qBittorrent versions (Web API 2.14.1 or newer)
- **More than one of each service**, e.g. a second Radarr for 4K. Give each its own name
- **Test before you save.** The test tells you exactly what's wrong: server not found, connection refused, no answer in time, API key refused, wrong username or password, qBittorrent blocking the phone after too many failed sign-ins, an untrusted or changed certificate, or the wrong service at that address (it names Radarr when you pointed Sonarr at Radarr's port). When it works, it shows the service's version. You can still save a server that doesn't pass, to fix it later
- **Self-signed certificates** are trusted per server: tap **Review certificate**, compare the SHA-256 fingerprint and tap **Trust**. ArrCtrl then accepts exactly that certificate from that server and nothing else, and warns you if it ever changes. A trusted certificate can be forgotten from the server's page. There is no setting to trust every certificate
- **Import all your servers at once** by pasting a JSON block or scanning it as a QR code, instead of typing every API key on your phone. You see what will be added before anything is saved. Servers that are already saved with the same address are updated, not duplicated. The format is in the README
- **Live status in the Servers list**: every server is checked when you open it, with a tick or a warning and the reason
- **Edit or remove** a server by tapping it. Removing only deletes it from the phone
- **Tabs offer to add the server they need**: Movies offers Radarr, Series offers Sonarr, and Calendar and Activity take you to the Servers list. Once a server is added, the tab says so and what comes next. The tools in More open the add form for their service
- Addresses are tidied up for you: a trailing slash is removed, and without `http://` or `https://`, IP addresses and local names (like `nas.lan`) get `http://` and anything else `https://`. Paths for servers behind a reverse proxy (`https://example.com/radarr`) are kept
- A note under the address when a server uses plain HTTP, since the API key is then sent unencrypted

### Security

- API keys and passwords are stored encrypted with a key held in the phone's secure hardware (Android Keystore), and are never included in backups or transfers to a new phone
- An API key is only ever sent to its own server: it never follows a redirect to another site, and never drops from https to plain http
- API keys and passwords typed into the form aren't kept in Android's saved app state
- The camera is only used for scanning a server QR code, and only asked for when you tap **Scan QR**

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
