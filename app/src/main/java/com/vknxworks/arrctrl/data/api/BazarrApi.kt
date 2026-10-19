package com.vknxworks.arrctrl.data.api

import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.server.Server
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class SubLanguage(val name: String, val code: String?, val hi: Boolean, val forced: Boolean) {
    val label: String get() = (code?.uppercase() ?: name) + (if (hi) " HI" else "") + (if (forced) " ⚑" else "")
}

/** Something Bazarr still wants subtitles for: a movie ([radarrId]) or an episode ([episodeId]). */
data class WantedItem(
    val title: String,
    /** "1x05 · Title" for episodes. */
    val detail: String?,
    val missing: List<SubLanguage>,
    val radarrId: Int? = null,
    val seriesId: Int? = null,
    val episodeId: Int? = null,
) {
    val key: String get() = radarrId?.let { "m$it" } ?: "e$episodeId"
}

data class SubtitleResult(
    val provider: String,
    val subtitle: String,
    val language: String?,
    val hi: String,
    val forced: String,
    val originalFormat: String,
    val score: Int,
    val releaseInfo: List<String>,
    val matches: List<String>,
    val dontMatches: List<String>,
    val uploader: String?,
)

data class SubHistory(val title: String, val detail: String?, val description: String?, val time: String?, val provider: String?, val language: String?, val score: String?)

/** An enabled subtitle provider. Bazarr reports "Good" for working ones, otherwise the reason it's throttled. */
data class SubProvider(val name: String, val status: String, val retry: String?) {
    val working: Boolean get() = status.isBlank() || status.equals("Good", ignoreCase = true)
}

/** A subtitle Bazarr knows about for a file: external ([path] set) or embedded in the video. */
data class BazarrSub(val name: String, val code: String, val path: String?, val hi: Boolean, val forced: Boolean, val size: Long?, val embedded: Boolean) {
    val fileName: String? get() = path?.substringAfterLast('/')?.substringAfterLast('\\')
}

/** A movie, series or episode as Bazarr sees it. */
data class BazarrMedia(
    val title: String,
    val profileId: Int?,
    val subtitles: List<BazarrSub>,
    val missing: List<SubLanguage>,
    val imdbId: String?,
    val tvdbId: Int?,
)

/** A language Bazarr can search for; [enabled] ones are those turned on in Bazarr's language settings. */
data class BazarrLanguage(val name: String, val code: String, val enabled: Boolean)

/** One language in a languages profile. */
data class ProfileLanguage(val code: String, val hi: Boolean, val forced: Boolean)

/** A Bazarr languages profile: which subtitle languages to download for a movie or series. */
data class LanguagesProfile(val id: Int, val name: String, val languages: List<ProfileLanguage>)

/** Bazarr's own health issues ("object" and "issue"). */
data class BazarrIssue(val obj: String, val issue: String)

/** Bazarr API. Paths are under /api; data comes wrapped as { "data": … }. */
class BazarrApi(private val http: Http, val server: Server) {
    private val client get() = http.client(server)
    private fun url(path: String) = server.api(path)

    private fun data(e: JsonElement?): List<JsonObject> = ((e as? JsonObject)?.get("data") ?: e).objects()
    private fun total(e: JsonElement?): Int? = (e as? JsonObject).int("total")

    private fun langs(o: JsonObject, key: String) = o.arr(key).objects().map { l ->
        SubLanguage(l.str("name").orEmpty(), l.str("code2"), l.bool("hi") ?: false, l.bool("forced") ?: false)
    }

    suspend fun wantedMovies(start: Int, length: Int): Pair<List<WantedItem>, Int> {
        val body = Api.json(client, Api.get(url("/movies/wanted?start=$start&length=$length")))
        val items = data(body).map { o ->
            WantedItem(o.str("title").orEmpty(), null, langs(o, "missing_subtitles"), radarrId = o.int("radarrId"))
        }
        return items to (total(body) ?: items.size)
    }

    suspend fun wantedEpisodes(start: Int, length: Int): Pair<List<WantedItem>, Int> {
        val body = Api.json(client, Api.get(url("/episodes/wanted?start=$start&length=$length")))
        val items = data(body).map { o ->
            WantedItem(
                title = o.str("seriesTitle").orEmpty(),
                detail = listOfNotNull(o.str("episode_number"), o.str("episodeTitle")).joinToString(" · ").ifBlank { null },
                missing = langs(o, "missing_subtitles"),
                seriesId = o.int("sonarrSeriesId"),
                episodeId = o.int("sonarrEpisodeId"),
            )
        }
        return items to (total(body) ?: items.size)
    }

    private suspend fun patch(path: String) =
        Api.send(client, Request.Builder().url(url(path)).patch(ByteArray(0).toRequestBody(null)).build())

    /** Automatic search for one movie's missing subtitles. */
    suspend fun searchMovie(radarrId: Int) = patch("/movies?radarrid=$radarrId&action=search-missing")

    /** Bazarr searches a series at a time (all its episodes' missing subtitles). */
    suspend fun searchSeries(seriesId: Int) = patch("/series?seriesid=$seriesId&action=search-missing")

    suspend fun searchAllMovies() = patch("/movies?action=search-wanted")
    suspend fun searchAllEpisodes() = patch("/series?action=search-wanted")

    private val slow get() = client.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()

    private fun results(e: JsonElement?): List<SubtitleResult> = data(e).mapNotNull { o ->
        SubtitleResult(
            provider = o.str("provider") ?: return@mapNotNull null,
            subtitle = o.str("subtitle") ?: return@mapNotNull null,
            language = o.str("language"),
            hi = o.str("hearing_impaired") ?: "False",
            forced = o.str("forced") ?: "False",
            originalFormat = o.str("original_format") ?: "False",
            score = o.int("score") ?: 0,
            releaseInfo = o.arr("release_info").strings(),
            matches = o.arr("matches").strings(),
            dontMatches = o.arr("dont_matches").strings(),
            uploader = o.str("uploader"),
        )
    }.sortedByDescending { it.score }

    /** Manual search: asks every provider; can take a while. */
    suspend fun movieSubtitles(radarrId: Int): List<SubtitleResult> = results(Api.json(slow, Api.get(url("/providers/movies?radarrid=$radarrId"))))

    suspend fun episodeSubtitles(episodeId: Int): List<SubtitleResult> = results(Api.json(slow, Api.get(url("/providers/episodes?episodeid=$episodeId"))))

    private fun form(r: SubtitleResult) = FormBody.Builder()
        .add("hi", r.hi).add("forced", r.forced).add("original_format", r.originalFormat)
        .add("provider", r.provider).add("subtitle", r.subtitle)

    suspend fun downloadMovieSubtitle(radarrId: Int, r: SubtitleResult) =
        Api.send(slow, Api.post(url("/providers/movies"), form(r).add("radarrid", radarrId.toString()).build()))

    suspend fun downloadEpisodeSubtitle(seriesId: Int, episodeId: Int, r: SubtitleResult) =
        Api.send(slow, Api.post(url("/providers/episodes"), form(r).add("seriesid", seriesId.toString()).add("episodeid", episodeId.toString()).build()))

    suspend fun movieHistory(length: Int = 50): List<SubHistory> = data(Api.json(client, Api.get(url("/movies/history?start=0&length=$length")))).map { o ->
        SubHistory(o.str("title").orEmpty(), null, o.str("description"), o.str("timestamp"), o.str("provider"), o.obj("language").str("name"), o.str("score"))
    }

    suspend fun episodeHistory(length: Int = 50): List<SubHistory> = data(Api.json(client, Api.get(url("/episodes/history?start=0&length=$length")))).map { o ->
        SubHistory(
            o.str("seriesTitle").orEmpty(),
            listOfNotNull(o.str("episode_number"), o.str("episodeTitle")).joinToString(" · ").ifBlank { null },
            o.str("description"), o.str("timestamp"), o.str("provider"), o.obj("language").str("name"), o.str("score"),
        )
    }

    /** Every enabled provider; "retry" is "-" when there's nothing to wait for. */
    suspend fun providers(): List<SubProvider> = data(Api.json(client, Api.get(url("/providers")))).map { o ->
        SubProvider(o.str("name").orEmpty(), o.str("status").orEmpty(), o.str("retry")?.takeIf { it != "-" })
    }

    /** Clears every throttled provider so Bazarr uses them again straight away. */
    suspend fun resetProviders() = Api.send(client, Api.post(url("/providers"), FormBody.Builder().add("action", "reset").build()))

    private fun yes(o: JsonObject, key: String) = o.bool(key) ?: o.str(key).equals("True", ignoreCase = true)

    /** GET /system/languages/profiles returns a bare list. */
    suspend fun profiles(): List<LanguagesProfile> = data(Api.json(client, Api.get(url("/system/languages/profiles")))).mapNotNull { o ->
        LanguagesProfile(
            id = o.int("profileId") ?: return@mapNotNull null,
            name = o.str("name").orEmpty(),
            languages = o.arr("items").objects().mapNotNull { i ->
                ProfileLanguage(i.str("language") ?: return@mapNotNull null, yes(i, "hi"), yes(i, "forced"))
            },
        )
    }

    /** Every language Bazarr knows (GET /system/languages returns a bare list). */
    suspend fun languages(): List<BazarrLanguage> = data(Api.json(client, Api.get(url("/system/languages")))).mapNotNull { o ->
        BazarrLanguage(o.str("name") ?: return@mapNotNull null, o.str("code2") ?: return@mapNotNull null, o.bool("enabled") ?: false)
    }

    /** Language names by two-letter code, for showing what's in a profile. */
    suspend fun languageNames(): Map<String, String> = languages().associate { it.code to it.name }

    private fun subs(o: JsonObject) = o.arr("subtitles").objects().mapNotNull { x ->
        val path = x.str("path")
        BazarrSub(
            name = x.str("name") ?: x.str("code2") ?: return@mapNotNull null,
            code = x.str("code2") ?: return@mapNotNull null,
            path = path,
            hi = x.bool("hi") ?: false,
            forced = x.bool("forced") ?: false,
            size = x.long("file_size")?.takeIf { it > 0 },
            embedded = path == null || x.int("embedded_track_id") != null,
        )
    }

    private fun media(o: JsonObject?, title: String?): BazarrMedia? = o?.let {
        BazarrMedia(title ?: it.str("title").orEmpty(), it.int("profileId"), subs(it), langs(it, "missing_subtitles"), it.str("imdbId"), it.int("tvdbId"))
    }

    /** The movie with this Radarr id, or null when this Bazarr doesn't have it. */
    suspend fun movie(radarrId: Int): BazarrMedia? =
        media(data(Api.json(client, Api.get(url("/movies?radarrid%5B%5D=$radarrId")))).firstOrNull(), null)

    /** The series with this Sonarr id (no subtitles at series level). */
    suspend fun series(seriesId: Int): BazarrMedia? =
        media(data(Api.json(client, Api.get(url("/series?seriesid%5B%5D=$seriesId")))).firstOrNull(), null)

    /** The episode with this Sonarr id (GET /episodes returns a bare list). */
    suspend fun episode(episodeId: Int): BazarrMedia? =
        media(data(Api.json(client, Api.get(url("/episodes?episodeid%5B%5D=$episodeId")))).firstOrNull(), null)

    /** The movie's languages profile id, or null when it has none. */
    suspend fun movieProfile(radarrId: Int): Int? = movie(radarrId)?.profileId

    suspend fun seriesProfile(seriesId: Int): Int? = series(seriesId)?.profileId

    private fun query(path: String, vararg params: Pair<String, String>): String =
        url(path).toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build().toString()

    private fun flag(b: Boolean) = if (b) "True" else "False"

    /**
     * Searches the providers for one language, whether or not it's in the profile (PATCH /movies/subtitles).
     * Bazarr queues it as a job and answers straight away.
     */
    suspend fun searchMovieLanguage(radarrId: Int, code: String, hi: Boolean, forced: Boolean) = Api.send(
        client,
        Request.Builder().url(query("/movies/subtitles", "radarrid" to radarrId.toString(), "language" to code, "hi" to flag(hi), "forced" to flag(forced)))
            .patch(ByteArray(0).toRequestBody(null)).build(),
    )

    suspend fun searchEpisodeLanguage(seriesId: Int, episodeId: Int, code: String, hi: Boolean, forced: Boolean) = Api.send(
        client,
        Request.Builder().url(
            query("/episodes/subtitles", "seriesid" to seriesId.toString(), "episodeid" to episodeId.toString(), "language" to code, "hi" to flag(hi), "forced" to flag(forced)),
        ).patch(ByteArray(0).toRequestBody(null)).build(),
    )

    /** Deletes an external subtitle file. Embedded subtitles can't be deleted. */
    suspend fun deleteMovieSubtitle(radarrId: Int, sub: BazarrSub) = Api.send(
        client,
        Request.Builder().url(
            query("/movies/subtitles", "radarrid" to radarrId.toString(), "language" to sub.code, "hi" to flag(sub.hi), "forced" to flag(sub.forced), "path" to sub.path.orEmpty()),
        ).delete().build(),
    )

    suspend fun deleteEpisodeSubtitle(seriesId: Int, episodeId: Int, sub: BazarrSub) = Api.send(
        client,
        Request.Builder().url(
            query(
                "/episodes/subtitles", "seriesid" to seriesId.toString(), "episodeid" to episodeId.toString(),
                "language" to sub.code, "hi" to flag(sub.hi), "forced" to flag(sub.forced), "path" to sub.path.orEmpty(),
            ),
        ).delete().build(),
    )

    /** Sets the languages profile; null removes it ("" is Bazarr's "no profile"). Bazarr then recomputes what's missing. */
    suspend fun setMovieProfile(radarrId: Int, profileId: Int?) = Api.send(
        client,
        Api.post(url("/movies"), FormBody.Builder().add("radarrid", radarrId.toString()).add("profileid", profileId?.toString() ?: "").build()),
    )

    suspend fun setSeriesProfile(seriesId: Int, profileId: Int?) = Api.send(
        client,
        Api.post(url("/series"), FormBody.Builder().add("seriesid", seriesId.toString()).add("profileid", profileId?.toString() ?: "").build()),
    )

    suspend fun health(): List<BazarrIssue> = data(Api.json(client, Api.get(url("/system/health")))).map { o ->
        BazarrIssue(o.str("object").orEmpty(), o.str("issue").orEmpty())
    }
}
