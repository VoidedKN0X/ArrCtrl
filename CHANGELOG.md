# Changelog

What changed in each ArrCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/ArrCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

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
