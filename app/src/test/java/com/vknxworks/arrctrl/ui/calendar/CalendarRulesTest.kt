package com.vknxworks.arrctrl.ui.calendar

import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class CalendarRulesTest {
    private val radarr = Server(id = "r1", type = ServiceType.RADARR, name = "Radarr", url = "http://127.0.0.1:7878")
    private val radarr4k = Server(id = "r2", type = ServiceType.RADARR, name = "Radarr 4K", url = "http://127.0.0.1:7879")
    private val sonarr = Server(id = "s1", type = ServiceType.SONARR, name = "Sonarr", url = "http://127.0.0.1:8989")
    private val qbit = Server(id = "q1", type = ServiceType.QBITTORRENT, name = "qBittorrent", url = "http://127.0.0.1:8080")

    private fun entry(
        server: Server,
        kind: EntryKind,
        at: String,
        title: String,
        subtitle: String? = null,
        day: LocalDate = Instant.parse(at).atZone(ZoneId.of("UTC")).toLocalDate(),
        id: Int = 1,
    ) = CalendarEntry(
        server = server,
        kind = kind,
        at = Instant.parse(at),
        day = day,
        title = title,
        subtitle = subtitle,
        status = EntryStatus.UPCOMING,
        movieId = if (kind == EntryKind.MOVIE) id else null,
        seriesId = if (kind == EntryKind.EPISODE) id else null,
        season = if (kind == EntryKind.EPISODE) 1 else null,
    )

    @Test
    fun requestWindowCoversADayEitherSideInThePhonesZone() {
        val zone = ZoneId.of("Europe/Brussels")
        val (start, end) = CalendarRules.requestWindow(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 20), zone)
        // Midnight on the 12th in Brussels (UTC+1 in November) is 23:00 UTC on the 11th.
        assertEquals(Instant.parse("2026-11-11T23:00:00Z"), start)
        // The day after the 21st starts at midnight on the 22nd.
        assertEquals(Instant.parse("2026-11-21T23:00:00Z"), end)
    }

    @Test
    fun requestWindowFollowsDaylightSavingChanges() {
        val zone = ZoneId.of("Europe/Brussels")
        // Clocks go back on 25 October 2026: the 24th is still UTC+2, the 27th UTC+1.
        val (start, end) = CalendarRules.requestWindow(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25), zone)
        assertEquals(Instant.parse("2026-10-23T22:00:00Z"), start)
        assertEquals(Instant.parse("2026-10-26T23:00:00Z"), end)
    }

    @Test
    fun wholeDayReleaseDatesStayOnTheirDateInEveryZone() {
        val at = Instant.parse("2026-12-18T00:00:00Z")
        assertEquals(LocalDate.of(2026, 12, 18), CalendarRules.releaseDay(at))
        // Shown in Los Angeles this would be the 17th; release days must not move.
        assertEquals(LocalDate.of(2026, 12, 17), at.atZone(ZoneId.of("America/Los_Angeles")).toLocalDate())
    }

    @Test
    fun episodesAirOnTheLocalDay() {
        val at = Instant.parse("2026-11-14T02:30:00Z")
        assertEquals(LocalDate.of(2026, 11, 13), CalendarRules.airDay(at, ZoneId.of("America/New_York")))
        assertEquals(LocalDate.of(2026, 11, 14), CalendarRules.airDay(at, ZoneId.of("Europe/Brussels")))
        assertEquals(LocalDate.of(2026, 11, 14), CalendarRules.airDay(at, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun mergeSortsByTimeThenTitleThenServer() {
        val a = entry(radarr, EntryKind.MOVIE, "2026-11-15T00:00:00Z", "Zebra", id = 1)
        val b = entry(sonarr, EntryKind.EPISODE, "2026-11-14T20:00:00Z", "Andor", "S02E01", id = 7)
        val c = entry(radarr4k, EntryKind.MOVIE, "2026-11-15T00:00:00Z", "alpha", id = 2)
        val d = entry(radarr, EntryKind.MOVIE, "2026-11-15T00:00:00Z", "Alpha", id = 3)
        val merged = CalendarRules.merge(listOf(listOf(a, c), listOf(b), listOf(d)), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(listOf("Andor", "Alpha", "alpha", "Zebra"), merged.map { it.title })
        // Same time and title: the server name decides ("Radarr" before "Radarr 4K").
        assertEquals(listOf("r1", "r2"), merged.subList(1, 3).map { it.server.id })
    }

    @Test
    fun mergeDropsDaysOutsideTheRangeAndDuplicates() {
        val inside = entry(radarr, EntryKind.MOVIE, "2026-11-13T00:00:00Z", "Inside")
        val before = entry(radarr, EntryKind.MOVIE, "2026-11-12T00:00:00Z", "Before", id = 2)
        val after = entry(radarr, EntryKind.MOVIE, "2026-11-21T00:00:00Z", "After", id = 3)
        val lastDay = entry(radarr, EntryKind.MOVIE, "2026-11-20T00:00:00Z", "Last day", id = 4)
        val merged = CalendarRules.merge(
            listOf(listOf(inside, before, after, lastDay), listOf(inside)),
            LocalDate.of(2026, 11, 13),
            LocalDate.of(2026, 11, 20),
        )
        assertEquals(listOf("Inside", "Last day"), merged.map { it.title })
    }

    @Test
    fun mergeKeepsEachReleaseOfAMovie() {
        val cinema = entry(radarr, EntryKind.MOVIE, "2026-11-13T00:00:00Z", "Film").copy(release = MovieRelease.CINEMA)
        val digital = entry(radarr, EntryKind.MOVIE, "2026-11-13T00:00:00Z", "Film").copy(release = MovieRelease.DIGITAL)
        val merged = CalendarRules.merge(listOf(listOf(cinema, digital)), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30))
        assertEquals(2, merged.size)
    }

    @Test
    fun filterByKindAndServer() {
        val m1 = entry(radarr, EntryKind.MOVIE, "2026-11-13T00:00:00Z", "M1")
        val m2 = entry(radarr4k, EntryKind.MOVIE, "2026-11-13T00:00:00Z", "M2", id = 2)
        val e1 = entry(sonarr, EntryKind.EPISODE, "2026-11-13T20:00:00Z", "E1")
        val all = listOf(m1, m2, e1)
        assertEquals(all, CalendarRules.filter(all, KindFilter.ALL, emptySet()))
        assertEquals(listOf(m1, m2), CalendarRules.filter(all, KindFilter.MOVIES, emptySet()))
        assertEquals(listOf(e1), CalendarRules.filter(all, KindFilter.SERIES, emptySet()))
        assertEquals(listOf(m1, e1), CalendarRules.filter(all, KindFilter.ALL, setOf("r2")))
        assertEquals(listOf(m1), CalendarRules.filter(all, KindFilter.MOVIES, setOf("r2")))
        assertTrue(CalendarRules.filter(all, KindFilter.MUSIC, emptySet()).isEmpty())
    }

    @Test
    fun visibleServersAreMediaServersNotHidden() {
        val servers = listOf(radarr, radarr4k, sonarr, qbit)
        assertEquals(listOf(radarr, radarr4k, sonarr), CalendarRules.visibleServers(servers, emptySet()))
        assertEquals(listOf(radarr, sonarr), CalendarRules.visibleServers(servers, setOf("r2", "q1")))
    }

    @Test
    fun monthGridStartsOnTheChosenWeekday() {
        // 1 November 2026 is a Sunday.
        val (mondayStart, mondayEnd) = CalendarRules.monthGrid(YearMonth.of(2026, 11), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 26), mondayStart)
        assertEquals(LocalDate.of(2026, 12, 6), mondayEnd)
        val (sundayStart, _) = CalendarRules.monthGrid(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), sundayStart)
        // February 2027 starts on a Monday: no spill-over days before it.
        assertEquals(LocalDate.of(2027, 2, 1), CalendarRules.monthGrid(YearMonth.of(2027, 2), DayOfWeek.MONDAY).first)
    }

    @Test
    fun byDayGroupsInDateOrder() {
        val late = entry(radarr, EntryKind.MOVIE, "2026-11-20T00:00:00Z", "Late")
        val early = entry(radarr, EntryKind.MOVIE, "2026-11-14T00:00:00Z", "Early", id = 2)
        val grouped = CalendarRules.byDay(listOf(late, early))
        assertEquals(listOf(LocalDate.of(2026, 11, 14), LocalDate.of(2026, 11, 20)), grouped.keys.toList())
    }
}
