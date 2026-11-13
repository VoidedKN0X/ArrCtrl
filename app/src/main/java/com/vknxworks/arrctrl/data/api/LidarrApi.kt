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

// ---------- Models ----------

data class Artist(
    val server: Server,
    /** The full resource as Lidarr sent it, so updates send back every field unchanged. */
    val raw: JsonObject,
    val id: Int,
    val name: String,
    val sortName: String,
    val disambiguation: String?,
    val overview: String?,
    val artistType: String?,
    /** continuing, ended, deleted */
    val status: String,
    val monitored: Boolean,
    /** all, none, new */
    val monitorNewItems: String,
    val qualityProfileId: Int,
    val metadataProfileId: Int,
    val rootFolderPath: String?,
    val path: String?,
    val tags: List<Int>,
    val genres: List<String>,
    val added: Instant?,
    val albumCount: Int,
    val trackFileCount: Int,
    val trackCount: Int,
    val sizeOnDisk: Long,
    val rating: Double?,
    val nextAlbum: String?,
    val nextAlbumDate: Instant?,
    val foreignArtistId: String?,
) {
    val key: String get() = "${server.id}:$id"
    val progress: Float get() = if (trackCount <= 0) 0f else (trackFileCount.toFloat() / trackCount).coerceIn(0f, 1f)
    val complete: Boolean get() = trackCount > 0 && trackFileCount >= trackCount
}

data class Album(
    val raw: JsonObject,
    val id: Int,
    val artistId: Int,
    val title: String,
    val disambiguation: String?,
    /** Album, EP, Single, Broadcast, Other */
    val albumType: String?,
    val secondaryTypes: List<String>,
    val monitored: Boolean,
    val releaseDate: Instant?,
    val trackFileCount: Int,
    val trackCount: Int,
    val sizeOnDisk: Long,
    val durationMs: Long?,
    val genres: List<String>,
    val artist: Artist?,
) {
    val progress: Float get() = if (trackCount <= 0) 0f else (trackFileCount.toFloat() / trackCount).coerceIn(0f, 1f)
    val complete: Boolean get() = trackCount > 0 && trackFileCount >= trackCount
    val released: Boolean get() = releaseDate?.isBefore(Instant.now()) == true
    val year: Int? get() = releaseDate?.atZone(java.time.ZoneOffset.UTC)?.year
}

/** A search result for adding an artist. [inLibraryId] is set when it's already in Lidarr. */
data class LookupArtist(
    val raw: JsonObject,
    val name: String,
    val disambiguation: String?,
    val overview: String?,
    val artistType: String?,
    val genres: List<String>,
    val foreignArtistId: String?,
    val inLibraryId: Int?,
)

// ---------- Client ----------

/** Lidarr v1 API for the Music screens. Shapes follow Lidarr.Api.V1 (ArtistResource, AlbumResource). */
class LidarrApi(private val http: Http, val server: Server) {
    private val client get() = http.client(server)
    private fun url(path: String) = server.api(path)

    suspend fun artists(): List<Artist> = Api.json(client, Api.get(url("/artist"))).objects().mapNotNull(::artist)

    suspend fun artist(id: Int): Artist? = (Api.json(client, Api.get(url("/artist/$id"))) as? JsonObject)?.let(::artist)

    /** Artist ids with a download in Lidarr's queue, for the "downloading" badge. */
    suspend fun queuedArtistIds(): Set<Int> {
        val body = Api.json(client, Api.get(url("/queue?page=1&pageSize=500&includeUnknownArtistItems=false"))) as? JsonObject
        return body.arr("records").objects().mapNotNull { it.int("artistId") }.toSet()
    }

    private fun artist(o: JsonObject): Artist? {
        val id = o.int("id") ?: return null
        val stats = o.obj("statistics")
        val next = o.obj("nextAlbum")
        return Artist(
            server = server,
            raw = o,
            id = id,
            name = o.str("artistName") ?: "#$id",
            sortName = o.str("sortName") ?: o.str("artistName").orEmpty().lowercase(),
            disambiguation = o.str("disambiguation"),
            overview = o.str("overview"),
            artistType = o.str("artistType"),
            status = o.str("status").orEmpty(),
            monitored = o.bool("monitored") ?: false,
            monitorNewItems = o.str("monitorNewItems") ?: "all",
            qualityProfileId = o.int("qualityProfileId") ?: 0,
            metadataProfileId = o.int("metadataProfileId") ?: 0,
            rootFolderPath = o.str("rootFolderPath"),
            path = o.str("path"),
            tags = o.arr("tags")?.mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }.orEmpty(),
            genres = o.arr("genres").strings(),
            added = Format.instant(o.str("added")),
            albumCount = stats.int("albumCount") ?: 0,
            trackFileCount = stats.int("trackFileCount") ?: 0,
            trackCount = stats.int("trackCount") ?: 0,
            sizeOnDisk = stats.long("sizeOnDisk") ?: 0,
            rating = o.obj("ratings").dbl("value")?.takeIf { it > 0 },
            nextAlbum = next.str("title"),
            nextAlbumDate = Format.instant(next.str("releaseDate")),
            foreignArtistId = o.str("foreignArtistId"),
        )
    }

    private fun album(o: JsonObject): Album? {
        val id = o.int("id") ?: return null
        val stats = o.obj("statistics")
        return Album(
            raw = o,
            id = id,
            artistId = o.int("artistId") ?: 0,
            title = o.str("title") ?: "#$id",
            disambiguation = o.str("disambiguation"),
            albumType = o.str("albumType"),
            secondaryTypes = o.arr("secondaryTypes").strings(),
            monitored = o.bool("monitored") ?: false,
            releaseDate = Format.instant(o.str("releaseDate")),
            trackFileCount = stats.int("trackFileCount") ?: 0,
            trackCount = stats.int("trackCount") ?: 0,
            sizeOnDisk = stats.long("sizeOnDisk") ?: 0,
            durationMs = o.long("duration")?.takeIf { it > 0 },
            genres = o.arr("genres").strings(),
            artist = o.obj("artist")?.let(::artist),
        )
    }

    /** Every album of an artist, newest first. */
    suspend fun albums(artistId: Int): List<Album> =
        Api.json(client, Api.get(url("/album?artistId=$artistId"))).objects().mapNotNull(::album)
            .sortedWith(compareByDescending<Album> { it.releaseDate ?: Instant.MIN }.thenBy { it.title.lowercase() })

    /** Albums released between [start] and [end] (ISO-8601), each with its artist. */
    suspend fun calendar(start: String, end: String, unmonitored: Boolean): List<Album> =
        Api.json(client, Api.get(url("/calendar?start=$start&end=$end&unmonitored=$unmonitored&includeArtist=true"))).objects().mapNotNull(::album)

    /**
     * One page of monitored albums that are missing ([cutoff] false, GET /wanted/missing) or below the cutoff
     * ([cutoff] true, GET /wanted/cutoff), each with its artist, plus the total.
     */
    suspend fun wanted(cutoff: Boolean, page: Int, pageSize: Int = 50): Pair<List<Album>, Int> {
        val path = if (cutoff) "/wanted/cutoff" else "/wanted/missing"
        val body = Api.json(client, Api.get(url("$path?page=$page&pageSize=$pageSize&monitored=true&includeArtist=true"))) as? JsonObject
        val items = body.arr("records").objects().mapNotNull(::album)
        return items to (body.int("totalRecords") ?: items.size)
    }

    // ---------- Images (through the server, never from MusicBrainz or fanart.tv directly) ----------

    fun poster(artistId: Int, large: Boolean = false): String = url("/mediacover/artist/$artistId/poster-${if (large) 500 else 250}.jpg")
    fun fanart(artistId: Int): String = url("/mediacover/artist/$artistId/fanart-360.jpg")

    /** Lidarr keeps album covers at one size; asking for -250 falls back to it. */
    fun cover(albumId: Int): String = url("/mediacover/album/$albumId/cover-250.jpg")

    // ---------- Editing ----------

    private suspend fun putJson(path: String, body: JsonObject) =
        Api.send(client, Request.Builder().url(url(path)).put(body.toString().toRequestBody(JSON)).build())

    /** PUT /artist/{id} with the artist's own resource and [changes] applied. */
    suspend fun update(a: Artist, moveFiles: Boolean = false, changes: MutableMap<String, JsonElement>.() -> Unit) {
        val body = JsonObject(a.raw.toMutableMap().apply(changes))
        putJson("/artist/${a.id}?moveFiles=$moveFiles", body)
    }

    suspend fun setMonitored(a: Artist, on: Boolean) = update(a) { put("monitored", JsonPrimitive(on)) }

    /** PUT /album/monitor for one or more albums. */
    suspend fun setAlbumsMonitored(ids: List<Int>, on: Boolean) {
        val body = buildJsonObject {
            put("albumIds", JsonArray(ids.map { JsonPrimitive(it) }))
            put("monitored", on)
        }
        putJson("/album/monitor", body)
    }

    suspend fun delete(id: Int, deleteFiles: Boolean, addExclusion: Boolean) =
        Api.send(client, Api.delete(url("/artist/$id?deleteFiles=$deleteFiles&addImportListExclusion=$addExclusion")))

    // ---------- Commands ----------

    private suspend fun command(name: String, extra: JsonObjectBuilder.() -> Unit = {}) {
        val body = buildJsonObject { put("name", name); extra() }
        Api.send(client, Api.postJson(url("/command"), body))
    }

    suspend fun searchArtist(artistId: Int) = command("ArtistSearch") { put("artistId", artistId) }
    suspend fun searchAlbums(ids: List<Int>) = command("AlbumSearch") { put("albumIds", JsonArray(ids.map { JsonPrimitive(it) })) }
    suspend fun refresh(artistId: Int) = command("RefreshArtist") { put("artistIds", JsonArray(listOf(JsonPrimitive(artistId)))) }
    suspend fun searchAllMissing() = command("MissingAlbumSearch")
    suspend fun searchAllCutoffUnmet() = command("CutoffUnmetAlbumSearch")

    // ---------- History ----------

    suspend fun history(artistId: Int): List<HistoryItem> =
        Api.json(client, Api.get(url("/history/artist?artistId=$artistId&includeAlbum=true"))).objects().mapNotNull { o ->
            val id = o.int("id") ?: return@mapNotNull null
            val data = o.obj("data")
            HistoryItem(
                server = server,
                id = id,
                kind = lidarrKind(o.str("eventType")),
                title = o.str("sourceTitle").orEmpty(),
                detail = o.obj("album").str("title"),
                release = o.str("sourceTitle"),
                quality = o.obj("quality").obj("quality").str("name"),
                date = Format.instant(o.str("date")),
                reason = data.str("message") ?: data.str("reason"),
                mediaId = o.int("artistId"),
            )
        }.filter { it.kind != HistoryKind.OTHER }.sortedByDescending { it.date }

    // ---------- Settings lists ----------

    suspend fun profiles(): List<Profile> = Api.json(client, Api.get(url("/qualityprofile"))).objects()
        .mapNotNull { o -> Profile(o.int("id") ?: return@mapNotNull null, o.str("name").orEmpty()) }

    suspend fun metadataProfiles(): List<Profile> = Api.json(client, Api.get(url("/metadataprofile"))).objects()
        .mapNotNull { o -> Profile(o.int("id") ?: return@mapNotNull null, o.str("name").orEmpty()) }

    suspend fun rootFolders(): List<RootFolder> = Api.json(client, Api.get(url("/rootfolder"))).objects()
        .mapNotNull { o -> RootFolder(o.str("path") ?: return@mapNotNull null, o.long("freeSpace"), o.bool("accessible") ?: true) }

    suspend fun tags(): List<Tag> = Api.json(client, Api.get(url("/tag"))).objects()
        .mapNotNull { o -> Tag(o.int("id") ?: return@mapNotNull null, o.str("label").orEmpty()) }

    // ---------- Adding ----------

    suspend fun lookup(term: String): List<LookupArtist> {
        val q = URLEncoder.encode(term.trim(), "UTF-8")
        return Api.json(client, Api.get(url("/artist/lookup?term=$q"))).objects().map { o ->
            LookupArtist(
                raw = o,
                name = o.str("artistName").orEmpty(),
                disambiguation = o.str("disambiguation"),
                overview = o.str("overview"),
                artistType = o.str("artistType"),
                genres = o.arr("genres").strings(),
                foreignArtistId = o.str("foreignArtistId"),
                inLibraryId = o.int("id")?.takeIf { it > 0 },
            )
        }
    }

    /**
     * POST /artist with the lookup result plus the user's choices. [monitor] is one of Lidarr's MonitorTypes
     * (all, future, missing, existing, latest, first, none); [monitorNewItems] all, none or new.
     */
    suspend fun add(
        a: LookupArtist,
        qualityProfileId: Int,
        metadataProfileId: Int,
        rootFolderPath: String,
        monitor: String,
        monitorNewItems: String,
        searchMissing: Boolean,
        tags: List<Int>,
    ): Artist? {
        val fields = a.raw.toMutableMap()
        fields.remove("id")
        fields["qualityProfileId"] = JsonPrimitive(qualityProfileId)
        fields["metadataProfileId"] = JsonPrimitive(metadataProfileId)
        fields["rootFolderPath"] = JsonPrimitive(rootFolderPath)
        fields["monitored"] = JsonPrimitive(monitor != "none")
        fields["monitorNewItems"] = JsonPrimitive(monitorNewItems)
        fields["tags"] = JsonArray(tags.map { JsonPrimitive(it) })
        fields["addOptions"] = buildJsonObject {
            put("monitor", monitor)
            put("monitored", monitor != "none")
            put("searchForMissingAlbums", searchMissing)
        }
        val created = Api.json(client, Api.postJson(url("/artist"), JsonObject(fields))) as? JsonObject
        return created?.let(::artist)
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Lidarr's history events. Only whole downloads count as imported (downloadImported); the per-track
 * trackFileImported events would otherwise list (and notify) every track of an album.
 */
fun lidarrKind(event: String?): HistoryKind = when (event) {
    "grabbed" -> HistoryKind.GRABBED
    "downloadImported", "artistFolderImported" -> HistoryKind.IMPORTED
    "downloadFailed", "albumImportIncomplete" -> HistoryKind.FAILED
    "trackFileDeleted" -> HistoryKind.DELETED
    "trackFileRenamed" -> HistoryKind.RENAMED
    "downloadIgnored" -> HistoryKind.IGNORED
    else -> HistoryKind.OTHER
}
