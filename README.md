# ArrCtrl

**One app for your whole self-hosted download stack.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/ArrCtrl?label=download)](https://github.com/VoidedKN0X/ArrCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

ArrCtrl is an Android app for [Radarr](https://radarr.video/), [Sonarr](https://sonarr.tv/), [Prowlarr](https://prowlarr.com/), [Bazarr](https://www.bazarr.media/), [qBittorrent](https://www.qbittorrent.org/), [autobrr](https://autobrr.com/) and [Cleanuparr](https://github.com/Cleanuparr/Cleanuparr), in the spirit of nzb360 and LunaSea.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ArrCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

> **Early development.** Version 0.1.0 is the app's frame: tabs, settings and theming. Server setup and the features below arrive over the next updates; see the [changelog](CHANGELOG.md).

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
