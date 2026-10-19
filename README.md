# ArrCtrl

**One app for your whole self-hosted download stack.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/ArrCtrl?label=download)](https://github.com/VoidedKN0X/ArrCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

ArrCtrl is an Android app for [Radarr](https://radarr.video/), [Sonarr](https://sonarr.tv/), [Prowlarr](https://prowlarr.com/), [Bazarr](https://www.bazarr.media/), [qBittorrent](https://www.qbittorrent.org/), [autobrr](https://autobrr.com/) and [Cleanuparr](https://github.com/Cleanuparr/Cleanuparr), in the spirit of nzb360 and LunaSea.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ArrCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

> **Early development.** Servers, Movies, Series and Activity (queue, torrents, history) work; Calendar and the More tools arrive over the next updates. See the [changelog](CHANGELOG.md).

## Features

- **Movies and Series**: your Radarr and Sonarr libraries with automatic and interactive search, monitoring, quality profiles, renaming and bulk edits
- **Calendar** with upcoming movies and episodes from both
- **Activity**: the Radarr and Sonarr queues with manual import, and your qBittorrent torrents with full controls
- **Add torrents** from magnet links and `.torrent` files, straight from the share sheet
- **Prowlarr** indexers and cross-indexer search, **Bazarr** subtitles, **autobrr** filters and releases, **Cleanuparr** stats and jobs
- **System health** for every server in one place, with low disk space warnings
- **Notifications** without Google services: polling, or instant push through your own ntfy server
- **Widgets** for upcoming releases and active downloads, and a quick settings tile for qBittorrent's alternative speed limits
- **Every service is optional**, and you can add more than one of each (a second Radarr for 4K, say)
- **Self-signed certificates** trusted per server, by fingerprint

## Requirements

- Android 13 or newer
- Any of: Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr, Cleanuparr

## Install

1. Download the latest `.apk` file from the [Releases page](https://github.com/VoidedKN0X/ArrCtrl/releases/latest).
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and servers are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds ArrCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ArrCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/ArrCtrl` in Obtainium by hand.

## Getting started

1. Tap the button at the top right of any tab, then **Add a server**.
2. Pick the service and enter its address, e.g. `http://192.168.1.10:7878` or `https://radarr.example.com`. Add the path too if it runs under one behind a reverse proxy (`https://example.com/radarr`).
3. Enter the API key (in the web UI under Settings → General → Security for Radarr, Sonarr, Prowlarr and Bazarr). For qBittorrent use your Web UI username and password, or an API key on recent versions.
4. Tap **Test**, then **Save**. If the server uses a self-signed certificate, tap **Review certificate**, check the fingerprint and trust it. ArrCtrl then accepts that exact certificate from that server only.

Every service is optional, and you can add more than one of each.

### Import all servers at once

Instead of typing seven API keys on your phone, put them in one JSON block and either paste it (**Import servers** → **Paste**) or turn it into a QR code and scan it (**Scan QR**).

```json
{
  "arrctrl": 1,
  "servers": [
    { "type": "radarr",      "url": "http://192.168.1.10:7878", "apiKey": "…" },
    { "type": "radarr",      "name": "Radarr 4K", "url": "http://192.168.1.10:7879", "apiKey": "…" },
    { "type": "sonarr",      "url": "https://sonarr.example.com", "apiKey": "…" },
    { "type": "prowlarr",    "url": "http://192.168.1.10:9696", "apiKey": "…" },
    { "type": "bazarr",      "url": "http://192.168.1.10:6767", "apiKey": "…" },
    { "type": "qbittorrent", "url": "http://192.168.1.10:8080", "username": "admin", "password": "…" },
    { "type": "autobrr",     "url": "http://192.168.1.10:7474", "apiKey": "…" },
    { "type": "cleanuparr",  "url": "https://cleanuparr.lan", "apiKey": "…", "certSha256": "AB:CD:…" }
  ]
}
```

| Field | Needed | What it is |
| --- | --- | --- |
| `type` | yes | `radarr`, `sonarr`, `prowlarr`, `bazarr`, `qbittorrent`, `autobrr` or `cleanuparr` |
| `url` | yes | The server address. Without `http://` or `https://`, IP addresses and local names get `http://`, anything else `https://` |
| `apiKey` | yes, except qBittorrent | The service's API key. For qBittorrent, an API key replaces username and password |
| `username`, `password` | qBittorrent only | Your qBittorrent Web UI login, when not using an API key |
| `name` | no | Shown in the app. Defaults to the service name, numbered when there's more than one |
| `certSha256` | no | SHA-256 fingerprint of a self-signed certificate to trust, with or without colons |

A plain list of servers (without the `"servers"` wrapper) works too. A server already saved with the same service and address is updated rather than added twice.

To make a QR code on Linux, save the JSON as `arrctrl.json` and run `qrencode -t ansiutf8 < arrctrl.json` (or `-o arrctrl.png` for an image). **The JSON and the QR code contain your API keys and passwords.** Delete them once you've scanned them, and don't share screenshots of them.

### Adding torrents

Tap **Add torrent** on the Torrents tab and paste a magnet link (or several), or pick a `.torrent` file. ArrCtrl also shows up when you open a `magnet:` link or a `.torrent` file, and in the share sheet. To make it the default for magnet links, open one and pick **ArrCtrl** → **Always**, or set it in Android's Settings → Apps → Default apps → Opening links.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/ArrCtrl.git
   cd ArrCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

## Support the project

ArrCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). Starring the repo and [reporting bugs](https://github.com/VoidedKN0X/ArrCtrl/issues) helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| **ArrCtrl** (this app) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |

## License

ArrCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The Manrope font is licensed under the SIL Open Font License; see `FONT-LICENSE-Manrope.txt`.
