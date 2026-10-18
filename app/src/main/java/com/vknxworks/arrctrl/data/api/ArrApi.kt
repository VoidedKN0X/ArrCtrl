package com.vknxworks.arrctrl.data.api

import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.time.Instant

// ---------- Models ----------

/** One download in a Radarr or Sonarr queue. */
data class QueueItem(
    val server: Server,
    val id: Int,
    /** Movie title, or series title. */
    val title: String,
    /** Year for movies; "S01E02 · Episode title" for episodes. */
    val detail: String?,
    /** Release name. */
    val release: String,
    val quality: String?,
    val size: Long,
    val sizeLeft: Long,
    val etaSeconds: Long?,
    /** queued, paused, downloading, completed, failed, warning, delay, … */
    val status: String,
    /** ok, warning, error */
    val trackedStatus: String,
    /** downloading, importBlocked, importPending, importing, imported, failedPending, failed, ignored */
    val trackedState: String,
    val messages: List<String>,
    val errorMessage: String?,
    val downloadId: String?,
    val protocol: String?,
    val client: String?,
    val indexer: String?,
    val outputPath: String?,
) {
    val key: String get() = "${server.id}:$id"
    val progress: Float get() = if (size <= 0) 0f else ((size - sizeLeft).toFloat() / size).coerceIn(0f, 1f)
    val hasWarning: Boolean get() = trackedStatus == "warning" || trackedStatus == "error" || status == "warning" || status == "failed"

    /** Waiting to be imported but stuck: the case manual import is for. */
    val canManualImport: Boolean
        get() = !downloadId.isNullOrBlank() && (trackedState == "importBlocked" || trackedState == "importPending" ||
            (status == "completed" && trackedStatus != "ok"))
}

enum class HistoryKind { GRABBED, IMPORTED, FAILED, DELETED, RENAMED, IGNORED, OTHER }

data class HistoryItem(
    val server: Server,
    val id: Int,
    val kind: HistoryKind,
    val title: String,
    val detail: String?,
    val release: String?,
    val quality: String?,
    val date: Instant?,
    val reason: String?,
) {
    val key: String get() = "${server.id}:$id"
}

/** A file found for a stuck download, with what Radarr/Sonarr thinks it is. */
data class ImportFile(
    val raw: JsonObject,
    val path: String,
    val name: String,
    val size: Long,
    val quality: String?,
    val rejections: List<String>,
    /** Radarr: matched movie. */
    val movieId: Int?,
    val movieTitle: String?,
    /** Sonarr: matched series and episodes. */
    val seriesId: Int?,
    val seriesTitle: String?,
    val seasonNumber: Int?,
    val episodeIds: List<Int>,
    val episodeLabel: String?,
)

/** A movie or series in the library, for re-mapping a manual import. */
data class LibraryEntry(val id: Int, val title: String, val year: Int?)

data class EpisodeEntry(val id: Int, val season: Int, val number: Int, val title: String?, val hasFile: Boolean)

// ---------- Client ----------

/** Radarr and Sonarr v3 API, for the parts both share plus their differences. */
class ArrApi(private val http: Http, val server: Server) {
    private val client get() = http.client(server)
    private val isSonarr get() = server.type == ServiceType.SONARR

    private fun url(path: String) = server.api(path)

    suspend fun queue(): List<QueueItem> {
        val include = if (isSonarr) "includeSeries=true&includeEpisode=true" else "includeMovie=true"
        val body = Api.json(client, Api.get(url("/queue?page=1&pageSize=200&$include"))) as? JsonObject
        return body.arr("records").objects().mapNotNull(::queueItem)
    }

    private fun queueItem(o: JsonObject): QueueItem? {
        val id = o.int("id") ?: return null
        val release = o.str("title").orEmpty()
        val (title, detail) = if (isSonarr) {
            val series = o.obj("series")
            val ep = o.obj("episode")
            val code = Format.episode(ep.int("seasonNumber") ?: o.int("seasonNumber"), ep.int("episodeNumber"))
            (series.str("title") ?: release) to listOfNotNull(code, ep.str("title")).joinToString(" · ").ifBlank { null }
        } else {
            val movie = o.obj("movie")
            (movie.str("title") ?: release) to movie.int("year")?.takeIf { it > 0 }?.toString()
        }
        val messages = o.arr("statusMessages").objects().flatMap { m ->
            val msgs = m.arr("messages").strings()
            if (msgs.isEmpty()) listOfNotNull(m.str("title")) else msgs
        }.distinct()
        return QueueItem(
            server = server,
            id = id,
            title = title,
            detail = detail,
            release = release,
            quality = o.obj("quality").obj("quality").str("name"),
            size = o.long("size") ?: 0,
            sizeLeft = o.long("sizeleft") ?: 0,
            etaSeconds = Format.arrTimeLeft(o.str("timeleft")),
            status = o.str("status").orEmpty(),
            trackedStatus = o.str("trackedDownloadStatus").orEmpty(),
            trackedState = o.str("trackedDownloadState").orEmpty(),
            messages = messages,
            errorMessage = o.str("errorMessage"),
            downloadId = o.str("downloadId"),
            protocol = o.str("protocol"),
            client = o.str("downloadClient"),
            indexer = o.str("indexer"),
            outputPath = o.str("outputPath"),
        )
    }

    /** DELETE /queue/{id}. [skipRedownload] only matters together with [blocklist]. */
    suspend fun removeFromQueue(id: Int, removeFromClient: Boolean, blocklist: Boolean, skipRedownload: Boolean) {
        Api.send(
            client,
            Api.delete(url("/queue/$id?removeFromClient=$removeFromClient&blocklist=$blocklist&skipRedownload=${blocklist && skipRedownload}")),
        )
    }

    suspend fun history(page: Int, pageSize: Int = 50): Pair<List<HistoryItem>, Int> {
        val include = if (isSonarr) "includeSeries=true&includeEpisode=true" else "includeMovie=true"
        val body = Api.json(client, Api.get(url("/history?page=$page&pageSize=$pageSize&sortKey=date&sortDirection=descending&$include"))) as? JsonObject
        val items = body.arr("records").objects().mapNotNull { o ->
            val id = o.int("id") ?: return@mapNotNull null
            val (title, detail) = if (isSonarr) {
                val ep = o.obj("episode")
                (o.obj("series").str("title") ?: o.str("sourceTitle").orEmpty()) to
                    listOfNotNull(Format.episode(ep.int("seasonNumber"), ep.int("episodeNumber")), ep.str("title")).joinToString(" · ").ifBlank { null }
            } else {
                val m = o.obj("movie")
                (m.str("title") ?: o.str("sourceTitle").orEmpty()) to m.int("year")?.takeIf { it > 0 }?.toString()
            }
            val data = o.obj("data")
            HistoryItem(
                server = server,
                id = id,
                kind = kind(o.str("eventType")),
                title = title,
                detail = detail,
                release = o.str("sourceTitle"),
                quality = o.obj("quality").obj("quality").str("name"),
                date = Format.instant(o.str("date")),
                reason = data.str("message") ?: data.str("reason"),
            )
        }
        return items to (body.int("totalRecords") ?: items.size)
    }

    private fun kind(event: String?): HistoryKind = when (event) {
        "grabbed" -> HistoryKind.GRABBED
        "downloadFolderImported", "movieFolderImported", "seriesFolderImported" -> HistoryKind.IMPORTED
        "downloadFailed" -> HistoryKind.FAILED
        "movieFileDeleted", "episodeFileDeleted" -> HistoryKind.DELETED
        "movieFileRenamed", "episodeFileRenamed" -> HistoryKind.RENAMED
        "downloadIgnored" -> HistoryKind.IGNORED
        else -> HistoryKind.OTHER
    }

    // ---------- Manual import ----------

    suspend fun importFiles(downloadId: String): List<ImportFile> {
        val id = URLEncoder.encode(downloadId, "UTF-8")
        val body = Api.json(client, Api.get(url("/manualimport?downloadId=$id&filterExistingFiles=true")))
        return body.objects().mapNotNull { o ->
            val path = o.str("path") ?: return@mapNotNull null
            val movie = o.obj("movie")
            val series = o.obj("series")
            val eps = o.arr("episodes").objects()
            ImportFile(
                raw = o,
                path = path,
                name = o.str("relativePath") ?: o.str("name") ?: path.substringAfterLast('/'),
                size = o.long("size") ?: 0,
                quality = o.obj("quality").obj("quality").str("name"),
                rejections = o.arr("rejections").objects().mapNotNull { it.str("reason") },
                movieId = movie.int("id"),
                movieTitle = movie.str("title")?.let { t -> movie.int("year")?.takeIf { it > 0 }?.let { "$t ($it)" } ?: t },
                seriesId = series.int("id"),
                seriesTitle = series.str("title"),
                seasonNumber = o.int("seasonNumber"),
                episodeIds = eps.mapNotNull { it.int("id") },
                episodeLabel = episodeLabel(eps),
            )
        }
    }

    private fun episodeLabel(eps: List<JsonObject>): String? {
        if (eps.isEmpty()) return null
        val codes = eps.mapNotNull { Format.episode(it.int("seasonNumber"), it.int("episodeNumber")) }
        return if (eps.size == 1) listOfNotNull(codes.firstOrNull(), eps[0].str("title")).joinToString(" · ") else codes.joinToString(", ")
    }

    /**
     * Runs Radarr/Sonarr's ManualImport command for [files], keeping the quality, languages, release group
     * and flags they detected. Movie, series and episodes come from each file (possibly re-mapped by the user).
     */
    suspend fun manualImport(files: List<ImportFile>, downloadId: String) {
        val body = buildJsonObject {
            put("name", "ManualImport")
            put("importMode", "auto")
            put(
                "files",
                buildJsonArray {
                    files.forEach { f ->
                        add(
                            buildJsonObject {
                                put("path", f.path)
                                f.raw["folderName"]?.let { put("folderName", it) }
                                f.raw["quality"]?.let { put("quality", it) }
                                put("languages", f.raw["languages"] ?: JsonArray(emptyList()))
                                f.raw["releaseGroup"]?.let { put("releaseGroup", it) }
                                put("indexerFlags", f.raw["indexerFlags"] ?: JsonPrimitive(0))
                                put("downloadId", f.raw["downloadId"] ?: JsonPrimitive(downloadId))
                                if (isSonarr) {
                                    put("seriesId", f.seriesId ?: 0)
                                    put("episodeIds", JsonArray(f.episodeIds.map { JsonPrimitive(it) }))
                                    f.raw["releaseType"]?.let { put("releaseType", it) }
                                } else {
                                    put("movieId", f.movieId ?: 0)
                                }
                            },
                        )
                    }
                },
            )
        }
        Api.send(client, Api.postJson(url("/command"), body))
    }

    /** Movies (Radarr) or series (Sonarr) in the library, sorted by title. */
    suspend fun library(): List<LibraryEntry> {
        val body: JsonElement? = Api.json(client, Api.get(url(if (isSonarr) "/series" else "/movie")))
        return body.objects().mapNotNull { o ->
            val id = o.int("id") ?: return@mapNotNull null
            LibraryEntry(id, o.str("title") ?: return@mapNotNull null, o.int("year")?.takeIf { it > 0 })
        }.sortedBy { it.title.lowercase() }
    }

    /** Sonarr: all episodes of a series. */
    suspend fun episodes(seriesId: Int): List<EpisodeEntry> {
        val body = Api.json(client, Api.get(url("/episode?seriesId=$seriesId")))
        return body.objects().mapNotNull { o ->
            EpisodeEntry(
                id = o.int("id") ?: return@mapNotNull null,
                season = o.int("seasonNumber") ?: 0,
                number = o.int("episodeNumber") ?: 0,
                title = o.str("title"),
                hasFile = o.bool("hasFile") ?: false,
            )
        }.sortedWith(compareBy({ it.season }, { it.number }))
    }
}
