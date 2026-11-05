# ArrCtrl

**One app for your whole self-hosted download stack.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/ArrCtrl?label=download)](https://github.com/VoidedKN0X/ArrCtrl/releases/latest) ![Android 13+](https://img.shields.io/badge/Android-13%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

ArrCtrl is an Android app for [Radarr](https://radarr.video/), [Sonarr](https://sonarr.tv/), [Lidarr](https://lidarr.audio/), [Prowlarr](https://prowlarr.com/), [Bazarr](https://www.bazarr.media/), [qBittorrent](https://www.qbittorrent.org/), [autobrr](https://autobrr.com/) and [Cleanuparr](https://github.com/Cleanuparr/Cleanuparr), in the spirit of nzb360 and LunaSea.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ArrCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

> **ArrCtrl 1.0.** Everything planned is in: Movies, Series, Calendar, Activity, the More tools, subtitles, notifications, widgets and the quick settings tile. Found a bug? [Open an issue](https://github.com/VoidedKN0X/ArrCtrl/issues). See the [changelog](CHANGELOG.md) for what's new.

## Features

- **Movies and Series**: your Radarr and Sonarr libraries with automatic and interactive search, monitoring, quality profiles, renaming and bulk edits
- **Music**: your Lidarr artists and albums, with add, search, monitoring and profiles (shown only when you have a Lidarr server)
- **Wanted**: Radarr, Sonarr and Lidarr's missing and cutoff-unmet lists, with search for one item, a selection or everything
- **Calendar** with upcoming movies, episodes and album releases
- **Activity**: the Radarr, Sonarr and Lidarr queues (with manual import for Radarr and Sonarr), your qBittorrent torrents with full controls, and their history
- **qBittorrent extras**: move a torrent's files (set location, with recent folders), per-torrent share limits, and the global normal and alternative speed limits
- **Add torrents** from magnet links and `.torrent` files, straight from the share sheet
- **Subtitles** through Bazarr on every movie and episode: see what's there, get any other language, or pick one yourself
- **Prowlarr** indexers and cross-indexer search, **Bazarr** subtitles, **autobrr** filters and releases, **Cleanuparr** stats and jobs
- **System health** for every server in one place, with low disk space warnings
- **Notifications**: background checks, plus instant push through [ntfy](https://f-droid.org/packages/io.heckel.ntfy/) and your own ntfy server
- **Widgets** for upcoming releases and active downloads, and a quick settings tile for qBittorrent's alternative speed limits
- **Every service is optional**, and you can add more than one of each (a second Radarr for 4K, say)
- **Import all your servers at once** by pasting one block of settings or scanning it as a QR code
- **Play in JellyCtrl** and **Open in SeerrCtrl** buttons when those apps are installed, and `arrctrl://` links that let JellyCtrl and SeerrCtrl open a movie or series in ArrCtrl (or add it)
- **The Ctrl / Signal look**: high-contrast and shared by every Ctrl app, dark or light following your phone's theme, with Space Grotesk and JetBrains Mono type
- **Self-signed certificates** trusted per server, by fingerprint
- **App lock** with its own PIN and optional fingerprint unlock; your API keys and passwords stay encrypted while the app is locked
- **API keys and passwords are encrypted** on your phone with the Android Keystore, and the server and import screens always block screenshots. Optional **Hide in screenshots** and **Hide in recent apps** switches cover the rest of the app

## Requirements

- Android 13 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- Any of: Radarr, Sonarr, Lidarr, Prowlarr, Bazarr, qBittorrent, autobrr, Cleanuparr
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google services, and the app never contacts Google. It works the same on de-Googled phones (GrapheneOS, CalyxOS, /e/OS, LineageOS without Google apps).

## Install

1. On your phone, open the [Releases page](https://github.com/VoidedKN0X/ArrCtrl/releases/latest), scroll down to **Assets** and tap the file that ends in `.apk` (for example `ArrCtrl-v1.6.3.apk`). The "Source code" zip and tar.gz files are not the app; you don't need them.
2. Open the downloaded file. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings and servers are kept.

**Automatic updates:** on your phone, tap one of the buttons below.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds ArrCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ArrCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/ArrCtrl` in Obtainium by hand.

## Is it safe to install?

- **Open source.** All of ArrCtrl's code is in this repo, under the GPL-3.0 license. You can read every line.
- **Built by GitHub, not on a personal computer.** Every release on the [Releases page](https://github.com/VoidedKN0X/ArrCtrl/releases) is built by GitHub Actions straight from the code in this repo, using [the build workflow](.github/workflows/build.yml).
- **Always signed with the same key.** Android refuses to install an update that is signed with a different key, so nobody else can push an "update" over your copy. The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key too. Its SHA-256 certificate fingerprint is:

  ```
  EF:DF:6A:A1:18:80:D2:85:68:2D:55:9C:48:4E:E7:02:28:24:26:A5:82:A8:10:AF:FC:C7:E8:1D:53:B7:16:CE
  ```

  Advanced users can compare it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).
- **Check a download.** Each release's notes end with the APK's SHA-256, so you can check the file you downloaded (`sha256sum ArrCtrl-vX.Y.Z.apk`). With the [GitHub CLI](https://cli.github.com/), `gh attestation verify ArrCtrl-vX.Y.Z.apk -R VoidedKN0X/ArrCtrl` proves the APK was built by this repo's build workflow from the tagged commit. The signing-certificate fingerprint above stays the check Android and AppVerifier use.

**Permissions** ArrCtrl asks for:

| Permission | What it's for |
| --- | --- |
| Internet (`INTERNET`) | Talking to your own servers (and your ntfy server when you tap **Send a test wake-up**). Nothing else. |
| Network state (`ACCESS_NETWORK_STATE`) | Waiting for a connection before background checks and widget refreshes. |
| Camera (`CAMERA`) | Only for scanning a server-settings QR code, asked for when you tap **Scan QR**. The code is read on the phone. |
| Notifications (`POST_NOTIFICATIONS`) | Notifications about your downloads, asked for when you turn them on. |
| Hide floating windows (`HIDE_OVERLAY_WINDOWS`) | Hide other apps' floating windows over the PIN screen, and over the lock screen and confirmations, only while they show. Granted at install, without a prompt. |
| Fingerprint (`USE_BIOMETRIC`) | Optional fingerprint unlock for the app lock. |
| Ignore battery optimisations (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) | Lets the **Battery** row on the Notifications screen ask Android not to delay ArrCtrl's checks, so notifications arrive on time. Nothing changes unless you say OK in Android's own dialog. |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Putting the background checks and widget refreshes back on schedule after the phone restarts. Added by Android's background work library (WorkManager). |
| Keep awake (`WAKE_LOCK`) | Keeping the phone awake for the few seconds a check runs. Added by WorkManager. |

`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is an internal permission Android adds to every app built for Android 13 and newer; it keeps ArrCtrl's own broadcasts private. ArrCtrl has no location, microphone, contacts, storage or foreground-service permission.

See [Privacy](#privacy) for what ArrCtrl does and doesn't send.

## Getting started

1. Tap your profile picture at the top right of any tab, then **Add a server** on the **You** page.
2. Pick the service and enter its address, e.g. `http://192.168.1.10:7878` or `https://radarr.example.com`. Plain `http://` works for local addresses and over Tailscale. Without `http://` or `https://`, local and Tailscale addresses (like `192.168.1.10`, `100.101.102.103`, `nas.lan` or `nas.tailnet-name.ts.net`) get `http://`, and everything else, including public IP addresses, gets `https://`; type `http://` yourself to use plain HTTP anyway. Add the path too if it runs under one behind a reverse proxy (`https://example.com/radarr`).
3. Enter the API key (in the web UI under Settings → General → Security for Radarr, Sonarr, Lidarr, Prowlarr and Bazarr). For qBittorrent use your Web UI username and password, or an API key on qBittorrent 5.2 or newer.
4. Tap **Test**, then **Save**. If the server uses a self-signed certificate, tap **Review certificate**, check the fingerprint and trust it. ArrCtrl then accepts that exact certificate from that server only.

Every service is optional, and you can add more than one of each.

Your servers, **Settings** and **About** are all on the **You** page behind your profile picture. The **More** tab holds **Wanted**, **Music** (with a Lidarr server) and the extra tools (Prowlarr, Bazarr, autobrr, Cleanuparr and System health).

### Import all servers at once

Instead of typing eight API keys on your phone, put them in one JSON block and either paste it (**Import servers** → **Paste**) or turn it into a QR code and scan it (**Scan QR**).

```json
{
  "arrctrl": 1,
  "servers": [
    { "type": "radarr",      "url": "http://192.168.1.10:7878", "apiKey": "…" },
    { "type": "radarr",      "name": "Radarr 4K", "url": "http://192.168.1.10:7879", "apiKey": "…" },
    { "type": "sonarr",      "url": "https://sonarr.example.com", "apiKey": "…" },
    { "type": "lidarr",      "url": "http://192.168.1.10:8686", "apiKey": "…" },
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
| `type` | yes | `radarr`, `sonarr`, `lidarr`, `prowlarr`, `bazarr`, `qbittorrent`, `autobrr` or `cleanuparr` |
| `url` | yes | The server address. Without `http://` or `https://`, local and Tailscale addresses (private IPs, `100.64.0.0/10`, `fd7a:115c:a1e0::/48`, single-word names and `.local`, `.lan`, `.home`, `.internal`, `.home.arpa`, `.ts.net` names) get `http://`, anything else `https://` |
| `apiKey` | yes, except qBittorrent | The service's API key. For qBittorrent, an API key replaces username and password |
| `username`, `password` | qBittorrent only | Your qBittorrent Web UI login, when not using an API key |
| `name` | no | Shown in the app. Defaults to the service name, numbered when there's more than one |
| `certSha256` | no | SHA-256 fingerprint of a self-signed certificate to trust, with or without colons |

A plain list of servers (without the `"servers"` wrapper) works too. A server already saved with the same service and address is updated rather than added twice.

To make a QR code on Linux, save the JSON as `arrctrl.json` and run `qrencode -t ansiutf8 < arrctrl.json` (or `-o arrctrl.png` for an image). **The JSON and the QR code contain your API keys and passwords.** Delete them once you've scanned them, and don't share screenshots of them.

### Adding torrents

Tap **Add torrent** on the Torrents tab and paste a magnet link (or several), or pick a `.torrent` file. ArrCtrl also shows up when you open a `magnet:` link or a `.torrent` file, and in the share sheet. To make it the default for magnet links, open one and pick **ArrCtrl** → **Always**, or set it in Android's Settings → Apps → Default apps → Opening links.

### Links from other apps

Other apps (JellyCtrl and SeerrCtrl do) can open a movie or series in ArrCtrl:

- `arrctrl://movie?tmdb=603` opens the movie with that TMDB id in Radarr, or **Add movie** looked up by that id when it isn't in any of your Radarr servers.
- `arrctrl://series?tvdb=81189&tmdb=1396` opens the series in Sonarr (by TVDB id, or TMDB id when there's no TVDB id), or **Add series** when it isn't there yet.

A link only opens a screen, after the app lock; it never adds or searches anything until you tap. Web pages can't open these links.

### Notifications

Turn them on: tap your profile picture › **Settings › Notifications**. ArrCtrl checks your servers itself and tells you about downloads, failed or stuck downloads, finished torrents, health warnings, low disk space and servers that stop answering. You choose which kinds, and which servers. Nothing goes through any outside service.

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
| **Radarr**, **Sonarr**, **Lidarr**, **Prowlarr** | Settings → Connect → **+** → **ntfy** | Server URL: the ntfy server. Topics: the topic. Tick the events you care about (On Grab, On Import, On Health Issue, and so on) and **Test**. |
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

Long-press your home screen → Widgets → ArrCtrl. Both refresh every 30 minutes, when you open ArrCtrl and when you tap the refresh button; Downloads also refreshes every couple of minutes while something is downloading. Tap a row to open it in ArrCtrl. They use ArrCtrl's Signal colours, dark or light following your phone's theme.

The **Alt. speed** tile (pull down Quick Settings → edit → drag it in) turns qBittorrent's alternative speed limits on or off for all your qBittorrent servers, like the button on the Torrents tab. On the phone's lock screen it asks you to unlock the phone first. Long-press it to open the Torrents tab.

While ArrCtrl's app lock is locked, the widgets only say **Locked** and the tile says **ArrCtrl is locked**; tapping either opens ArrCtrl to its lock screen. They show your data again once you unlock.

## App lock

Turn it on: tap your profile picture › **Settings › Privacy and security › App lock**. It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, such as the fingerprint sensor, checked by the phone's secure hardware. Face unlock that only uses the camera isn't accepted. The PIN is still needed after the phone restarts and at least every 72 hours, and adding a new fingerprint to the phone turns fingerprint unlock off until you turn it on again.
- **Your API keys and passwords are encrypted with the PIN while ArrCtrl is locked** (unless notifications are on, see below). The key only exists in memory while the app is unlocked and is wiped the moment it locks, together with qBittorrent's sign-in sessions. The PIN itself is never stored: it's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware, so a copy of the app's files isn't enough to guess it on a computer.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Picking a `.torrent` file, opening JellyCtrl or SeerrCtrl, a help link, ntfy, the share sheet and Android's notification and battery settings don't count as leaving, unless you stay away for more than 5 minutes.
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too.
- **Forgot your PIN?** It can't be recovered, because it encrypts your API keys and passwords. The lock screen offers to remove your servers with their API keys and passwords, and what the widgets and notifications remembered, so you can add them again; nothing changes on your servers.
- **Notifications and the app lock:** background checks and instant notifications need to sign in while ArrCtrl is locked. With notifications on, your API keys and passwords stay encrypted with the Android Keystore only, as without the app lock, and notifications keep working. Turn notifications off to keep them encrypted with your PIN too. Either way, while ArrCtrl is locked the widgets only say **Locked** and the Alt. speed tile opens ArrCtrl instead of changing anything, until you unlock.

**Hide in screenshots** and **Hide in recent apps** (the last two switches in the same section, also without the app lock, both off by default): the first keeps ArrCtrl out of screenshots and screen recordings (and blanks its recent-apps preview), the second shows a blank card for ArrCtrl in the recent-apps view while screenshots still work. With the app lock on, the recent-apps view always shows a blank card. The server and import screens, the lock screen and the PIN screens are always kept out of screenshots.

## Privacy

**Telemetry-free and Google-free.** ArrCtrl has no analytics, crash reporting, usage stats, ads or tracking of any kind, and no Google libraries or services.

Everything ArrCtrl connects to:

- **The servers you add**, at exactly the address you entered. API keys and passwords are only ever sent to their own server, and a redirect to any other address (another site, another port, or from https down to plain http) is refused before anything is sent there.
- **Your ntfy server**, only when you tap **Send a test wake-up** on the Notifications screen. Instant notifications themselves arrive through the ntfy app; ArrCtrl only receives a wake-up call from it.
- **Nothing else.** Posters, backdrops and album covers come from your own Radarr, Sonarr and Lidarr servers, never straight from sites like TMDB, MusicBrainz or fanart.tv, and QR codes are read on the phone.

Help links in System health, **Install ntfy** (f-droid.org) and the GitHub and licence links in About open in your browser only when you tap them.

## How ArrCtrl is made

ArrCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

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

**Dependency verification:** every library and Gradle plugin the build downloads is checked against its SHA-256 in `gradle/verification-metadata.xml`, and the build stops if one doesn't match. When you add or update a dependency or plugin, run the **Dependency verification metadata** workflow from the Actions tab, check the diff of the file it prints, and commit the new `gradle/verification-metadata.xml` in the same push.

## Support the project

ArrCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). In the app, the About section at the bottom of the **You** page (tap your profile picture) has **Support ArrCtrl** and **Report a bug**, which opens the [issue tracker](https://github.com/VoidedKN0X/ArrCtrl/issues). Starring the repo helps too.

Found a bug or have an idea? [Open an issue](https://github.com/VoidedKN0X/ArrCtrl/issues/new/choose) and pick **Bug report** or **Feature request**; the form asks for what's needed.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same Material You design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| **ArrCtrl** (this app) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| [ClockCtrl](https://github.com/VoidedKN0X/ClockCtrl) | Alarms, timers, stopwatch and world clock |
| [ContactCtrl](https://github.com/VoidedKN0X/ContactCtrl) | Contacts: browse, edit, import and export |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [DateCtrl](https://github.com/VoidedKN0X/DateCtrl) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| [SynapseCtrl](https://github.com/VoidedKN0X/SynapseCtrl) | Administer your Matrix Synapse server |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| [WrtCtrl](https://github.com/VoidedKN0X/WrtCtrl) | Manage your OpenWrt router |

## License

ArrCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

ArrCtrl is an independent app and isn't made by or affiliated with the Radarr, Sonarr, Lidarr, Prowlarr, Bazarr, qBittorrent, autobrr or Cleanuparr projects.

The Space Grotesk and JetBrains Mono fonts are licensed under the SIL Open Font License 1.1; see `FONT-LICENSE-SpaceGrotesk.txt` and `FONT-LICENSE-JetBrainsMono.txt`.
