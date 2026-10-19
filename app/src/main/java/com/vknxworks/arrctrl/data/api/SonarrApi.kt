package com.vknxworks.arrctrl.data.api

import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.server.Server
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

// ---------- Models ----------

data class Season(
    val number: Int,
    val monitored: Boolean,
    val episodeFileCount: Int,
    /** Aired (or monitored-and-aired) episodes Sonarr counts towards progress. */
    val episodeCount: Int,
    val totalEpisodeCount: Int,
    val sizeOnDisk: Long,
    val nextAiring: Instant?,
)

data class Series(
    val server: Server,
    /** The full resource as Sonarr sent it, so updates send back every field unchanged. */
    val raw: JsonObject,
    val id: Int,
    val title: String,
    val sortTitle: String,
    val year: Int?,
    val overview: String?,
    val monitored: Boolean,
    /** continuing, ended, upcoming, deleted */
    val status: String,
    val ended: Boolean,
    val network: String?,
    val seriesType: String,
    val seasonFolder: Boolean,
    val qualityProfileId: Int,
    val rootFolderPath: String?,
    val path: String?,
    val tags: List<Int>,
    val runtime: Int?,
    val certification: String?,
    val genres: List<String>,
    val added: Instant?,
    val nextAiring: Instant?,
    val previousAiring: Instant?,
    val tvdbId: Int?,
    val tmdbId: Int?,
    val imdbId: String?,
    val rating: Double?,
    val seasons: List<Season>,
    val episodeFileCount: Int,
    val episodeCount: Int,
    val totalEpisodeCount: Int,
    val sizeOnDisk: Long,
) {
    val key: String get() = "${server.id}:$id"
    /** "18/24": downloaded vs. aired episodes Sonarr expects. */
    val progress: Float get() = if (episodeCount <= 0) 0f else (episodeFileCount.toFloat() / episodeCount).coerceIn(0f, 1f)
    val complete: Boolean get() = episodeCount > 0 && episodeFileCount >= episodeCount
}

data class Episode(
    val raw: JsonObject,
    val id: Int,
    val seriesId: Int,
    val season: Int,
    val number: Int,
    val absoluteNumber: Int?,
    val title: String?,
    val overview: String?,
    val airDate: Instant?,
    val monitored: Boolean,
    val hasFile: Boolean,
    val fileId: Int?,
    val finaleType: String?,
    val runtime: Int?,
    val tvdbId: Int?,
) {
    val code: String get() = Format.episode(season, number).orEmpty()
    val aired: Boolean get() = airDate?.isBefore(Instant.now()) == true
}

data class EpisodeFileInfo(
    val id: Int,
    val relativePath: String?,
    val size: Long,
    val quality: String?,
    val releaseGroup: String?,
    val videoCodec: String?,
    val videoDynamicRange: String?,
    val resolution: String?,
    val audioCodec: String?,
    val audioChannels: Double?,
    val audioLanguages: String?,
    val subtitles: String?,
    val runTime: String?,
    val dateAdded: Instant?,
    val customFormatScore: Int?,
    val cutoffNotMet: Boolean,
)

data class EpisodeRename(val episodeFileId: Int, val label: String, val existingPath: String, val newPath: String)

data class LookupSeries(
    val raw: JsonObject,
    val title: String,
    val year: Int?,
    val overview: String?,
    val network: String?,
    val seasonCount: Int,
    val status: String?,
    val tvdbId: Int?,
    val inLibraryId: Int?,
)

// ---------- Client ----------

/** Sonarr v3 API (Sonarr v4 still serves /api/v3) for the Series tab. */
class SonarrApi(private val http: Http, val server: Server) {
    private val client get() = http.client(server)
    private fun url(path: String) = server.api(path)

    suspend fun allSeries(): List<Series> = Api.json(client, Api.get(url("/series"))).objects().mapNotNull(::series)

    suspend fun series(id: Int): Series? = (Api.json(client, Api.get(url("/series/$id"))) as? JsonObject)?.let(::series)

    /** Series ids with a download in Sonarr's queue, for the "downloading" badge; and the episode ids. */
    suspend fun queued(): Pair<Set<Int>, Set<Int>> {
        val body = Api.json(client, Api.get(url("/queue?page=1&pageSize=500&includeUnknownSeriesItems=false"))) as? JsonObject
        val records = body.arr("records").objects()
        return records.mapNotNull { it.int("seriesId") }.toSet() to records.mapNotNull { it.int("episodeId") }.toSet()
    }

    private fun series(o: JsonObject): Series? {
        val id = o.int("id") ?: return null
        val stats = o.obj("statistics")
        return Series(
            server = server,
            raw = o,
            id = id,
            title = o.str("title") ?: "#$id",
            sortTitle = o.str("sortTitle") ?: o.str("title").orEmpty().lowercase(),
            year = o.int("year")?.takeIf { it > 0 },
            overview = o.str("overview"),
            monitored = o.bool("monitored") ?: false,
            status = o.str("status").orEmpty(),
            ended = o.bool("ended") ?: (o.str("status") == "ended"),
            network = o.str("network"),
            seriesType = o.str("seriesType") ?: "standard",
            seasonFolder = o.bool("seasonFolder") ?: true,
            qualityProfileId = o.int("qualityProfileId") ?: 0,
            rootFolderPath = o.str("rootFolderPath"),
            path = o.str("path"),
            tags = o.arr("tags")?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.orEmpty(),
            runtime = o.int("runtime")?.takeIf { it > 0 },
            certification = o.str("certification"),
            genres = o.arr("genres").strings(),
            added = Format.instant(o.str("added")),
            nextAiring = Format.instant(o.str("nextAiring")),
            previousAiring = Format.instant(o.str("previousAiring")),
            tvdbId = o.int("tvdbId")?.takeIf { it > 0 },
            tmdbId = o.int("tmdbId")?.takeIf { it > 0 },
            imdbId = o.str("imdbId"),
            rating = o.obj("ratings").dbl("value")?.takeIf { it > 0 },
            seasons = o.arr("seasons").objects().mapNotNull { s ->
                val st = s.obj("statistics")
                Season(
                    number = s.int("seasonNumber") ?: return@mapNotNull null,
                    monitored = s.bool("monitored") ?: false,
                    episodeFileCount = st.int("episodeFileCount") ?: 0,
                    episodeCount = st.int("episodeCount") ?: 0,
                    totalEpisodeCount = st.int("totalEpisodeCount") ?: 0,
                    sizeOnDisk = st.long("sizeOnDisk") ?: 0,
                    nextAiring = Format.instant(st.str("nextAiring")),
                )
            }.sortedByDescending { it.number },
            episodeFileCount = stats.int("episodeFileCount") ?: 0,
            episodeCount = stats.int("episodeCount") ?: 0,
            totalEpisodeCount = stats.int("totalEpisodeCount") ?: 0,
            sizeOnDisk = stats.long("sizeOnDisk") ?: 0,
        )
    }

    // ---------- Images (through the server, never from TheTVDB directly) ----------

    fun poster(seriesId: Int, large: Boolean = false): String = url("/mediacover/$seriesId/poster-${if (large) 500 else 250}.jpg")
    fun fanart(seriesId: Int): String = url("/mediacover/$seriesId/fanart-360.jpg")

    // ---------- Episodes ----------

    suspend fun episodes(seriesId: Int): List<Episode> =
        Api.json(client, Api.get(url("/episode?seriesId=$seriesId"))).objects().mapNotNull(::episode)
            .sortedWith(compareByDescending<Episode> { it.season }.thenByDescending { it.number })

    private fun episode(o: JsonObject): Episode? = Episode(
        raw = o,
        id = o.int("id") ?: return null,
        seriesId = o.int("seriesId") ?: 0,
        season = o.int("seasonNumber") ?: 0,
        number = o.int("episodeNumber") ?: 0,
        absoluteNumber = o.int("absoluteEpisodeNumber"),
        title = o.str("title"),
        overview = o.str("overview"),
        airDate = Format.instant(o.str("airDateUtc")),
        monitored = o.bool("monitored") ?: false,
        hasFile = o.bool("hasFile") ?: false,
        fileId = o.int("episodeFileId")?.takeIf { it > 0 },
        finaleType = o.str("finaleType"),
        runtime = o.int("runtime")?.takeIf { it > 0 },
        tvdbId = o.int("tvdbId")?.takeIf { it > 0 },
    )

    suspend fun episodeFile(fileId: Int): EpisodeFileInfo? {
        val f = Api.json(client, Api.get(url("/episodefile/$fileId"))) as? JsonObject ?: return null
        val mi = f.obj("mediaInfo")
        return EpisodeFileInfo(
            id = f.int("id") ?: fileId,
            relativePath = f.str("relativePath"),
            size = f.long("size") ?: 0,
            quality = f.obj("quality").obj("quality").str("name"),
            releaseGroup = f.str("releaseGroup"),
            videoCodec = mi.str("videoCodec"),
            videoDynamicRange = mi.str("videoDynamicRangeType") ?: mi.str("videoDynamicRange"),
            resolution = mi.str("resolution"),
            audioCodec = mi.str("audioCodec"),
            audioChannels = mi.dbl("audioChannels"),
            audioLanguages = mi.str("audioLanguages"),
            subtitles = mi.str("subtitles"),
            runTime = mi.str("runTime"),
            dateAdded = Format.instant(f.str("dateAdded")),
            customFormatScore = f.int("customFormatScore"),
            cutoffNotMet = f.bool("qualityCutoffNotMet") ?: false,
        )
    }

    suspend fun deleteEpisodeFile(fileId: Int) = Api.send(client, Api.delete(url("/episodefile/$fileId")))

    /** PUT /episode/monitor for one or more episodes. */
    suspend fun setEpisodesMonitored(ids: List<Int>, on: Boolean) {
        val body = buildJsonObject {
            put("episodeIds", JsonArray(ids.map { JsonPrimitive(it) }))
            put("monitored", on)
        }
        putJson("/episode/monitor", body)
    }

    // ---------- Editing ----------

    private suspend fun putJson(path: String, body: JsonObject) =
        Api.send(client, Request.Builder().url(url(path)).put(body.toString().toRequestBody(JSON)).build())

    /** PUT /series/{id} with the series' own resource and [changes] applied. */
    suspend fun update(s: Series, moveFiles: Boolean = false, changes: MutableMap<String, JsonElement>.() -> Unit) {
        val body = JsonObject(s.raw.toMutableMap().apply(changes))
        putJson("/series/${s.id}?moveFiles=$moveFiles", body)
    }

    suspend fun setMonitored(s: Series, on: Boolean) = update(s) { put("monitored", JsonPrimitive(on)) }

    /** Monitors or unmonitors one season. Its episodes follow, as in the web UI. */
    suspend fun setSeasonMonitored(s: Series, season: Int, on: Boolean) = update(s) {
        val seasons = (s.raw["seasons"] as? JsonArray).objects().map { o ->
            if (o.int("seasonNumber") == season) JsonObject(o.toMutableMap().apply { put("monitored", JsonPrimitive(on)) }) else o
        }
        put("seasons", JsonArray(seasons))
    }

    /** Bulk edit; null fields stay as they are. */
    suspend fun editor(
        ids: List<Int>,
        monitored: Boolean? = null,
        qualityProfileId: Int? = null,
        seriesType: String? = null,
        seasonFolder: Boolean? = null,
        rootFolderPath: String? = null,
        moveFiles: Boolean = false,
    ) {
        val body = buildJsonObject {
            put("seriesIds", JsonArray(ids.map { JsonPrimitive(it) }))
            monitored?.let { put("monitored", it) }
            qualityProfileId?.let { put("qualityProfileId", it) }
            seriesType?.let { put("seriesType", it) }
            seasonFolder?.let { put("seasonFolder", it) }
            rootFolderPath?.let { put("rootFolderPath", it); put("moveFiles", moveFiles) }
        }
        putJson("/series/editor", body)
    }

    suspend fun delete(id: Int, deleteFiles: Boolean, addExclusion: Boolean) =
        Api.send(client, Api.delete(url("/series/$id?deleteFiles=$deleteFiles&addImportListExclusion=$addExclusion")))

    suspend fun deleteMany(ids: List<Int>, deleteFiles: Boolean, addExclusion: Boolean) {
        val body = buildJsonObject {
            put("seriesIds", JsonArray(ids.map { JsonPrimitive(it) }))
            put("deleteFiles", deleteFiles)
            put("addImportListExclusion", addExclusion)
        }
        Api.send(client, Request.Builder().url(url("/series/editor")).delete(body.toString().toRequestBody(JSON)).build())
    }

    // ---------- Commands ----------

    private suspend fun command(name: String, extra: JsonObjectBuilder.() -> Unit) {
        val body = buildJsonObject { put("name", name); extra() }
        Api.send(client, Api.postJson(url("/command"), body))
    }

    suspend fun searchSeries(seriesId: Int) = command("SeriesSearch") { put("seriesId", seriesId) }
    suspend fun searchSeason(seriesId: Int, season: Int) = command("SeasonSearch") { put("seriesId", seriesId); put("seasonNumber", season) }
    suspend fun searchEpisodes(ids: List<Int>) = command("EpisodeSearch") { put("episodeIds", JsonArray(ids.map { JsonPrimitive(it) })) }

    /** Bulk search: one SeriesSearch per series (Sonarr has no multi-series search command). */
    suspend fun searchMany(ids: List<Int>) = ids.forEach { searchSeries(it) }

    /** Sonarr v4 reads seriesIds; older versions read seriesId. Both are sent. */
    suspend fun refresh(seriesId: Int) = command("RefreshSeries") {
        put("seriesId", seriesId)
        put("seriesIds", JsonArray(listOf(JsonPrimitive(seriesId))))
    }

    suspend fun renamePreview(seriesId: Int): List<EpisodeRename> =
        Api.json(client, Api.get(url("/rename?seriesId=$seriesId"))).objects().mapNotNull { o ->
            val eps = o.arr("episodeNumbers")?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.orEmpty()
            EpisodeRename(
                episodeFileId = o.int("episodeFileId") ?: return@mapNotNull null,
                label = eps.joinToString(", ") { Format.episode(o.int("seasonNumber"), it).orEmpty() },
                existingPath = o.str("existingPath").orEmpty(),
                newPath = o.str("newPath").orEmpty(),
            )
        }

    suspend fun rename(seriesId: Int, fileIds: List<Int>) = command("RenameFiles") {
        put("seriesId", seriesId)
        put("files", JsonArray(fileIds.map { JsonPrimitive(it) }))
    }

    // ---------- Interactive search ----------

    private val slow get() = client.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()

    suspend fun episodeReleases(episodeId: Int): List<Release> =
        Api.json(slow, Api.get(url("/release?episodeId=$episodeId"))).objects().mapNotNull(::parseRelease)

    /** A whole season: Sonarr returns season packs as well as single episodes. */
    suspend fun seasonReleases(seriesId: Int, season: Int): List<Release> =
        Api.json(slow, Api.get(url("/release?seriesId=$seriesId&seasonNumber=$season"))).objects().mapNotNull(::parseRelease)

    suspend fun grab(r: Release) {
        val body = buildJsonObject {
            put("guid", r.guid)
            put("indexerId", r.indexerId)
        }
        Api.send(client, Api.postJson(url("/release"), body))
    }

    // ---------- History ----------

    suspend fun history(seriesId: Int): List<HistoryItem> =
        Api.json(client, Api.get(url("/history/series?seriesId=$seriesId&includeEpisode=true"))).objects().mapNotNull { o ->
            val id = o.int("id") ?: return@mapNotNull null
            val ep = o.obj("episode")
            val data = o.obj("data")
            HistoryItem(
                server = server,
                id = id,
                kind = when (o.str("eventType")) {
                    "grabbed" -> HistoryKind.GRABBED
                    "downloadFolderImported", "seriesFolderImported" -> HistoryKind.IMPORTED
                    "downloadFailed" -> HistoryKind.FAILED
                    "episodeFileDeleted" -> HistoryKind.DELETED
                    "episodeFileRenamed" -> HistoryKind.RENAMED
                    "downloadIgnored" -> HistoryKind.IGNORED
                    else -> HistoryKind.OTHER
                },
                title = o.str("sourceTitle").orEmpty(),
                detail = Format.episode(ep.int("seasonNumber"), ep.int("episodeNumber")),
                release = o.str("sourceTitle"),
                quality = o.obj("quality").obj("quality").str("name"),
                date = Format.instant(o.str("date")),
                reason = data.str("message") ?: data.str("reason"),
            )
        }.sortedByDescending { it.date }

    // ---------- Settings lists ----------

    suspend fun profiles(): List<Profile> = Api.json(client, Api.get(url("/qualityprofile"))).objects()
        .mapNotNull { o -> Profile(o.int("id") ?: return@mapNotNull null, o.str("name").orEmpty()) }

    suspend fun rootFolders(): List<RootFolder> = Api.json(client, Api.get(url("/rootfolder"))).objects()
        .mapNotNull { o -> RootFolder(o.str("path") ?: return@mapNotNull null, o.long("freeSpace"), o.bool("accessible") ?: true) }

    suspend fun tags(): List<Tag> = Api.json(client, Api.get(url("/tag"))).objects()
        .mapNotNull { o -> Tag(o.int("id") ?: return@mapNotNull null, o.str("label").orEmpty()) }

    // ---------- Adding ----------

    suspend fun lookup(term: String): List<LookupSeries> {
        val q = URLEncoder.encode(term.trim(), "UTF-8")
        return Api.json(client, Api.get(url("/series/lookup?term=$q"))).objects().map { o ->
            LookupSeries(
                raw = o,
                title = o.str("title").orEmpty(),
                year = o.int("year")?.takeIf { it > 0 },
                overview = o.str("overview"),
                network = o.str("network"),
                seasonCount = o.arr("seasons").objects().count { (it.int("seasonNumber") ?: 0) > 0 },
                status = o.str("status"),
                tvdbId = o.int("tvdbId"),
                inLibraryId = o.int("id")?.takeIf { it > 0 },
            )
        }
    }

    /**
     * POST /series with the lookup result plus the user's choices.
     * [monitor] is one of Sonarr's monitor options (all, future, missing, existing, firstSeason, lastSeason, pilot, none, …).
     */
    suspend fun add(
        s: LookupSeries,
        qualityProfileId: Int,
        rootFolderPath: String,
        monitor: String,
        seriesType: String,
        seasonFolder: Boolean,
        searchMissing: Boolean,
        searchCutoffUnmet: Boolean,
        tags: List<Int>,
    ): Series? {
        val fields = s.raw.toMutableMap()
        fields.remove("id")
        fields["qualityProfileId"] = JsonPrimitive(qualityProfileId)
        fields["rootFolderPath"] = JsonPrimitive(rootFolderPath)
        fields["monitored"] = JsonPrimitive(monitor != "none")
        fields["seriesType"] = JsonPrimitive(seriesType)
        fields["seasonFolder"] = JsonPrimitive(seasonFolder)
        fields["tags"] = JsonArray(tags.map { JsonPrimitive(it) })
        fields["addOptions"] = buildJsonObject {
            put("monitor", monitor)
            put("searchForMissingEpisodes", searchMissing)
            put("searchForCutoffUnmetEpisodes", searchCutoffUnmet)
        }
        val created = Api.json(client, Api.postJson(url("/series"), JsonObject(fields))) as? JsonObject
        return created?.let(::series)
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
