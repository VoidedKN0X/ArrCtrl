# ArrCtrl

**One app for your whole self-hosted download stack.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/ArrCtrl?label=download)](https://github.com/VoidedKN0X/ArrCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

ArrCtrl is an Android app for [Radarr](https://radarr.video/), [Sonarr](https://sonarr.tv/), [Prowlarr](https://prowlarr.com/), [Bazarr](https://www.bazarr.media/), [qBittorrent](https://www.qbittorrent.org/), [autobrr](https://autobrr.com/) and [Cleanuparr](https://github.com/Cleanuparr/Cleanuparr), in the spirit of nzb360 and LunaSea.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ArrCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

> **ArrCtrl 1.0.** Everything planned is in: Movies, Series, Calendar, Activity, the More tools, subtitles, notifications, widgets and the quick settings tile. Found a bug? [Open an issue](https://github.com/VoidedKN0X/ArrCtrl/issues). See the [changelog](CHANGELOG.md) for what's new.

## Features

- **Movies and Series**: your Radarr and Sonarr libraries with automatic and interactive search, monitoring, quality profiles, renaming and bulk edits
- **Calendar** with upcoming movies and episodes from both
- **Activity**: the Radarr and Sonarr queues with manual import, your qBittorrent torrents with full controls, and the Radarr and Sonarr history
- **Add torrents** from magnet links and `.torrent` files, straight from the share sheet
- **Subtitles** through Bazarr on every movie and episode: see what's there, get any other language, or pick one yourself
- **Prowlarr** indexers and cross-indexer search, **Bazarr** subtitles, **autobrr** filters and releases, **Cleanuparr** stats and jobs
- **System health** for every server in one place, with low disk space warnings
- **Notifications**: background checks, plus instant push through [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) and your own ntfy server
- **Widgets** for upcoming releases and active downloads, and a quick settings tile for qBittorrent's alternative speed limits
- **Every service is optional**, and you can add more than one of each (a second Radarr for 4K, say)
- **Import all your servers at once** by pasting one block of settings or scanning it as a QR code
- **Play in JellyCtrl** and **Open in SeerrCtrl** buttons when those apps are installed (for now they open the app)
- **Light, dark or pure black**, in ArrCtrl's amber or your wallpaper colours
- **Self-signed certificates** trusted per server, by fingerprint
- **App lock** with its own PIN and optional fingerprint unlock; your API keys and passwords stay encrypted while the app is locked
- **API keys and passwords are encrypted** on your phone with the Android Keystore; the server and import screens block screenshots, and ArrCtrl's preview is blank in recent apps. **Hide in screenshots and recent apps** blocks screenshots everywhere in the app

## Screenshots

Coming soon.

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- Any of: Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr, Cleanuparr
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

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
3. Enter the API key (in the web UI under Settings → General → Security for Radarr, Sonarr, Prowlarr and Bazarr). For qBittorrent use your Web UI username and password, or an API key on qBittorrent 5.2 or newer.
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

### Notifications

Turn them on in **Account and settings** (the button at the top right) → **Notifications**. ArrCtrl checks your servers itself and tells you about downloads, failed or stuck downloads, finished torrents, health warnings, low disk space and servers that stop answering. You choose which kinds, and which servers. Nothing goes through any outside service.

There are two ways ArrCtrl finds out, and you can use both:

- **Background checks** every 15 minutes to 3 hours. Nothing to set up, but Android can delay them, especially when the phone is idle.
- **Instant, with ntfy.** Push comes through the [ntfy app](https://f-droid.org/packages/io.heckel.ntfy/) only (no other push apps). Your servers wake ArrCtrl the moment something happens, and ArrCtrl then checks and notifies you. The message itself is only a wake-up call: ArrCtrl ignores its content and asks your servers directly, so it doesn't matter what a service puts in it. Background checks keep running alongside as a fallback.

An **Active downloads** notification (what's downloading and how fast) is also available. It's off by default because it updates every minute while something downloads.

#### Setting up instant notifications with ntfy

1. Install the [ntfy app](https://f-droid.org/packages/io.heckel.ntfy/) and, in its settings, set your own ntfy server as the default server. If ntfy isn't installed, the Notifications screen says so and has an **Install ntfy** button.
2. In ArrCtrl, turn on **Instant with ntfy**. ArrCtrl shows a **push address** such as `https://ntfy.example.com/upAbC123xyz?up=1`, split into the **ntfy server** (`https://ntfy.example.com`) and **topic** (`upAbC123xyz`). Tap a row to copy it.
3. Point each service at it, below. You only need the services you want instant notifications for; the rest are still covered by background checks.
4. Tap **Send a test wake-up**. "Last wake-up" updates when it arrives.

| Service | Where | What to enter |
|---|---|---|
| **Radarr**, **Sonarr**, **Prowlarr** | Settings → Connect → **+** → **ntfy** | Server URL: the ntfy server. Topics: the topic. Tick the events you care about (On Grab, On Import, On Health Issue, and so on) and **Test**. |
| **Bazarr** | Settings → Notifications → add | An Apprise URL: `ntfys://ntfy.example.com/upAbC123xyz` (`ntfy://` for plain HTTP). |
| **autobrr** | Settings → Notifications → add → **ntfy** | URL: the whole push address (autobrr posts straight to it), e.g. `https://ntfy.example.com/upAbC123xyz`. Pick the events. |
| **Cleanuparr** | Settings → Notifications → add → **ntfy** | Server URL: the ntfy server. Topics: the topic. |
| **qBittorrent** | Options → Downloads → **Run external program** → *on torrent finished* | `curl -s -d done https://ntfy.example.com/upAbC123xyz` (the container or host needs `curl`). |

If your ntfy server needs a login to publish, add the username and password (or access token) in the service's ntfy settings, or allow anonymous publishing to `up*` topics in ntfy's access control, which is what ntfy's docs recommend for these app topics.

#### Notifications arrive late?

Android limits apps in the background to save battery. The Notifications screen shows whether ArrCtrl is restricted and lets you lift it. Some phones go further on their own: OPPO and OnePlus (ColorOS), Xiaomi, Samsung and Huawei can stop apps in the background regardless. On those, open ArrCtrl's **App settings** from the same screen and allow background activity and auto-launch. Do the same for the ntfy app, since it has to stay connected for instant notifications; the Notifications screen warns when ntfy is battery-restricted.

### Widgets and the quick settings tile

- **Upcoming** shows movies and episodes coming in the next two weeks, by day, with episode air times. A red dot means it's out and missing.
- **Downloads** shows what's downloading, with progress, time left and the total qBittorrent speed.

Long-press your home screen → Widgets → ArrCtrl. Both refresh every 30 minutes, when you open ArrCtrl and when you tap the refresh button; Downloads also refreshes every couple of minutes while something is downloading. Tap a row to open it in ArrCtrl. They follow ArrCtrl's colours, or your wallpaper's when "Use wallpaper colours" is on.

The **Alt. speed** tile (pull down Quick Settings → edit → drag it in) turns qBittorrent's alternative speed limits on or off for all your qBittorrent servers, like the button on the Torrents tab. Long-press it to open the Torrents tab.

## App lock

Turn it on under **Account › Settings › Privacy and security › App lock** (it's off until you do).

- **Its own PIN**, at least 6 digits and separate from your phone's; very obvious ones like 111111 or 123456 are refused. The PIN is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a secret held in the phone's secure hardware, so copying ArrCtrl's files off the phone isn't enough to guess it.
- **Unlock with fingerprint** (optional): only strong biometrics such as your fingerprint, checked by the phone's secure hardware. Adding a new fingerprint turns it off until you turn it on again with your PIN. The PIN is still needed after the phone restarts and at least every 72 hours.
- **Wrong PINs make you wait**: 30 seconds after 5 tries, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again**: as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Trips ArrCtrl starts itself (a file picker, your browser, the share sheet, Android settings) don't count as leaving, unless you stay away longer than 5 minutes.
- **Your API keys and passwords are sealed while locked** (unless notifications are on, see below). They're encrypted with a key that only exists in memory while ArrCtrl is unlocked and is wiped the moment it locks, so a locked ArrCtrl, or a copy of its files, can't sign in to your servers. While ArrCtrl is locked, nothing of it is shown, not even in recent apps.
- **Notifications and the app lock:** background checks and instant notifications need to sign in while ArrCtrl is locked. With notifications on, your API keys and passwords stay encrypted with the Android Keystore only, as without the app lock, and notifications, widgets and the tile keep working. Turn notifications off to keep them sealed with your PIN too; widgets then keep showing what they last loaded and the tile opens ArrCtrl, until you unlock.
- **Forgot your PIN?** The lock screen can remove your servers with their API keys and passwords so you can add them again. Nothing changes on your servers.
- **Change app PIN** asks for your current PIN first, and so does turning the lock off.

## Privacy

**Telemetry-free.** ArrCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It only talks to the servers you add.

- Posters and backdrops come from your own Radarr and Sonarr servers, never straight from sites like TMDB.
- Instant notifications come through the ntfy app and your own ntfy server, and **Send a test wake-up** posts to your push address.
- Help links in System health, and the GitHub and licence links in About, open in your browser only when you tap them.

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

ArrCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). In the app, the About section at the bottom of the Account and settings page (tap your profile picture) has **Support ArrCtrl** and **Report a bug**, which opens the [issue tracker](https://github.com/VoidedKN0X/ArrCtrl/issues). Starring the repo helps too.

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| **ArrCtrl** (this app) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [ElementCtrl](https://github.com/VoidedKN0X/ElementCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |

## License

ArrCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

The Manrope font is licensed under the SIL Open Font License; see `FONT-LICENSE-Manrope.txt`.
