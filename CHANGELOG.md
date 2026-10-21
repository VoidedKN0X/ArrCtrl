# Changelog

What changed in each ArrCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/ArrCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## v1.2.1 (2026-10-21)

### Changed

- **Hide in screenshots** and **Hide in recent apps** are now two separate switches in Settings › Privacy and security, both off by default, the same in every Ctrl app. **Hide in screenshots** keeps ArrCtrl out of screenshots and screen recordings (and blanks its preview in recent apps); **Hide in recent apps** only shows a blank card in the recent apps view, so screenshots still work. Until now ArrCtrl's preview in recent apps was always blank: it now shows the app unless you turn on **Hide in recent apps** (or the app lock). If you turned on the single switch in 1.2.0, both new switches start on
- **Always protected, whatever the switches say:** the server and import screens (API keys, passwords and pasted server settings), the lock screen and the PIN screens are never in screenshots or screen recordings, and with the app lock on the recent apps view always shows a blank card
- **Notifications moved into Settings**, between Appearance and Privacy and security, the same order as in the other Ctrl apps. The Account page keeps the Settings row
- **README**: the App lock section uses the same wording as the other Ctrl apps and describes the two switches

## v1.2.0 (2026-10-21)

The app lock: the same one as in every other Ctrl app, with its own PIN, optional fingerprint unlock and your API keys and passwords sealed while ArrCtrl is locked.

### New

- **App lock** (**Account › Settings › Privacy and security › App lock**, off until you turn it on). ArrCtrl asks for its own app PIN when you open it. The PIN needs at least 6 digits and is separate from your phone's; very obvious ones like 111111 or 123456 are refused. You choose it twice when turning the lock on
- **Unlock with fingerprint** (optional): only strong biometrics such as your fingerprint, not face unlock that only uses the camera, which a photo can sometimes fool. The PIN is still needed after the phone restarts and at least every 72 hours, the same as Android does for your screen lock
- **Lock again**: as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts. Picking a `.torrent` file, opening JellyCtrl or SeerrCtrl, a help link, ntfy, the share sheet or Android's notification and battery settings from ArrCtrl doesn't count as leaving, unless you stay away longer than 5 minutes
- **Change app PIN** asks for your current PIN first, and turning the app lock off asks for the PIN too
- **Forgot PIN?** on the lock screen removes your servers with their API keys and passwords, and what the widgets and notifications remembered, so you can add them again. Nothing changes on your servers
- **Hide in screenshots and recent apps** (Settings › Privacy and security, off by default): blocks screenshots and screen recordings everywhere in ArrCtrl. The server and import screens still always block them, and ArrCtrl's preview in recent apps stays blank as before

### Changed

- **Settings** now has a **Privacy and security** group under Appearance, the same as in the other Ctrl apps, and the Settings row on the Account page says "Appearance, privacy and app lock"
- **"Use wallpaper colours" is now "Dynamic colour"** ("Use colours from your wallpaper"), and the Theme and Dynamic colour rows have icons, like in the other Ctrl apps
- **Notifications with the app lock on:** background checks and instant notifications need to sign in while ArrCtrl is locked, so with notifications on your API keys and passwords stay encrypted with the Android Keystore only, as without the app lock, and notifications, widgets and the Alt. speed tile keep working. The Privacy and security group says so while it applies. Turn notifications off to keep the keys sealed with your PIN too
- **Widgets and the tile while locked (notifications off):** the widgets keep showing what they last loaded, and the Alt. speed tile shows "ArrCtrl is locked" and opens ArrCtrl when tapped. Both refresh again once you unlock
- **README**: new App lock section, and AdCtrl and MemoCtrl added to the Ctrl series table

### Security

- **Your API keys and passwords are sealed while ArrCtrl is locked** (with notifications off). They're also encrypted with a key that only exists in memory while ArrCtrl is unlocked and is wiped the moment it locks, together with qBittorrent's sign-in sessions. A locked ArrCtrl, or a copy of its files, can't sign in to your servers. While locked, none of ArrCtrl's screens are kept, so nothing can be read from them
- **The PIN can't be guessed off the phone.** It's never stored. It's stretched with 600,000 rounds of PBKDF2 and mixed with a secret held in the phone's secure hardware (StrongBox where available), so copying ArrCtrl's files to a computer isn't enough to try PINs there
- **Wrong PINs lock the screen**: after 5 tries you wait 30 seconds, doubling with every further 5 wrong tries up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't reset the wait
- **Fingerprint unlock can't be faked**: the fingerprint has to unlock a key in the phone's secure hardware, so a tampered "success" on a rooted phone doesn't open the app. Adding a new fingerprint turns fingerprint unlock off until you turn it on again with your PIN

## v1.1.1 (2026-10-20)

### Changed

- **64-bit ARM phones only.** ArrCtrl now ships only the 64-bit ARM (arm64-v8a) build that nearly every phone from the last several years uses, like every Ctrl app, so the download is smaller. Old 32-bit phones and x86 devices (emulators, some Chromebooks) are no longer supported.
- **README**: Requirements say 64-bit ARM phones only

## v1.1.0 (2026-10-20)

### Changed

- **Push notifications through ntfy only.** Instant notifications now always come through the [ntfy app](https://f-droid.org/packages/io.heckel.ntfy/), and other push apps can no longer be used. If you already used ntfy, nothing changes: your push address stays the same, so your servers don't need new settings. If you used another push app, ArrCtrl removes its registration there and, if ntfy is installed, registers with ntfy instead; your servers then need the new push address shown under **Notifications** (the README explains where each service takes it). Without ntfy, instant notifications are off until you install it. Background checks keep running either way
- **"Instant with UnifiedPush" is now "Instant with ntfy"**, and the texts on the Notifications screen talk about ntfy instead of "distributors"
- **ntfy isn't installed?** The Notifications screen now says so, with an **Install ntfy** button that opens ntfy on F-Droid. It checks again whenever you come back to the screen, so it updates as soon as ntfy is installed
- **ntfy battery warning**: with instant notifications on, the Notifications screen warns when ntfy is battery-restricted (Android can then cut its connection while the phone sleeps), and tapping it opens ntfy's App info so you can set its battery usage to Unrestricted
- **ntfy temporarily unavailable**: when ntfy reports that your ntfy server is down, the Instant with ntfy row says so. Your push address stays the same and wake-ups resume by themselves once the server is back
- **Why push couldn't be turned on**: if ntfy refuses to register ArrCtrl, the row now shows ntfy's reason instead of turning off silently
- **New QR scanner for Import servers.** **Scan QR** now opens ArrCtrl's own full-screen camera view instead of a separate scanner screen. It asks for the camera when it opens, reads the code on the phone with the open-source zxing-cpp decoder, and closes as soon as it has read your settings

### Privacy

- **De-Googled.** The push library ArrCtrl used is gone, along with the Google crypto library (Tink) it brought along, its built-in check for Google Play services and its references to Firebase. ArrCtrl now talks to ntfy directly. The old QR scanner, which was built on Google's ZXing library, is replaced too (see above). ArrCtrl contains no Google libraries or services and never contacts Google, and the app's texts no longer mention Google
- **README**: the Google-free line now reads "Google-free and de-Googled", notifications are described as ntfy-only with background checks as a fallback, and the ntfy setup steps are updated

## v1.0.4 (2026-10-20)

### Privacy

- **No more usage stats: ArrCtrl is now telemetry-free.** The optional anonymous usage stats are gone completely, along with the Usage stats setting and the one-time question. The app no longer contacts VKNXWorks' stats server at all, and the settings it used are deleted from your phone when you update. ArrCtrl now only talks to the servers you add (and your own push distributor, if you use instant notifications)

### Changed

- **README**: a Screenshots section (screenshots are coming soon), and a link preview image (`.github/assets/social-preview.png`) with ArrCtrl's icon, name and summary, for when the repo is shared on Reddit, Discord or elsewhere. It also gets a "Telemetry-free" badge and tag, and a Privacy section that lists exactly what ArrCtrl connects to, in place of the old Usage stats section

## v1.0.3 (2026-10-19)

### Changed

- **One About section in every Ctrl app**: Support, Report a bug, Source code and Licence (GPL-3.0), with the version underneath, at the bottom of the Account and settings page (tap your profile picture). The rows are in the same order, with the same wording and icons, as in the other Ctrl apps. Report a bug now reads "Found a problem or have an idea? Open an issue on GitHub", Source code shows the repository address, and the new Licence row opens the full GNU General Public License v3.0

## v1.0.2 (2026-10-19)

### Changed

- **Support ArrCtrl** under About on the Account and settings page (tap your profile picture) opens VKNXWorks' GitHub Sponsors page. It replaces the old "Support development" row: it now has a heart icon and reads "ArrCtrl is free and open source. Sponsor development on GitHub", the same as in the other Ctrl apps

## v1.0.1 (2026-10-19)

### Fixed

- **Play in JellyCtrl opens the right movie or episode.** JellyCtrl kept saying the movie or series wasn't in your Jellyfin library. ArrCtrl now sends JellyCtrl the title, year and every id it knows (TMDB, IMDb and, for series, TVDB), so JellyCtrl can find it however Jellyfin has it catalogued. Update JellyCtrl to 0.1.21 or newer as well
- **The Play in JellyCtrl button shows for every downloaded movie and episode**, also when Radarr or Sonarr has no TMDB or TVDB id for it

## v1.0.0 (2026-10-19)

ArrCtrl 1.0: everything that was planned is in. Movies and Series from Radarr and Sonarr, the Calendar, the Activity tab with your queues, torrents and history, Prowlarr, Bazarr, autobrr and Cleanuparr, System health, subtitles in any language, notifications (with instant push through your own ntfy server), home screen widgets and the Alt. speed tile.

### Changed

- **Play in JellyCtrl opens the movie or episode.** With JellyCtrl 0.1.20 or newer, the Play in JellyCtrl button on a movie page or in an episode's sheet opens that movie or episode in JellyCtrl, ready to play, instead of only starting the app. If the episode isn't in Jellyfin yet you land on the series; if the movie or series isn't there, JellyCtrl tells you. Older JellyCtrl versions still just open
- **Open in SeerrCtrl opens the movie or series.** With SeerrCtrl 1.4.7 or newer, the Open in SeerrCtrl button on a movie or series page opens that movie or series in SeerrCtrl instead of only starting the app
- **README:** the "early development" note is gone; it now says ArrCtrl 1.0 is complete and where to report bugs

## v0.9.1 (2026-10-19)

ArrCtrl is now completely Google-free. Nothing changes in how you use it.

### Privacy

- **Emoji no longer make ArrCtrl ask Google for a font.** A standard Android library inside the app asked Google Play services for a downloadable emoji font every time ArrCtrl started, which could make Google Play services fetch that font from Google. This lookup is now switched off: emoji are drawn with your phone's own emoji font. On phones without Google Play services nothing changes; on other phones, emoji can look slightly different if the phone's built-in emoji font is older.
- **Checked: ArrCtrl is Google-free.** It contains no Google Play services, Firebase, ML Kit, Google Cast or Play Core, and it doesn't load fonts or anything else from Google's servers. It also never switches server addresses by itself: it always uses the address you set up, and only you can change it.

### Changed

- **README: new Google-free line** under Requirements: works without Google Play services, contains no Google libraries that contact Google, and draws emoji with your phone's own emoji font.
- **README brought up to date with 0.9.0.** The feature list now also mentions the Radarr and Sonarr history on the Activity tab, importing all your servers at once by paste or QR code, the Play in JellyCtrl and Open in SeerrCtrl buttons, the light, dark and pure black themes with wallpaper colours, and that API keys and passwords are encrypted on your phone and kept out of screenshots and recent apps. It now points to **Account and settings** (the button at the top right) for notifications and usage stats, the "Notifications arrive late?" tips sit under Notifications, and a new paragraph lists everything ArrCtrl connects to besides your own servers. No change to the app itself

## v0.9.0 (2026-10-19)

Home screen widgets and a Quick Settings tile.

### New

- **Upcoming widget**: movies and episodes from all your Radarr and Sonarr servers for the next two weeks, grouped under Today, Tomorrow and the date. Episodes show their number, title and air time; movies show whether it's the cinema, digital or physical release. A coloured dot shows the state: amber for upcoming, red for out but missing, grey for unmonitored. Only monitored items are shown, and downloaded ones drop off
- **Downloads widget**: what's downloading right now, with a progress bar, percentage and time left for each, and the total qBittorrent download speed in the title. It uses the Radarr and Sonarr queues (so you see movie and episode names), or your qBittorrent torrents when there's no queue
- Both widgets can be resized, refresh every 30 minutes, when you open ArrCtrl and with their refresh button, and show when they were last updated. While something downloads, the Downloads widget refreshes every couple of minutes. Tapping a row opens the movie, series, Calendar or Activity tab in ArrCtrl
- Widgets follow ArrCtrl's amber colours in light and dark mode, or your wallpaper colours when "Use wallpaper colours" is on
- **Alt. speed tile** in Quick Settings: turns qBittorrent's alternative speed limits on or off for all your qBittorrent servers at once, like the button on the Torrents tab. It shows On, Off, "On for some servers", or when a server can't be reached. With no qBittorrent server it opens ArrCtrl instead, and long-pressing it opens the Torrents tab
- The tile updates straight away when you toggle alternative speed limits inside the app

## v0.8.2 (2026-10-19)

A security and privacy update: safer handling of shared .torrent files, redirects and certificates, less on the lock screen and in screenshots, and a fix for qBittorrent API keys.

### Security

- **Shared and opened .torrent files are checked before they're read.** ArrCtrl now only accepts .torrent files handed over by another app through Android's normal file sharing. `file://` paths and links into ArrCtrl's own storage are refused, so another app can no longer get ArrCtrl to read one of its own private files and send it to your qBittorrent. The file is read in small pieces and stops at 10 MB instead of being loaded whole first, it must actually look like a .torrent file, and its name is cleaned up before it's sent to qBittorrent
- **Nothing follows a redirect away from your server.** If a server or reverse proxy answers with a redirect to another address (another site, another port, or from https down to plain http), ArrCtrl no longer re-sends your qBittorrent password, an uploaded .torrent file or any other change there; the request stops with an error instead. API keys already never followed such redirects
- **qBittorrent's sign-in cookie stays with that server.** The session cookie is only accepted from, and only sent back to, the exact address you saved (same host and port), never to other ports on the same machine
- **Trusted certificates stay with their server.** A self-signed certificate you trust belongs to that exact address. If you change a server's address to another host or port, the old trust no longer applies and ArrCtrl asks you to review the new server's certificate
- **Imports show the certificate they trust.** When imported server settings include a certificate fingerprint, the preview shows it before you add them. Importing settings for a server you already have keeps its trusted certificate unless the import names a different one
- **No screenshots of API keys.** The add/edit server screen and the Import screen now block screenshots and screen recording, and pasted import text (which holds keys and passwords) is no longer kept in Android's saved app state
- **Hidden in recent apps.** ArrCtrl's preview in the recent-apps screen is now blank, so your servers, downloads and keys don't show there
- **Less on the lock screen.** On a locked phone, notifications now show only the kind of event (for example "Downloaded" or "Health warnings") and the active-downloads notification only the count and speed, never movie, series, torrent or server names, unless you've allowed sensitive notification content on the lock screen in Android's settings
- **"Can't reach" notifications no longer show raw errors.** When qBittorrent can't be reached, the notification gives a plain reason (for example "Connection refused") instead of the technical error, which could include the server's address
- **Health links open only web pages.** Links in System health come from your servers; ArrCtrl now only opens them if they're normal http(s) web links
- **Push address copied privately.** Copying the push address, ntfy server or topic marks it as sensitive, so Android doesn't show it in the clipboard preview
- **Usage stats switched off means nothing more is sent.** Turning usage stats off now also throws away any event that was still waiting to be sent, and the next session after switching back on starts fresh
- **Images from a removed server are cleared.** Removing a server also clears ArrCtrl's cached posters and fanart

### Fixed

- **qBittorrent API keys now work.** ArrCtrl sent the API key in a header qBittorrent doesn't read, so signing in with an API key failed. It now uses the `Authorization: Bearer` header qBittorrent expects (qBittorrent 5.2 or newer); the help text under the field says which versions support keys

### Changed

- **"Not encrypted (HTTP)" in the server list.** Servers you reach over plain http now say so in Account → Servers and in the Import preview, not only on the edit screen
- **All Ctrl apps in the README.** The README's Ctrl series table now lists all 14 apps. New in the table: [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) (scan and browse documents on Paperless-ngx), [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) (Bambu Lab printers through Bambuddy, and Spoolman spools), [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) (manage your Proxmox VE server) and [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) (monitor and manage your TrueNAS server). Every app in the table is also in the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) for automatic updates. No change to the app itself.
- The README now says API keys need qBittorrent 5.2 or newer

## v0.8.1 (2026-10-19)

### New

- **Optional anonymous usage stats.** After you add your first server, ArrCtrl asks once whether you'd like to share anonymous usage stats. Nothing is sent unless you tap **Share stats**, and you can switch it on or off any time in **Account → Settings → Usage stats**
- **What's shared:** which screens you open (for example "Movies", "Movie", "Activity" or "Prowlarr", never which movie, series, torrent or server), when you add a movie, series or torrent, start a search or grab a release and which kind of service did it (Radarr, Sonarr, Prowlarr or qBittorrent), the app version, Android version, phone model, language, phone or tablet, your theme, and whether notifications are on
- **What's never shared:** your server addresses, API keys, usernames, passwords, which movies, series, releases or torrents, what you search for, or any ID that follows you between sessions. Each session gets a new random ID, and stats from before and after you switch them off and on again aren't linked
- **Where it goes:** to VKNXWorks' own self-hosted server (Aptabase), not to Google or any analytics company. Builds you make from source can't send stats at all, and the setting doesn't appear in them

### Changed

- The README has a new **Usage stats** section listing exactly what's collected

## v0.8.0 (2026-10-19)

Notifications, and subtitles in any language for every movie and episode.

### New

- **Notifications**, under Account (the profile button) → Notifications. They're off until you turn them on, and Android asks for permission the first time. ArrCtrl checks your servers itself; nothing goes through Google, Firebase or Play Services
- **Choose what you're told about**: downloaded and imported, download failed, needs attention (for example a download that can't be imported automatically), health warnings, low disk space (using the threshold from System health) and servers that stop answering are on by default; grabbed and torrent finished can be turned on. Each kind is also its own channel in Android's notification settings, so you can change its sound or silence it there
- **Choose which servers** notify you, one switch per server
- **Background checks** every 15 minutes, 30 minutes (the default), hour or 3 hours, or only when woken by push. **Check now** checks straight away and the screen shows when the last check ran
- **Instant notifications with UnifiedPush**: with a distributor app such as ntfy, your servers wake ArrCtrl the moment something happens and ArrCtrl checks and notifies you. The app shows the push address to give your servers, already split into the ntfy server and topic (tap to copy, or share it), and **Send a test wake-up** to try it. The message is only a wake-up call; its content is ignored. The README has step-by-step setup for Radarr, Sonarr, Prowlarr, Bazarr, autobrr, Cleanuparr and qBittorrent
- **Active downloads**: an optional quiet, ongoing notification with what's downloading, how far along it is and the total speed. Off by default; it updates about once a minute while something downloads and disappears when nothing does
- **Battery**: the Notifications screen shows whether Android is holding ArrCtrl back to save battery and lets you lift that, with a shortcut to the app settings for phones (such as OPPO/ColorOS, Xiaomi and Samsung) that also need background activity allowed there
- Tapping a notification opens the movie or series it's about, the Activity tab, or System health. When one check finds lots of one kind at once, they're bundled into a single notification
- The first check after turning notifications on only notes what's already there, so you aren't flooded with old events
- **Subtitles on movie pages**: when Bazarr has the movie, a Subtitles section shows which subtitle languages it has and which are missing. Tap it for the full subtitles screen
- **Subtitles for episodes**: a Subtitles button in an episode's sheet (for downloaded episodes) opens the same screen
- **The subtitles screen** lists every subtitle with its language, whether it's hearing impaired or forced, and whether it's a separate file (with name and size) or inside the video. From there you can:
  - **Search another language**: any language Bazarr knows, even one that isn't in the languages profile, optionally hearing impaired or forced. Your enabled languages are listed first, with all languages a tap away and a search box
  - Tap a missing language to search for just that one
  - **Search manually** to pick a subtitle yourself
  - **Change the languages profile** (for an episode, it applies to the whole series)
  - **Delete** a subtitle file you don't want (subtitles inside the video can't be deleted)
- **Subtitles on series pages**: the series' languages profile, with a button to change it, and a search for all its missing subtitles
- ArrCtrl finds the right Bazarr by itself when you have more than one, by checking the IMDb or TVDB id

## v0.7.1 (2026-10-19)

Bazarr fixes, and you can now choose which subtitle languages Bazarr gets.

### New

- **Change subtitle languages** for a movie or a series. Tap a wanted item in Bazarr and choose **Change languages**, or use the languages button at the top of a manual subtitle search. Pick any of the languages profiles you've set up in Bazarr (each shows the languages in it, and whether they're hearing impaired or forced), or None to stop downloading subtitles for it. For episodes, the profile applies to the whole series, as it does in Bazarr. Bazarr then works out again what's missing, and a manual search runs again in the new languages
- **Language filter** in manual subtitle search: when results come in more than one language, chips at the top show just one language at a time
- **Reset throttled providers** from the Providers tab, so Bazarr uses them again straight away instead of waiting for the retry time

### Fixed

- The Bazarr Providers tab showed every working provider as failing, with a warning sign and "Good · retry -". Working providers now show a tick and "Working", throttled ones show a warning with the reason and when Bazarr will retry, problems are listed first, and the top shows how many providers are working. Pull down to refresh

## v0.7.0 (2026-10-19)

The More tab comes alive: Prowlarr, Bazarr, autobrr and Cleanuparr each get their own screen, and System health shows every warning from every server in one place.

### New

- **Prowlarr: indexers.** Every indexer with its protocol, privacy (public, semi-private or private) and priority, plus a summary of how many are enabled and how many are failing. Failing indexers show since when, or until when Prowlarr has paused them after repeated failures
- **Prowlarr: enable, disable and test.** Switch an indexer on or off with its toggle, or test it from its menu; a failed test shows Prowlarr's reasons
- **Prowlarr: search.** Search all your indexers at once. Results show the indexer, size, age, seeders and peers, categories and grab count. Tap one to send it to the download client set up in Prowlarr (the app warns that Radarr and Sonarr won't import it automatically)
- **Prowlarr: stats.** Queries, grabs and failures for each indexer, with the average response time
- **Bazarr: wanted.** Missing subtitles for movies and episodes, with the languages that are missing. Search one automatically, search a whole series, or search everything that's wanted at once
- **Bazarr: manual subtitle search.** See every subtitle your providers offer, with its score, language, provider, whether it's hearing impaired or forced, and what does or doesn't match your file. Tap one to download it
- **Bazarr: history** of downloaded subtitles with their language, provider and score, and **providers** that are throttled or failing, with when Bazarr will retry them
- **autobrr: stats** for releases seen, pushed, rejected and failed
- **autobrr: releases.** The latest announced releases with their indexer, size, which filter caught them and what happened: approved or rejected by the filter, pushed, rejected by the download client, or failed to push. Load older ones at the bottom
- **autobrr: filters.** Turn each filter on or off with its toggle
- **autobrr: IRC.** Each network and channel, whether it's connected and being monitored, and when the last announce came in. autobrr's IRC passwords are never read or shown
- **Cleanuparr: stats** for the last 24 hours, 7 days or 30 days: downloads removed, strikes given, downloads that recovered and jobs that ran fine
- **Cleanuparr: connections** to your download clients and *arr apps, with any that can't be reached shown in red
- **Cleanuparr: jobs** with when each last ran and runs next. **Run now** starts a job once straight away (Seeker can't be started by hand, so it has no button)
- **Cleanuparr: recent strikes**, with strikes from dry runs marked, and the **event log**, with older events at the bottom
- **System health** (the Health row in More): warnings from Radarr, Sonarr and Prowlarr, Bazarr's health checks, autobrr IRC networks or channels that are down, Cleanuparr download clients it can't reach, and qBittorrent servers that don't respond. Servers that are fine say so; the count of problems is at the top
- **Disk space** for every Radarr and Sonarr root folder and drive, as a bar with free and total space. Drives below your warning threshold are flagged; set the threshold from 5% to 25% (10% by default)
- With more than one server of a kind, chips at the top of each tool switch between them
- Tools you haven't added a server for take you straight to adding one

### Changed

- The More tab no longer shows a "connected" line under each tool; tools without a server now offer to add one

## v0.6.0 (2026-10-19)

The Calendar tab, and a fix for the selection bar in Movies and Series.

### New

- **Calendar** with upcoming movies from Radarr and episodes from Sonarr, from all your servers, in one place
- **Agenda view** (the default): day by day from today, with "Today", "Tomorrow" and the date as headings. Load the next four weeks at a time, or go back a week at a time to see what aired recently
- **Month view**: a month grid with a dot for every movie or episode on that day (and "+n" when there are more than three). Tap a day to list it underneath; move between months with the arrows. The week starts on your phone's first day of the week
- **Colours** show at a glance what you have: downloaded, missing (aired or released and not downloaded yet), upcoming, or unmonitored. A legend at the top explains them
- **Episodes** show their air time in your time zone, the episode number and title, and whether it's a premiere or a finale
- **Movies** show which release falls on that day: cinema, digital or physical. A cinema date in the past doesn't count as missing, since the movie can't be downloaded yet
- **Filters** for movies, series or both, and for monitored items only (on by default). The view and filters are remembered
- **Today** button to jump back, and pull to refresh
- Tap an episode to open its season, or a movie to open its page
- One server that can't be reached shows a red card; the others still fill the calendar

### Fixed

- When you selected movies or series with a long-press, "1 selected" wrapped over three lines because the bar had too many buttons. The count now stays on one line: monitor, unmonitor, search and delete are buttons, and select all and change quality profile moved to the ⋮ menu

## v0.5.0 (2026-10-19)

The Series tab: your Sonarr library, down to every season and episode.

### New

- **Series library** with posters in a grid, or a list with network, number of seasons, size and quality profile. Grid or list is remembered
- **Episode progress** on every series, like 18/24, with a bar that shows complete series, series still missing episodes, ended series with gaps, and unmonitored series in different colours
- **Search, filter and sort**: filter by monitored, unmonitored, continuing, ended, missing episodes or downloading; sort by title, year, date added, next airing, last aired, size or how many episodes you have, in either direction (remembered too)
- **Several Sonarr servers** (an anime one, say): switch with the chips at the top
- **Series page** with backdrop, poster, year, network, runtime, certificate, genres, rating, status, progress and when the next episode airs
- **Seasons** with their own progress (downloaded of aired episodes) and size. Tap the bookmark to monitor or unmonitor a season; tap the season to open it
- **Season page** with every episode, its air date and whether it's downloaded, downloading, missing or not aired yet. Monitor or unmonitor single episodes, or every episode in the season at once. Search the whole season (Sonarr looks for a season pack first), or open **interactive season search** to see and grab season packs and single episodes yourself
- **Episode details**: overview, air date, runtime, absolute number, and the file's quality (and whether an upgrade is still wanted), size, video and audio, subtitles, release group and format score. Search for the episode, run an interactive search, monitor or unmonitor it, or delete its file
- **Series settings**: monitored, quality profile, series type (standard, daily or anime), season folders, root folder (choose whether the files move too) and tags
- **Search all monitored episodes**, **refresh**, and **rename** with a preview of every new file name
- **History** for the series, with the episode each event was for
- **Delete a series**, optionally with all its files and an import list exclusion
- **Add series**: search by title or `tvdb:` id, then pick the quality profile, root folder (with free space), which episodes to monitor (all, future, missing, existing, recent, pilot, first season, last season or none), series type, season folders and tags, and whether to start searching for missing episodes and for upgrades straight away. Series already in your library are marked and open their page
- **Select several series** (long-press) to monitor or unmonitor them, change their quality profile, search them or delete them
- **Play in JellyCtrl** on downloaded episodes and **Open in SeerrCtrl** on series, when those apps are installed (for now they open the app)
- Posters and backdrops come from your Sonarr server, through the same connection as everything else

## v0.4.0 (2026-10-19)

The Movies tab: your whole Radarr library, with everything you need to manage it from your phone.

### New

- **Movie library** with posters in a grid, or a compact list with size and quality profile. The choice is remembered
- **Status badges** on every movie: downloading, downloaded, missing, in cinemas or announced, plus a marker for unmonitored movies
- **Search, filter and sort** the library: filter by monitored, unmonitored, missing, downloaded or downloading; sort by title, year, date added, release date, size on disk or rating, in either direction (remembered too)
- **Several Radarr servers** (a 4K one, say): switch between them with the chips at the top
- **Movie page** with the backdrop, poster, year, runtime, rating certificate, genres, IMDb, TMDB and Rotten Tomatoes scores, and the overview
- **Change settings per movie**: monitored on or off, quality profile, minimum availability, root folder (choose whether the files move too) and tags
- **File details**: quality (and whether an upgrade is still wanted), size, video and audio codecs, resolution and HDR, audio languages, subtitles, release group, edition and format score. Delete just the file if you want
- **Release dates** (cinema, digital, physical) and the movie's own history (grabbed, imported, failed, deleted, renamed)
- **Automatic search**, **refresh** and **rename** (see the new file name before applying it) from the movie page
- **Interactive search**: every release your indexers find, with quality, format score and formats, indexer, size, age, seeders and leechers, languages and the reasons Radarr would reject it. Sort by best match, seeders, size, newest or format score, hide rejected releases, and tap one to send it to your download client. Grabbing a rejected release asks first
- **Delete a movie**, optionally with its files and an import list exclusion so it doesn't come back
- **Add movies**: search by title, `imdb:` id or `tmdb:` id, then pick the quality profile, root folder (with free space), what to monitor (the movie, the movie and its collection, or nothing), minimum availability and tags, and whether to start searching straight away. Movies already in your library are marked and open their page
- **Select several movies** (long-press) to monitor or unmonitor them, change their quality profile, search for them or delete them in one go
- **Play in JellyCtrl** for downloaded movies and **Open in SeerrCtrl**, shown when those apps are installed. For now they open the app; they'll jump straight to the movie once JellyCtrl and SeerrCtrl support ArrCtrl's links
- **Posters and backdrops come from your Radarr server** (its own resized copies), not from TMDB, and use the same connection as everything else, including a trusted self-signed certificate. Movies found in Add movie have no poster until they're in Radarr

## v0.3.0 (2026-10-19)

The Activity tab now works: your Radarr and Sonarr queue, your qBittorrent torrents and your download history, all in one place.

### New

- **Queue**: everything Radarr and Sonarr are downloading, from all your servers in one list. Each item shows the movie or episode, progress, size, time left, quality and status (downloading, queued, paused, waiting to import, import blocked, failed and more). Items with a problem are listed first, in red, with the reason from Radarr or Sonarr (for example "No files found are eligible for import")
- **Queue details**: tap an item for the release name, client, indexer, protocol, path and every message about it
- **Remove from the queue**, with the same choices as the web UI: also remove it from the download client, add the release to the blocklist, and (when blocklisting) don't search for a replacement
- **Manual import** for downloads stuck on "import blocked" or "waiting to import": see the files found, what Radarr or Sonarr matched them to, and why any were rejected. Tap a file's match to choose a different movie, or a different series and episodes. Files that are matched and have no problems are selected for you; importing files with problems asks first
- **Torrents**: every torrent from qBittorrent with progress, state, size, time left, download and upload speed, ratio, seeds and peers, category and tags. Filters for downloading, seeding, stopped and errored, with counts
- **Torrent actions**: long-press a torrent (or use the menu on its page) to start or stop it, force start, recheck, change its category or tags, move it in the queue, set its own download and upload limits, or delete it with or without its files
- **Torrent page**: tap a torrent for live progress and speeds, plus tabs for info (path, downloaded and uploaded, limits, times, pieces, hash), files (tap a file to set its priority, including "don't download"), trackers with their status and counts, and connected peers
- **Speeds at the top** of the Activity tab: the total download and upload speed of your qBittorrent servers, with a button to turn the alternative speed limits on or off
- **Add torrents** from a magnet link (or several at once) or a .torrent file, with a choice of category, which qBittorrent server (when you have more than one), and whether to add them stopped
- **Open magnet links and .torrent files with ArrCtrl**, from your browser, file manager or the share sheet. ArrCtrl can be the default app for magnet links
- **History**: grabbed, imported, failed, deleted, renamed and ignored events from all Radarr and Sonarr servers, newest first, with filters and "load more". Failed events show the reason
- **Pull to refresh** on every list. The torrents update every 2 seconds and the queue every 5 while you're looking at them; nothing is checked in the background
- **One server down doesn't stop the rest**: an unreachable server shows a red card with the reason at the top, and the other servers' items still show
- Works with qBittorrent 4.x (pause and resume) and 5.x (stop and start)

### Changed

- The Activity tab shows what's there instead of a placeholder, and switches between Queue, Torrents and History when you have both Radarr or Sonarr and qBittorrent

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
