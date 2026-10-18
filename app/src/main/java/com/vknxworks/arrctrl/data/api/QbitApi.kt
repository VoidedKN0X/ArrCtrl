package com.vknxworks.arrctrl.data.api

import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.server.Server
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

// ---------- Models ----------

data class Torrent(
    val server: Server,
    val hash: String,
    val name: String,
    val state: String,
    val progress: Double,
    val dlSpeed: Long,
    val upSpeed: Long,
    val ratio: Double,
    val seeds: Int,
    val seedsTotal: Int,
    val peers: Int,
    val peersTotal: Int,
    val category: String,
    val tags: List<String>,
    val size: Long,
    val eta: Long?,
    /** Queue position; 0 or less when queueing is off or the torrent isn't queued. */
    val priority: Int,
    val dlLimit: Long,
    val upLimit: Long,
    val addedOn: Long,
    val forceStart: Boolean,
    val savePath: String?,
) {
    val key: String get() = "${server.id}:$hash"
    val kind: TorrentKind get() = TorrentKind.of(state)
}

/** qBittorrent states grouped the way people think about them. 4.x says "paused", 5.x says "stopped". */
enum class TorrentKind {
    DOWNLOADING, SEEDING, STOPPED, QUEUED, CHECKING, STALLED, ERROR, OTHER;

    val isStopped: Boolean get() = this == STOPPED

    companion object {
        fun of(state: String): TorrentKind = when (state) {
            "downloading", "forcedDL", "metaDL", "forcedMetaDL" -> DOWNLOADING
            "uploading", "forcedUP" -> SEEDING
            "pausedDL", "pausedUP", "stoppedDL", "stoppedUP" -> STOPPED
            "queuedDL", "queuedUP" -> QUEUED
            "checkingDL", "checkingUP", "checkingResumeData", "moving", "allocating" -> CHECKING
            "stalledDL", "stalledUP" -> STALLED
            "error", "missingFiles" -> ERROR
            else -> OTHER
        }
    }
}

data class Transfer(val dlSpeed: Long, val upSpeed: Long, val altSpeed: Boolean, val freeSpace: Long?)

data class TorrentFile(val index: Int, val name: String, val size: Long, val progress: Double, val priority: Int)

data class Tracker(val url: String, val status: Int, val message: String?, val peers: Int, val seeds: Int, val leeches: Int)

data class Peer(val address: String, val client: String?, val country: String?, val progress: Double, val dlSpeed: Long, val upSpeed: Long, val flags: String?)

data class TorrentProperties(
    val savePath: String?,
    val totalSize: Long?,
    val downloaded: Long?,
    val uploaded: Long?,
    val timeElapsed: Long?,
    val seedingTime: Long?,
    val addedOn: Long?,
    val completionOn: Long?,
    val comment: String?,
    val piecesHave: Int?,
    val piecesNum: Int?,
    val pieceSize: Long?,
    val shareRatio: Double?,
)

// ---------- Client ----------

/**
 * qBittorrent WebUI API. Signs in again by itself when the session expires (403), and works with both
 * 4.x (pause/resume) and 5.x (stop/start).
 */
class QbitApi(private val http: Http, val server: Server) {
    private val client get() = http.client(server)
    private fun url(path: String) = server.api(path)

    private suspend fun login() {
        if (!server.usesLogin) return
        val form = FormBody.Builder().add("username", server.username).add("password", server.password).build()
        val body = Api.text(client, Api.post(url("/auth/login"), form)).trim()
        if (body.startsWith("Fails", ignoreCase = true)) throw ApiException(401, "Login failed")
    }

    /** Runs [block]; on 403 (session expired or never started) signs in once and tries again. */
    private suspend fun <T> authed(block: suspend () -> T): T = try {
        block()
    } catch (e: ApiException) {
        if (e.code != 403 || !server.usesLogin) throw e
        login()
        block()
    }

    private suspend fun getJson(path: String): JsonElement? = authed { Api.json(client, Api.get(url(path))) }

    private suspend fun postForm(path: String, vararg fields: Pair<String, String>) = authed {
        val form = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
        Api.send(client, Api.post(url(path), form))
    }

    // ---------- Sync ----------

    /** Incremental /sync/maindata state for one server. */
    class SyncState {
        var rid: Long = 0
        val torrents = LinkedHashMap<String, MutableMap<String, JsonElement>>()
        val serverState = mutableMapOf<String, JsonElement>()
        val categories = sortedSetOf<String>()
        val tags = sortedSetOf<String>()
    }

    /** Fetches what changed since the last call and folds it into [state]. */
    suspend fun sync(state: SyncState) {
        val o = getJson("/sync/maindata?rid=${state.rid}") as? JsonObject ?: return
        if (o.bool("full_update") == true) {
            state.torrents.clear()
            state.serverState.clear()
            state.categories.clear()
            state.tags.clear()
        }
        o.obj("torrents")?.forEach { (hash, el) ->
            val patch = el as? JsonObject ?: return@forEach
            state.torrents.getOrPut(hash) { mutableMapOf() }.putAll(patch)
        }
        o.arr("torrents_removed").strings().forEach { state.torrents.remove(it) }
        o.obj("server_state")?.let { state.serverState.putAll(it) }
        o.obj("categories")?.keys?.let { state.categories.addAll(it) }
        o.arr("categories_removed").strings().forEach { state.categories.remove(it) }
        o.arr("tags").strings().let { state.tags.addAll(it) }
        o.arr("tags_removed").strings().forEach { state.tags.remove(it) }
        state.rid = o.long("rid") ?: state.rid
    }

    fun torrents(state: SyncState): List<Torrent> = state.torrents.map { (hash, fields) -> toTorrent(hash, JsonObject(fields)) }

    /** One torrent, fresh from the server; null when it's gone. */
    suspend fun info(hash: String): Torrent? =
        getJson("/torrents/info?hashes=$hash").objects().firstOrNull()?.let { toTorrent(it.str("hash") ?: hash, it) }

    private fun toTorrent(hash: String, o: JsonObject): Torrent =
        Torrent(
            server = server,
            hash = hash,
            name = o.str("name") ?: hash,
            state = o.str("state").orEmpty(),
            progress = o.dbl("progress") ?: 0.0,
            dlSpeed = o.long("dlspeed") ?: 0,
            upSpeed = o.long("upspeed") ?: 0,
            ratio = o.dbl("ratio") ?: 0.0,
            seeds = o.int("num_seeds") ?: 0,
            seedsTotal = o.int("num_complete") ?: 0,
            peers = o.int("num_leechs") ?: 0,
            peersTotal = o.int("num_incomplete") ?: 0,
            category = o.str("category").orEmpty(),
            tags = o.str("tags").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
            size = o.long("size") ?: o.long("total_size") ?: 0,
            eta = o.long("eta"),
            priority = o.int("priority") ?: 0,
            dlLimit = o.long("dl_limit") ?: 0,
            upLimit = o.long("up_limit") ?: 0,
            addedOn = o.long("added_on") ?: 0,
            forceStart = o.bool("force_start") ?: false,
            savePath = o.str("save_path"),
        )

    fun transfer(state: SyncState): Transfer {
        val o = JsonObject(state.serverState)
        return Transfer(
            dlSpeed = o.long("dl_info_speed") ?: 0,
            upSpeed = o.long("up_info_speed") ?: 0,
            altSpeed = o.bool("use_alt_speed_limits") ?: false,
            freeSpace = o.long("free_space_on_disk"),
        )
    }

    // ---------- Actions ----------

    @Volatile private var legacyPause: Boolean? = null

    /** Stop (5.x) or pause (4.x); remembers which one this server understands. */
    suspend fun stop(hashes: List<String>) = startStop(hashes, start = false)

    suspend fun start(hashes: List<String>) = startStop(hashes, start = true)

    private suspend fun startStop(hashes: List<String>, start: Boolean) {
        val h = hashes.joinToString("|")
        val modern = if (start) "/torrents/start" else "/torrents/stop"
        val legacy = if (start) "/torrents/resume" else "/torrents/pause"
        val known = legacyPause ?: LEGACY.get(server.id)
        if (known == true) { postForm(legacy, "hashes" to h); return }
        try {
            postForm(modern, "hashes" to h)
            legacyPause = false
        } catch (e: ApiException) {
            if (e.code != 404) throw e
            postForm(legacy, "hashes" to h)
            legacyPause = true
            LEGACY[server.id] = true
        }
    }

    suspend fun forceStart(hashes: List<String>, value: Boolean) = postForm("/torrents/setForceStart", "hashes" to hashes.joinToString("|"), "value" to value.toString())
    suspend fun recheck(hashes: List<String>) = postForm("/torrents/recheck", "hashes" to hashes.joinToString("|"))
    suspend fun reannounce(hashes: List<String>) = postForm("/torrents/reannounce", "hashes" to hashes.joinToString("|"))
    suspend fun delete(hashes: List<String>, deleteFiles: Boolean) =
        postForm("/torrents/delete", "hashes" to hashes.joinToString("|"), "deleteFiles" to deleteFiles.toString())

    suspend fun setCategory(hashes: List<String>, category: String) =
        postForm("/torrents/setCategory", "hashes" to hashes.joinToString("|"), "category" to category)

    suspend fun addTags(hashes: List<String>, tags: List<String>) =
        postForm("/torrents/addTags", "hashes" to hashes.joinToString("|"), "tags" to tags.joinToString(","))

    suspend fun removeTags(hashes: List<String>, tags: List<String>) =
        postForm("/torrents/removeTags", "hashes" to hashes.joinToString("|"), "tags" to tags.joinToString(","))

    enum class Move(val path: String) { TOP("/torrents/topPrio"), UP("/torrents/increasePrio"), DOWN("/torrents/decreasePrio"), BOTTOM("/torrents/bottomPrio") }

    /** Queue position. qBittorrent answers 409 when queueing is turned off. */
    suspend fun move(hashes: List<String>, move: Move) = postForm(move.path, "hashes" to hashes.joinToString("|"))

    /** Bytes per second; 0 = unlimited. */
    suspend fun setDownloadLimit(hashes: List<String>, limit: Long) =
        postForm("/torrents/setDownloadLimit", "hashes" to hashes.joinToString("|"), "limit" to limit.coerceAtLeast(0).toString())

    suspend fun setUploadLimit(hashes: List<String>, limit: Long) =
        postForm("/torrents/setUploadLimit", "hashes" to hashes.joinToString("|"), "limit" to limit.coerceAtLeast(0).toString())

    suspend fun toggleAltSpeed() = postForm("/transfer/toggleSpeedLimitsMode")

    /** 0 = don't download, 1 = normal, 6 = high, 7 = maximum. */
    suspend fun setFilePriority(hash: String, indexes: List<Int>, priority: Int) =
        postForm("/torrents/filePrio", "hash" to hash, "id" to indexes.joinToString("|"), "priority" to priority.toString())

    // ---------- Details ----------

    suspend fun properties(hash: String): TorrentProperties {
        val o = getJson("/torrents/properties?hash=$hash") as? JsonObject
        return TorrentProperties(
            savePath = o.str("save_path"),
            totalSize = o.long("total_size"),
            downloaded = o.long("total_downloaded"),
            uploaded = o.long("total_uploaded"),
            timeElapsed = o.long("time_elapsed"),
            seedingTime = o.long("seeding_time"),
            addedOn = o.long("addition_date"),
            completionOn = o.long("completion_date")?.takeIf { it > 0 },
            comment = o.str("comment"),
            piecesHave = o.int("pieces_have"),
            piecesNum = o.int("pieces_num"),
            pieceSize = o.long("piece_size"),
            shareRatio = o.dbl("share_ratio"),
        )
    }

    suspend fun files(hash: String): List<TorrentFile> = getJson("/torrents/files?hash=$hash").objects().mapIndexed { i, o ->
        TorrentFile(
            index = o.int("index") ?: i,
            name = o.str("name").orEmpty(),
            size = o.long("size") ?: 0,
            progress = o.dbl("progress") ?: 0.0,
            priority = o.int("priority") ?: 1,
        )
    }

    suspend fun trackers(hash: String): List<Tracker> = getJson("/torrents/trackers?hash=$hash").objects().map { o ->
        Tracker(
            url = o.str("url").orEmpty(),
            status = o.int("status") ?: 0,
            message = o.str("msg"),
            peers = o.int("num_peers") ?: 0,
            seeds = o.int("num_seeds") ?: 0,
            leeches = o.int("num_leeches") ?: 0,
        )
    }

    suspend fun peers(hash: String): List<Peer> {
        val o = getJson("/sync/torrentPeers?hash=$hash&rid=0") as? JsonObject
        return o.obj("peers")?.mapNotNull { (addr, el) ->
            val p = el as? JsonObject ?: return@mapNotNull null
            Peer(
                address = addr,
                client = p.str("client"),
                country = p.str("country"),
                progress = p.dbl("progress") ?: 0.0,
                dlSpeed = p.long("dl_speed") ?: 0,
                upSpeed = p.long("up_speed") ?: 0,
                flags = p.str("flags"),
            )
        }.orEmpty().sortedByDescending { it.dlSpeed + it.upSpeed }
    }

    suspend fun categories(): List<String> =
        (getJson("/torrents/categories") as? JsonObject)?.keys?.sortedBy { it.lowercase() }.orEmpty()

    // ---------- Adding ----------

    /** Adds magnet links or http(s) .torrent URLs (one per line). */
    suspend fun addLinks(links: List<String>, category: String?, stopped: Boolean) = authed {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("urls", links.joinToString("\n"))
            .apply { addOptions(this, category, stopped) }
            .build()
        val text = Api.text(client, Api.post(url("/torrents/add"), body))
        if (text.trim().startsWith("Fails", ignoreCase = true)) throw ApiException(415, "Not a valid torrent")
    }

    /** Adds a .torrent file's contents. */
    suspend fun addFile(name: String, bytes: ByteArray, category: String?, stopped: Boolean) = authed {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("torrents", name.ifBlank { "file.torrent" }, bytes.toRequestBody(TORRENT_TYPE))
            .apply { addOptions(this, category, stopped) }
            .build()
        val text = Api.text(client, Api.post(url("/torrents/add"), body))
        if (text.trim().startsWith("Fails", ignoreCase = true)) throw ApiException(415, "Not a valid torrent")
    }

    private fun addOptions(b: MultipartBody.Builder, category: String?, stopped: Boolean) {
        if (!category.isNullOrBlank()) b.addFormDataPart("category", category)
        // 5.x reads "stopped", 4.x reads "paused"; each ignores the other.
        b.addFormDataPart("stopped", stopped.toString())
        b.addFormDataPart("paused", stopped.toString())
    }

    companion object {
        private val TORRENT_TYPE = "application/x-bittorrent".toMediaType()

        /** Servers known to need pause/resume instead of stop/start, remembered for the session. */
        private val LEGACY = ConcurrentHashMap<String, Boolean>()
    }
}
