package com.vknxworks.arrctrl.ui.calendar

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vknxworks.arrctrl.AppContainer
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException

enum class EntryKind { MOVIE, EPISODE }

/** Which release of a movie lands on this day. */
enum class MovieRelease { CINEMA, DIGITAL, PHYSICAL }

/** How an entry is coloured: already have it, should have it by now, still to come, or not wanted. */
enum class EntryStatus { DOWNLOADED, MISSING, UPCOMING, UNMONITORED }

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
) {
    val key: String get() = "${server.id}:$kind:${movieId ?: seriesId}:${subtitle.orEmpty()}:${release ?: ""}:$day"
}

/**
 * Upcoming movies and episodes from every Radarr and Sonarr server, for any date range.
 * A server that fails only adds an error card; the others still show.
 */
class CalendarModel(private val c: AppContainer) : ViewModel() {
    private val zone: ZoneId get() = ZoneId.systemDefault()

    var entries by mutableStateOf<List<CalendarEntry>>(emptyList()); private set
    var errors by mutableStateOf<Map<Server, TestResult>>(emptyMap()); private set
    var loading by mutableStateOf(false); private set
    var loaded by mutableStateOf(false); private set
    private var loadedRange: Triple<LocalDate, LocalDate, Boolean>? = null

    /** Loads [from]..[to] (inclusive). Skipped when that exact range is already on screen, unless [force]. */
    suspend fun load(from: LocalDate, to: LocalDate, monitoredOnly: Boolean, force: Boolean = false) = coroutineScope {
        val range = Triple(from, to, monitoredOnly)
        if (!force && range == loadedRange && loaded) return@coroutineScope
        loading = true
        try {
            // Ask for a day either side so time zones don't drop anything at the edges.
            val start = from.minusDays(1).atStartOfDay(zone).toInstant().toString()
            val end = to.plusDays(2).atStartOfDay(zone).toInstant().toString()
            val unmonitored = !monitoredOnly
            val servers = c.servers.servers.value
            val jobs = servers.filter { it.type == ServiceType.RADARR || it.type == ServiceType.SONARR }.map { s ->
                async {
                    s to runCatching {
                        if (s.type == ServiceType.RADARR) movies(s, start, end, unmonitored) else episodes(s, start, end, unmonitored)
                    }
                }
            }
            val results = jobs.awaitAll()
            results.forEach { (_, r) -> r.exceptionOrNull()?.let { if (it is CancellationException) throw it } }
            entries = results.flatMap { (_, r) -> r.getOrNull().orEmpty() }
                .filter { !it.day.isBefore(from) && !it.day.isAfter(to) }
                .sortedWith(compareBy<CalendarEntry> { it.at }.thenBy { it.title.lowercase() })
            errors = results.mapNotNull { (s, r) -> r.exceptionOrNull()?.let { s to it.toTestResult() } }.toMap()
            loadedRange = range
            loaded = true
        } finally {
            loading = false
        }
    }

    private suspend fun movies(s: Server, start: String, end: String, unmonitored: Boolean): List<CalendarEntry> {
        val now = Instant.now()
        return c.radarr(s).calendar(start, end, unmonitored).flatMap { m ->
            listOfNotNull(
                m.inCinemas?.let { it to MovieRelease.CINEMA },
                m.digitalRelease?.let { it to MovieRelease.DIGITAL },
                m.physicalRelease?.let { it to MovieRelease.PHYSICAL },
            ).map { (at, kind) ->
                CalendarEntry(
                    server = s,
                    kind = EntryKind.MOVIE,
                    at = at,
                    // Release dates are whole days (stored at midnight UTC): show them on that date everywhere.
                    day = at.atZone(ZoneId.of("UTC")).toLocalDate(),
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
    }

    private suspend fun episodes(s: Server, start: String, end: String, unmonitored: Boolean): List<CalendarEntry> {
        val now = Instant.now()
        return c.sonarr(s).calendar(start, end, unmonitored).mapNotNull { (e, series) ->
            val at = e.airDate ?: return@mapNotNull null
            CalendarEntry(
                server = s,
                kind = EntryKind.EPISODE,
                at = at,
                day = at.atZone(zone).toLocalDate(),
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
    }

    companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
