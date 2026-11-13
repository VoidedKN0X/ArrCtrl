package com.vknxworks.arrctrl.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vknxworks.arrctrl.AppContainer
import com.vknxworks.arrctrl.data.api.Album
import com.vknxworks.arrctrl.data.api.Episode
import com.vknxworks.arrctrl.data.api.LidarrApi
import com.vknxworks.arrctrl.data.api.Movie
import com.vknxworks.arrctrl.data.api.RadarrApi
import com.vknxworks.arrctrl.data.api.Series
import com.vknxworks.arrctrl.data.api.SonarrApi
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

enum class EntryKind { MOVIE, EPISODE, ALBUM }

/** Which release of a movie lands on this day. */
enum class MovieRelease { CINEMA, DIGITAL, PHYSICAL }

/** How an entry is coloured: already have it, should have it by now, still to come, or not wanted. */
enum class EntryStatus { DOWNLOADED, MISSING, UPCOMING, UNMONITORED }

/** The Calendar tab's type filter. */
enum class KindFilter { ALL, MOVIES, SERIES, MUSIC }

data class CalendarEntry(
    val server: Server,
    val kind: EntryKind,
    val at: Instant,
    val day: LocalDate,
    val title: String,
    /** "S01E02 · Title" for episodes. */
    val subtitle: String?,
    val status: EntryStatus,
    val release: MovieRelease? = null,
    val movieId: Int? = null,
    val seriesId: Int? = null,
    val season: Int? = null,
    val finale: Boolean = false,
    val premiere: Boolean = false,
    /** Lidarr: the album's artist. */
    val artistId: Int? = null,
) {
    val key: String get() = "${server.id}:$kind:${movieId ?: seriesId ?: artistId}:${subtitle.orEmpty()}:${release ?: ""}:$day"
}

/**
 * The calendar's date handling and merge rules, kept free of Android so they can be unit-tested.
 *
 * - Episodes air at a moment in time (Sonarr's `airDateUtc`): they're shown at that time, on that day, in
 *   the phone's time zone.
 * - Movie release dates (Radarr's `inCinemas`, `digitalRelease`, `physicalRelease`) and album release dates
 *   are whole days that the servers store as midnight UTC, so they're shown on that calendar date in every
 *   time zone (otherwise anyone west of UTC would see them a day early).
 */
object CalendarRules {
    /** Ask the servers for a day either side, so time zones never drop an entry at the edges. */
    fun requestWindow(from: LocalDate, to: LocalDate, zone: ZoneId): Pair<Instant, Instant> =
        from.minusDays(1).atStartOfDay(zone).toInstant() to to.plusDays(2).atStartOfDay(zone).toInstant()

    /** The day a whole-day release date (stored at midnight UTC) belongs to. */
    fun releaseDay(at: Instant): LocalDate = at.atZone(ZoneOffset.UTC).toLocalDate()

    /** The day an episode airs on, in the phone's time zone. */
    fun airDay(at: Instant, zone: ZoneId): LocalDate = at.atZone(zone).toLocalDate()

    fun movieEntries(s: Server, movies: List<Movie>, now: Instant): List<CalendarEntry> = movies.flatMap { m ->
        listOfNotNull(
            m.inCinemas?.let { it to MovieRelease.CINEMA },
            m.digitalRelease?.let { it to MovieRelease.DIGITAL },
            m.physicalRelease?.let { it to MovieRelease.PHYSICAL },
        ).map { (at, kind) ->
            CalendarEntry(
                server = s,
                kind = EntryKind.MOVIE,
                at = at,
                day = releaseDay(at),
                title = m.title,
                subtitle = m.year?.toString(),
                status = when {
                    m.hasFile -> EntryStatus.DOWNLOADED
                    !m.monitored -> EntryStatus.UNMONITORED
                    // Only a home release makes a movie downloadable; a cinema date in the past isn't "missing".
                    kind != MovieRelease.CINEMA && at.isBefore(now) -> EntryStatus.MISSING
                    else -> EntryStatus.UPCOMING
                },
                release = kind,
                movieId = m.id,
            )
        }
    }

    fun albumEntries(s: Server, albums: List<Album>, now: Instant): List<CalendarEntry> = albums.mapNotNull { a ->
        val at = a.releaseDate ?: return@mapNotNull null
        CalendarEntry(
            server = s,
            kind = EntryKind.ALBUM,
            at = at,
            day = releaseDay(at),
            title = a.artist?.name ?: a.title,
            subtitle = listOfNotNull(a.title.takeIf { a.artist != null }, a.albumType).joinToString(" · ").ifBlank { null },
            status = when {
                a.complete -> EntryStatus.DOWNLOADED
                !a.monitored || a.artist?.monitored == false -> EntryStatus.UNMONITORED
                at.isBefore(now) -> EntryStatus.MISSING
                else -> EntryStatus.UPCOMING
            },
            artistId = a.artistId,
        )
    }

    fun episodeEntries(s: Server, episodes: List<Pair<Episode, Series?>>, now: Instant, zone: ZoneId): List<CalendarEntry> =
        episodes.mapNotNull { (e, series) ->
            val at = e.airDate ?: return@mapNotNull null
            CalendarEntry(
                server = s,
                kind = EntryKind.EPISODE,
                at = at,
                day = airDay(at, zone),
                title = series?.title ?: "#${e.seriesId}",
                subtitle = listOfNotNull(e.code.ifBlank { null }, e.title).joinToString(" · ").ifBlank { null },
                status = when {
                    e.hasFile -> EntryStatus.DOWNLOADED
                    !e.monitored || series?.monitored == false -> EntryStatus.UNMONITORED
                    at.isBefore(now) -> EntryStatus.MISSING
                    else -> EntryStatus.UPCOMING
                },
                seriesId = e.seriesId,
                season = e.season,
                finale = e.finaleType != null,
                premiere = e.number == 1,
            )
        }

    /**
     * Merges every server's entries into one list: only days in [from]..[to], one copy of each entry (the same
     * server listed twice, or an overlapping answer, doesn't show it twice), sorted by time, then title, then
     * server name, so the order is stable between refreshes.
     */
    fun merge(perServer: List<List<CalendarEntry>>, from: LocalDate, to: LocalDate): List<CalendarEntry> =
        perServer.asSequence().flatten()
            .filter { !it.day.isBefore(from) && !it.day.isAfter(to) }
            .distinctBy { it.key }
            .sortedWith(
                compareBy<CalendarEntry> { it.at }
                    .thenBy { it.title.lowercase() }
                    .thenBy { it.subtitle.orEmpty() }
                    .thenBy { it.server.name.lowercase() },
            )
            .toList()

    /** The type filter and the per-server filter together. */
    fun filter(entries: List<CalendarEntry>, kind: KindFilter, hiddenServers: Set<String>): List<CalendarEntry> =
        entries.filter { e ->
            e.server.id !in hiddenServers && when (kind) {
                KindFilter.ALL -> true
                KindFilter.MOVIES -> e.kind == EntryKind.MOVIE
                KindFilter.SERIES -> e.kind == EntryKind.EPISODE
                KindFilter.MUSIC -> e.kind == EntryKind.ALBUM
            }
        }

    /** Which servers the calendar asks: Radarr, Sonarr and Lidarr ones the filter doesn't hide. */
    fun visibleServers(servers: List<Server>, hiddenServers: Set<String>): List<Server> =
        servers.filter { it.type.isMediaArr && it.id !in hiddenServers }

    /** First and last day of the six-week month grid for [ym], starting on [firstDayOfWeek]. */
    fun monthGrid(ym: YearMonth, firstDayOfWeek: DayOfWeek): Pair<LocalDate, LocalDate> {
        val first = ym.atDay(1)
        val start = first.minusDays(((first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7).toLong())
        return start to start.plusDays(41)
    }

    /** Entries per day, each day's list in the merged order. */
    fun byDay(entries: List<CalendarEntry>): Map<LocalDate, List<CalendarEntry>> = entries.groupBy { it.day }.toSortedMap()
}

/**
 * Fetches calendar entries from Radarr, Sonarr and Lidarr servers. Shared by the Calendar tab and the Upcoming
 * widget. A server that fails is returned in the error map; the others still count.
 */
class CalendarLoader(
    private val http: Http,
    private val zoneOf: () -> ZoneId = { ZoneId.systemDefault() },
    private val clock: () -> Instant = { Instant.now() },
) {
    constructor(c: AppContainer) : this(c.http)

    /** Entries on [from]..[to] (inclusive), merged and sorted, plus the servers that failed. */
    suspend fun load(servers: List<Server>, from: LocalDate, to: LocalDate, monitoredOnly: Boolean): Pair<List<CalendarEntry>, Map<Server, TestResult>> = coroutineScope {
        val zone = zoneOf()
        val now = clock()
        val (startAt, endAt) = CalendarRules.requestWindow(from, to, zone)
        val start = startAt.toString()
        val end = endAt.toString()
        val unmonitored = !monitoredOnly
        val jobs = servers.filter { it.type.isMediaArr }.map { s ->
            async {
                s to runCatching {
                    when (s.type) {
                        ServiceType.RADARR -> CalendarRules.movieEntries(s, RadarrApi(http, s).calendar(start, end, unmonitored), now)
                        ServiceType.LIDARR -> CalendarRules.albumEntries(s, LidarrApi(http, s).calendar(start, end, unmonitored), now)
                        else -> CalendarRules.episodeEntries(s, SonarrApi(http, s).calendar(start, end, unmonitored), now, zone)
                    }
                }
            }
        }
        val results = jobs.awaitAll()
        results.forEach { (_, r) -> r.exceptionOrNull()?.let { if (it is CancellationException) throw it } }
        val entries = CalendarRules.merge(results.map { (_, r) -> r.getOrNull().orEmpty() }, from, to)
        entries to results.mapNotNull { (s, r) -> r.exceptionOrNull()?.let { s to it.toTestResult() } }.toMap()
    }
}

/**
 * Upcoming movies and episodes from every Radarr and Sonarr server, for any date range.
 * A server that fails only adds an error card; the others still show.
 */
class CalendarModel(private val c: AppContainer) : ViewModel() {
    private val loader = CalendarLoader(c)

    var entries by mutableStateOf<List<CalendarEntry>>(emptyList()); private set
    var errors by mutableStateOf<Map<Server, TestResult>>(emptyMap()); private set
    var loading by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    private var loadedRange: Triple<LocalDate, LocalDate, Boolean>? = null
    private var loadedServers: List<Server>? = null

    /** Loads [from]..[to] (inclusive) from [servers]. Skipped when that exact range is already on screen, unless [force]. */
    suspend fun load(servers: List<Server>, from: LocalDate, to: LocalDate, monitoredOnly: Boolean, force: Boolean = false) {
        val range = Triple(from, to, monitoredOnly)
        if (!force && range == loadedRange && servers == loadedServers && loaded) return
        loading = true
        try {
            val (e, err) = loader.load(servers, from, to, monitoredOnly)
            entries = e
            errors = err
            loadedRange = range
            loadedServers = servers
            loaded = true
        } finally {
            loading = false
        }
    }

    companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
