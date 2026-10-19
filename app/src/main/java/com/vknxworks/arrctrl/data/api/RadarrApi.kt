package com.vknxworks.arrctrl.data.api

import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.server.Server
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant

// ---------- Models ----------

data class Movie(
    val server: Server,
    /** The full resource as Radarr sent it, so updates send back every field unchanged. */
    val raw: JsonObject,
    val id: Int,
    val title: String,
    val sortTitle: String,
    val year: Int?,
    val overview: String?,
    val monitored: Boolean,
    val hasFile: Boolean,
    val isAvailable: Boolean,
    /** tba, announced, inCinemas, released, deleted */
    val status: String,
    val minimumAvailability: String,
    val qualityProfileId: Int,
    val rootFolderPath: String?,
    val path: String?,
    val tags: List<Int>,
    val sizeOnDisk: Long,
    val added: Instant?,
    val inCinemas: Instant?,
    val digitalRelease: Instant?,
    val physicalRelease: Instant?,
    val runtime: Int?,
    val certification: String?,
    val genres: List<String>,
    val studio: String?,
    val tmdbId: Int?,
    val imdbId: String?,
    val imdbRating: Double?,
    val tmdbRating: Double?,
    val rottenTomatoes: Double?,
    val file: MovieFile?,
) {
    val key: String get() = "${server.id}:$id"

    /** Earliest home release, else the cinema date: what "release date" sorting uses. */
    val releaseDate: Instant? get() = listOfNotNull(digitalRelease, physicalRelease).minOrNull() ?: inCinemas
}

data class MovieFile(
    val id: Int,
    val relativePath: String?,
    val size: Long,
    val quality: String?,
    val releaseGroup: String?,
    val edition: String?,
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

/** One result of an interactive search. */
data class Release(
    val raw: JsonObject,
    val guid: String,
    val indexerId: Int,
    val title: String,
    val indexer: String?,
    val size: Long,
    val ageHours: Double?,
    val seeders: Int?,
    val leechers: Int?,
    val protocol: String?,
    val quality: String?,
    val qualityWeight: Int,
    val customFormatScore: Int,
    val customFormats: List<String>,
    val rejected: Boolean,
    val temporarilyRejected: Boolean,
    val rejections: List<String>,
    val releaseWeight: Int,
    val languages: List<String>,
    val infoUrl: String?,
)

data class RenameItem(val movieFileId: Int, val existingPath: String, val newPath: String)

data class Profile(val id: Int, val name: String)
data class RootFolder(val path: String, val freeSpace: Long?, val accessible: Boolean)
data class Tag(val id: Int, val label: String)

/** A search result for adding a movie. [inLibraryId] is set when it's already in Radarr. */
data class LookupMovie(val raw: JsonObject, val title: String, val year: Int?, val overview: String?, val tmdbId: Int?, val inLibraryId: Int?, val runtime: Int?, val genres: List<String>)

/** Reads one interactive-search result (same shape in Radarr and Sonarr). */
fun parseRelease(o: JsonObject): Release? {
    val guid = o.str("guid") ?: return null
    return Release(
        raw = o,
        guid = guid,
        indexerId = o.int("indexerId") ?: 0,
        title = o.str("title").orEmpty(),
        indexer = o.str("indexer"),
        size = o.long("size") ?: 0,
        ageHours = o.dbl("ageHours"),
        seeders = o.int("seeders"),
        leechers = o.int("leechers"),
        protocol = o.str("protocol"),
        quality = o.obj("quality").obj("quality").str("name"),
        qualityWeight = o.int("qualityWeight") ?: 0,
        customFormatScore = o.int("customFormatScore") ?: 0,
        customFormats = o.arr("customFormats").objects().mapNotNull { it.str("name") },
        rejected = o.bool("rejected") ?: false,
        temporarilyRejected = o.bool("temporarilyRejected") ?: false,
        rejections = o.arr("rejections").strings(),
        releaseWeight = o.int("releaseWeight") ?: 0,
        languages = o.arr("languages").objects().mapNotNull { it.str("name") },
        infoUrl = o.str("infoUrl"),
    )
}

// ---------- Client ----------

/** Radarr v3 API for the Movies tab. */
class RadarrApi(private val http: Http, val server: Server) {
    private val client get() = http.client(server)
    private fun url(path: String) = server.api(path)

    suspend fun movies(): List<Movie> = Api.json(client, Api.get(url("/movie"))).objects().mapNotNull(::movie)

    suspend fun movie(id: Int): Movie? = (Api.json(client, Api.get(url("/movie/$id"))) as? JsonObject)?.let(::movie)

    /** Movie ids with a download in Radarr's queue, for the "downloading" badge. */
    suspend fun queuedMovieIds(): Set<Int> {
        val body = Api.json(client, Api.get(url("/queue?page=1&pageSize=500&includeUnknownMovieItems=false"))) as? JsonObject
        return body.arr("records").objects().mapNotNull { it.int("movieId") }.toSet()
    }

    private fun movie(o: JsonObject): Movie? {
        val id = o.int("id") ?: return null
        val ratings = o.obj("ratings")
        val f = o.obj("movieFile")
        return Movie(
            server = server,
            raw = o,
            id = id,
            title = o.str("title") ?: "#$id",
            sortTitle = o.str("sortTitle") ?: o.str("title").orEmpty().lowercase(),
            year = o.int("year")?.takeIf { it > 0 },
            overview = o.str("overview"),
            monitored = o.bool("monitored") ?: false,
            hasFile = o.bool("hasFile") ?: false,
            isAvailable = o.bool("isAvailable") ?: false,
            status = o.str("status").orEmpty(),
            minimumAvailability = o.str("minimumAvailability") ?: "released",
            qualityProfileId = o.int("qualityProfileId") ?: 0,
            rootFolderPath = o.str("rootFolderPath"),
            path = o.str("path"),
            tags = o.arr("tags")?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.orEmpty(),
            sizeOnDisk = o.long("sizeOnDisk") ?: o.obj("statistics").long("sizeOnDisk") ?: 0,
            added = Format.instant(o.str("added")),
            inCinemas = Format.instant(o.str("inCinemas")),
            digitalRelease = Format.instant(o.str("digitalRelease")),
            physicalRelease = Format.instant(o.str("physicalRelease")),
            runtime = o.int("runtime")?.takeIf { it > 0 },
            certification = o.str("certification"),
            genres = o.arr("genres").strings(),
            studio = o.str("studio"),
            tmdbId = o.int("tmdbId")?.takeIf { it > 0 },
            imdbId = o.str("imdbId"),
            imdbRating = ratings.obj("imdb").dbl("value")?.takeIf { it > 0 },
            tmdbRating = ratings.obj("tmdb").dbl("value")?.takeIf { it > 0 },
            rottenTomatoes = ratings.obj("rottenTomatoes").dbl("value")?.takeIf { it > 0 },
            file = f?.let { movieFile(it) },
        )
    }

    private fun movieFile(f: JsonObject): MovieFile? {
        val id = f.int("id") ?: return null
        val mi = f.obj("mediaInfo")
        return MovieFile(
            id = id,
            relativePath = f.str("relativePath"),
            size = f.long("size") ?: 0,
            quality = f.obj("quality").obj("quality").str("name"),
            releaseGroup = f.str("releaseGroup"),
            edition = f.str("edition"),
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

    /** Movies with a cinema, digital or physical release between [start] and [end] (ISO-8601). */
    suspend fun calendar(start: String, end: String, unmonitored: Boolean): List<Movie> =
        Api.json(client, Api.get(url("/calendar?start=$start&end=$end&unmonitored=$unmonitored"))).objects().mapNotNull(::movie)

    // ---------- Images (always through the server, never straight from TMDB) ----------

    fun poster(movieId: Int, large: Boolean = false): String = url("/mediacover/$movieId/poster-${if (large) 500 else 250}.jpg")
    fun fanart(movieId: Int): String = url("/mediacover/$movieId/fanart-360.jpg")

    // ---------- Editing ----------

    /** PUT /movie/{id} with the movie's own resource and [changes] applied. */
    suspend fun update(movie: Movie, moveFiles: Boolean = false, changes: MutableMap<String, kotlinx.serialization.json.JsonElement>.() -> Unit) {
        val body = JsonObject(movie.raw.toMutableMap().apply(changes))
        Api.send(client, Request.Builder().url(url("/movie/${movie.id}?moveFiles=$moveFiles")).put(body.toString().toRequestBody(JSON)).build())
    }

    suspend fun setMonitored(movie: Movie, on: Boolean) = update(movie) { put("monitored", JsonPrimitive(on)) }

    /** Bulk edit; null fields stay as they are. */
    suspend fun editor(
        ids: List<Int>,
        monitored: Boolean? = null,
        qualityProfileId: Int? = null,
        minimumAvailability: String? = null,
        rootFolderPath: String? = null,
        moveFiles: Boolean = false,
        tags: List<Int>? = null,
    ) {
        val body = buildJsonObject {
            put("movieIds", JsonArray(ids.map { JsonPrimitive(it) }))
            monitored?.let { put("monitored", it) }
            qualityProfileId?.let { put("qualityProfileId", it) }
            minimumAvailability?.let { put("minimumAvailability", it) }
            rootFolderPath?.let { put("rootFolderPath", it); put("moveFiles", moveFiles) }
            tags?.let { put("tags", JsonArray(it.map { t -> JsonPrimitive(t) })); put("applyTags", "replace") }
        }
        Api.send(client, Request.Builder().url(url("/movie/editor")).put(body.toString().toRequestBody(JSON)).build())
    }

    suspend fun delete(id: Int, deleteFiles: Boolean, addExclusion: Boolean) =
        Api.send(client, Api.delete(url("/movie/$id?deleteFiles=$deleteFiles&addImportExclusion=$addExclusion")))

    suspend fun deleteMany(ids: List<Int>, deleteFiles: Boolean, addExclusion: Boolean) {
        val body = buildJsonObject {
            put("movieIds", JsonArray(ids.map { JsonPrimitive(it) }))
            put("deleteFiles", deleteFiles)
            put("addImportExclusion", addExclusion)
        }
        Api.send(client, Request.Builder().url(url("/movie/editor")).delete(body.toString().toRequestBody(JSON)).build())
    }

    suspend fun deleteFile(fileId: Int) = Api.send(client, Api.delete(url("/moviefile/$fileId")))

    // ---------- Commands ----------

    private suspend fun command(name: String, extra: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        val body = buildJsonObject { put("name", name); extra() }
        Api.send(client, Api.postJson(url("/command"), body))
    }

    suspend fun search(ids: List<Int>) = command("MoviesSearch") { put("movieIds", JsonArray(ids.map { JsonPrimitive(it) })) }
    suspend fun refresh(ids: List<Int>) = command("RefreshMovie") { put("movieIds", JsonArray(ids.map { JsonPrimitive(it) })) }

    suspend fun renamePreview(movieId: Int): List<RenameItem> =
        Api.json(client, Api.get(url("/rename?movieId=$movieId"))).objects().mapNotNull { o ->
            RenameItem(o.int("movieFileId") ?: return@mapNotNull null, o.str("existingPath").orEmpty(), o.str("newPath").orEmpty())
        }

    suspend fun rename(movieId: Int, fileIds: List<Int>) = command("RenameFiles") {
        put("movieId", movieId)
        put("files", JsonArray(fileIds.map { JsonPrimitive(it) }))
    }

    // ---------- Interactive search ----------

    /** Asks every indexer; can take a while, so the read timeout is longer than usual. */
    suspend fun releases(movieId: Int): List<Release> {
        val slow = client.newBuilder().readTimeout(120, java.util.concurrent.TimeUnit.SECONDS).build()
        return Api.json(slow, Api.get(url("/release?movieId=$movieId"))).objects().mapNotNull(::parseRelease)
    }

    /** Sends a release to the download client. */
    suspend fun grab(r: Release) {
        val body = buildJsonObject {
            put("guid", r.guid)
            put("indexerId", r.indexerId)
        }
        Api.send(client, Api.postJson(url("/release"), body))
    }

    // ---------- History ----------

    suspend fun history(movieId: Int): List<HistoryItem> =
        Api.json(client, Api.get(url("/history/movie?movieId=$movieId"))).objects().mapNotNull { o ->
            val id = o.int("id") ?: return@mapNotNull null
            val data = o.obj("data")
            HistoryItem(
                server = server,
                id = id,
                kind = when (o.str("eventType")) {
                    "grabbed" -> HistoryKind.GRABBED
                    "downloadFolderImported", "movieFolderImported" -> HistoryKind.IMPORTED
                    "downloadFailed" -> HistoryKind.FAILED
                    "movieFileDeleted" -> HistoryKind.DELETED
                    "movieFileRenamed" -> HistoryKind.RENAMED
                    "downloadIgnored" -> HistoryKind.IGNORED
                    else -> HistoryKind.OTHER
                },
                title = o.str("sourceTitle").orEmpty(),
                detail = null,
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

    suspend fun lookup(term: String): List<LookupMovie> {
        val q = URLEncoder.encode(term.trim(), "UTF-8")
        return Api.json(client, Api.get(url("/movie/lookup?term=$q"))).objects().map { o ->
            LookupMovie(
                raw = o,
                title = o.str("title").orEmpty(),
                year = o.int("year")?.takeIf { it > 0 },
                overview = o.str("overview"),
                tmdbId = o.int("tmdbId"),
                inLibraryId = o.int("id")?.takeIf { it > 0 },
                runtime = o.int("runtime")?.takeIf { it > 0 },
                genres = o.arr("genres").strings(),
            )
        }
    }

    /**
     * POST /movie with the lookup result plus the user's choices. [monitor] is movieOnly,
     * movieAndCollection or none; [minimumAvailability] announced, inCinemas or released.
     */
    suspend fun add(
        m: LookupMovie,
        qualityProfileId: Int,
        rootFolderPath: String,
        monitor: String,
        minimumAvailability: String,
        searchNow: Boolean,
        tags: List<Int>,
    ): Movie? {
        val fields = m.raw.toMutableMap()
        fields.remove("id")
        fields["qualityProfileId"] = JsonPrimitive(qualityProfileId)
        fields["rootFolderPath"] = JsonPrimitive(rootFolderPath)
        fields["monitored"] = JsonPrimitive(monitor != "none")
        fields["minimumAvailability"] = JsonPrimitive(minimumAvailability)
        fields["tags"] = JsonArray(tags.map { JsonPrimitive(it) })
        fields["addOptions"] = buildJsonObject {
            put("monitor", monitor)
            put("searchForMovie", searchNow)
        }
        val created = Api.json(client, Api.postJson(url("/movie"), JsonObject(fields))) as? JsonObject
        return created?.let(::movie)
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
