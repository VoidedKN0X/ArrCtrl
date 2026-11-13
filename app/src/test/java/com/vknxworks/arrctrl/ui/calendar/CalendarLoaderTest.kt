package com.vknxworks.arrctrl.ui.calendar

import com.vknxworks.arrctrl.FakeHttpServer
import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Collections

/**
 * The calendar against recorded Radarr and Sonarr answers, served on 127.0.0.1 by a fake server: no real
 * server is contacted. Checks the requests (path, query, API key), the merge and the date handling.
 */
class CalendarLoaderTest {
    private val seen = Collections.synchronizedList(mutableListOf<FakeHttpServer.Request>())
    private lateinit var radarrHttp: FakeHttpServer
    private lateinit var sonarrHttp: FakeHttpServer
    private lateinit var otherHttp: FakeHttpServer

    private fun serve(answer: (String) -> FakeHttpServer.Response) = FakeHttpServer { r -> seen += r; answer(r.path) }

    private fun url(s: FakeHttpServer) = s.url

    @Before
    fun start() {
        radarrHttp = serve { path -> if (path == "/api/v3/calendar") FakeHttpServer.Response(200, RADARR_CALENDAR) else FakeHttpServer.Response(404) }
        sonarrHttp = serve { path -> if (path == "/api/v3/calendar") FakeHttpServer.Response(200, SONARR_CALENDAR) else FakeHttpServer.Response(404) }
        otherHttp = serve { FakeHttpServer.Response(200, "[]") }
    }

    @After
    fun stop() {
        radarrHttp.close(); sonarrHttp.close(); otherHttp.close()
    }
    private val now = Instant.parse("2026-11-13T12:00:00Z")

    private fun loader(zone: String) = CalendarLoader(Http(), { ZoneId.of(zone) }, { now })

    @Test
    fun mergesRadarrAndSonarrInOrder() = runBlocking {
        val radarr = Server(id = "r", type = ServiceType.RADARR, name = "Radarr", url = url(radarrHttp), apiKey = "radarr-key")
        val sonarr = Server(id = "s", type = ServiceType.SONARR, name = "Sonarr", url = url(sonarrHttp), apiKey = "sonarr-key")
        val (entries, errors) = loader("Europe/Brussels").load(
            listOf(radarr, sonarr), LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 30), monitoredOnly = false,
        )
        assertTrue(errors.isEmpty())

        // Requests: the documented endpoint and query, each server's own API key.
        val r = seen.single { it.header("X-Api-Key") == "radarr-key" }
        assertEquals("/api/v3/calendar", r.path)
        assertEquals("2026-11-11T23:00:00Z", r.query["start"])
        assertEquals("2026-12-01T23:00:00Z", r.query["end"])
        assertEquals("true", r.query["unmonitored"])
        val s = seen.single { it.header("X-Api-Key") == "sonarr-key" }
        assertEquals("/api/v3/calendar", s.path)
        assertEquals("true", s.query["includeSeries"])

        // The movie's three release dates, the two episodes in range (one aired the evening before, local
        // time, is dropped), sorted by time.
        assertEquals(
            listOf(
                "Andor S02E04 2026-11-14",
                "Dune: Part Three CINEMA 2026-11-18",
                "Andor S02E05 2026-11-21",
                "Dune: Part Three DIGITAL 2026-11-25",
                "Dune: Part Three PHYSICAL 2026-11-30",
            ),
            entries.map { e ->
                listOfNotNull(e.title, e.release?.name ?: e.subtitle?.substringBefore(" ·"), e.day.toString()).joinToString(" ")
            },
        )
        val episode = entries.first()
        // 02:30 UTC on the 14th is 03:30 on the 14th in Brussels.
        assertEquals(Instant.parse("2026-11-14T02:30:00Z"), episode.at)
        assertEquals(EntryStatus.UPCOMING, episode.status)
        assertEquals(42, episode.seriesId)
        assertEquals(2, episode.season)
        assertTrue(entries.single { it.subtitle?.startsWith("S02E05") == true }.finale)
        // Unmonitored movie: shown as unmonitored on every release.
        assertTrue(entries.filter { it.kind == EntryKind.MOVIE }.all { it.status == EntryStatus.UNMONITORED && it.movieId == 7 })
    }

    @Test
    fun episodeDayFollowsThePhonesZone() = runBlocking {
        val sonarr = Server(id = "s", type = ServiceType.SONARR, name = "Sonarr", url = url(sonarrHttp), apiKey = "k")
        val (entries, _) = loader("America/New_York").load(listOf(sonarr), LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 30), monitoredOnly = true)
        // In New York, 02:30 UTC on the 14th is still the evening of the 13th.
        assertEquals(LocalDate.of(2026, 11, 13), entries.first { it.subtitle?.startsWith("S02E04") == true }.day)
        assertEquals("false", seen.single().query["unmonitored"])
    }

    @Test
    fun movieDatesDoNotMoveWithTheZone() = runBlocking {
        val radarr = Server(id = "r", type = ServiceType.RADARR, name = "Radarr", url = url(radarrHttp), apiKey = "k")
        val (entries, _) = loader("America/Los_Angeles").load(listOf(radarr), LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 30), monitoredOnly = false)
        assertEquals(LocalDate.of(2026, 11, 18), entries.single { it.release == MovieRelease.CINEMA }.day)
    }

    @Test
    fun aFailingServerDoesNotHideTheOthers() = runBlocking {
        val broken = serve { FakeHttpServer.Response(500, "{}") }
        try {
            val sonarr = Server(id = "s", type = ServiceType.SONARR, name = "Sonarr", url = url(sonarrHttp), apiKey = "k")
            val bad = Server(id = "b", type = ServiceType.RADARR, name = "Broken", url = url(broken), apiKey = "k")
            val (entries, errors) = loader("UTC").load(listOf(sonarr, bad), LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 30), monitoredOnly = true)
            assertFalse(entries.isEmpty())
            assertEquals(TestResult.HttpError(500), errors[bad])
        } finally {
            broken.close()
        }
    }

    @Test
    fun aRedirectToAnotherPortIsRefusedBeforeTheKeyIsSent() = runBlocking {
        val redirecting = serve { FakeHttpServer.Response(302, headers = mapOf("Location" to "${url(otherHttp)}/api/v3/calendar")) }
        try {
            val s = Server(id = "x", type = ServiceType.RADARR, name = "Radarr", url = url(redirecting), apiKey = "secret")
            val (entries, errors) = loader("UTC").load(listOf(s), LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 30), monitoredOnly = true)
            assertTrue(entries.isEmpty())
            assertTrue(errors.containsKey(s))
            // Only the redirecting server was asked; the other port never saw a request (or the key).
            assertEquals(1, seen.size)
        } finally {
            redirecting.close()
        }
    }

    @Test
    fun otherServiceTypesAreNotAsked() = runBlocking {
        val qbit = Server(id = "q", type = ServiceType.QBITTORRENT, name = "qBittorrent", url = url(otherHttp))
        val (entries, errors) = loader("UTC").load(listOf(qbit), LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 30), monitoredOnly = true)
        assertTrue(entries.isEmpty() && errors.isEmpty() && seen.isEmpty())
    }

    private companion object {
        /** A trimmed Radarr v5 /api/v3/calendar answer (MovieResource), as recorded. */
        val RADARR_CALENDAR = """
            [
              {
                "id": 7, "title": "Dune: Part Three", "sortTitle": "dune part three", "year": 2026,
                "status": "inCinemas", "monitored": false, "hasFile": false, "isAvailable": false,
                "minimumAvailability": "released", "qualityProfileId": 1,
                "inCinemas": "2026-11-18T00:00:00Z",
                "digitalRelease": "2026-11-25T00:00:00Z",
                "physicalRelease": "2026-11-30T00:00:00Z",
                "releaseDate": "2026-11-25T00:00:00Z",
                "tmdbId": 1170608, "tags": [], "added": "2025-01-02T10:00:00Z"
              },
              { "title": "No id, ignored" }
            ]
        """.trimIndent()

        /** A trimmed Sonarr v4 /api/v3/calendar?includeSeries=true answer (EpisodeResource). */
        val SONARR_CALENDAR = """
            [
              {
                "id": 1001, "seriesId": 42, "seasonNumber": 2, "episodeNumber": 3, "title": "Harvest",
                "airDate": "2026-11-12", "airDateUtc": "2026-11-12T22:00:00Z",
                "hasFile": true, "monitored": true,
                "series": { "id": 42, "title": "Andor", "monitored": true, "status": "continuing" }
              },
              {
                "id": 1002, "seriesId": 42, "seasonNumber": 2, "episodeNumber": 4, "title": "Ever Been to Ghorman?",
                "airDate": "2026-11-13", "airDateUtc": "2026-11-14T02:30:00Z",
                "hasFile": false, "monitored": true,
                "series": { "id": 42, "title": "Andor", "monitored": true, "status": "continuing" }
              },
              {
                "id": 1003, "seriesId": 42, "seasonNumber": 2, "episodeNumber": 5, "title": "I Have Friends Everywhere",
                "airDate": "2026-11-20", "airDateUtc": "2026-11-21T02:30:00Z", "finaleType": "season",
                "hasFile": false, "monitored": true,
                "series": { "id": 42, "title": "Andor", "monitored": true, "status": "continuing" }
              }
            ]
        """.trimIndent()
    }
}
