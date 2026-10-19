package com.vknxworks.arrctrl.data.api

import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.server.Server
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

data class Indexer(
    val raw: JsonObject,
    val id: Int,
    val name: String,
    val enabled: Boolean,
    /** torrent or usenet */
    val protocol: String?,
    /** public, semiPrivate, private */
    val privacy: String?,
    val priority: Int,
    val definition: String?,
    /** Set while Prowlarr has paused the indexer after failures. */
    val disabledTill: Instant?,
    val failingSince: Instant?,
)

data class IndexerStat(
    val indexerId: Int,
    val name: String,
    val queries: Int,
    val grabs: Int,
    val rssQueries: Int,
    val failedQueries: Int,
    val failedGrabs: Int,
    val avgResponseMs: Int,
)

data class SearchResult(
    val raw: JsonObject,
    val guid: String,
    val indexerId: Int,
    val indexer: String?,
    val title: String,
    val size: Long,
    val ageHours: Double?,
    val seeders: Int?,
    val leechers: Int?,
    val grabs: Int?,
    val protocol: String?,
    val categories: List<String>,
)

/** One warning from a server's own health check (Radarr, Sonarr, Prowlarr share the format). */
data class HealthIssue(val source: String?, val type: String, val message: String, val wikiUrl: String?)

data class DiskSpace(val path: String, val label: String?, val free: Long, val total: Long) {
    val freeFraction: Double get() = if (total <= 0) 1.0 else free.toDouble() / total
}

/** Health checks and disk space, shared by every *arr app (all expose the same endpoints). */
suspend fun arrHealth(http: Http, server: Server): List<HealthIssue> =
    Api.json(http.client(server), Api.get(server.api("/health"))).objects().map { o ->
        HealthIssue(o.str("source"), o.str("type") ?: "warning", o.str("message").orEmpty(), o.str("wikiUrl"))
    }

suspend fun arrDiskSpace(http: Http, server: Server): List<DiskSpace> =
    Api.json(http.client(server), Api.get(server.api("/diskspace"))).objects().mapNotNull { o ->
        DiskSpace(o.str("path") ?: return@mapNotNull null, o.str("label"), o.long("freeSpace") ?: 0, o.long("totalSpace") ?: 0)
    }

/** Prowlarr v1 API. */
class ProwlarrApi(private val http: Http, val server: Server) {
    private val client get() = http.client(server)
    private fun url(path: String) = server.api(path)

    suspend fun indexers(): List<Indexer> {
        val statuses = runCatching { Api.json(client, Api.get(url("/indexerstatus"))).objects() }.getOrDefault(emptyList())
            .associateBy { it.int("indexerId") }
        return Api.json(client, Api.get(url("/indexer"))).objects().mapNotNull { o ->
            val id = o.int("id") ?: return@mapNotNull null
            val st = statuses[id]
            Indexer(
                raw = o,
                id = id,
                name = o.str("name") ?: "#$id",
                enabled = o.bool("enable") ?: false,
                protocol = o.str("protocol"),
                privacy = o.str("privacy"),
                priority = o.int("priority") ?: 25,
                definition = o.str("definitionName") ?: o.str("implementationName"),
                disabledTill = Format.instant(st.str("disabledTill"))?.takeIf { it.isAfter(Instant.now()) },
                failingSince = Format.instant(st.str("initialFailure")),
            )
        }.sortedBy { it.name.lowercase() }
    }

    suspend fun stats(): List<IndexerStat> {
        val o = Api.json(client, Api.get(url("/indexerstats"))) as? JsonObject
        return o.arr("indexers").objects().mapNotNull { s ->
            IndexerStat(
                indexerId = s.int("indexerId") ?: return@mapNotNull null,
                name = s.str("indexerName").orEmpty(),
                queries = s.int("numberOfQueries") ?: 0,
                grabs = s.int("numberOfGrabs") ?: 0,
                rssQueries = s.int("numberOfRssQueries") ?: 0,
                failedQueries = s.int("numberOfFailedQueries") ?: 0,
                failedGrabs = s.int("numberOfFailedGrabs") ?: 0,
                avgResponseMs = s.int("averageResponseTime") ?: 0,
            )
        }
    }

    /** PUT /indexer/{id} with the indexer's own resource and only "enable" changed. */
    suspend fun setEnabled(i: Indexer, on: Boolean) {
        val body = JsonObject(i.raw.toMutableMap().apply { put("enable", JsonPrimitive(on)) })
        Api.send(client, Request.Builder().url(url("/indexer/${i.id}")).put(body.toString().toRequestBody(JSON)).build())
    }

    /** POST /indexer/test. Returns null when it passed, or Prowlarr's reasons when it didn't. */
    suspend fun test(i: Indexer): String? {
        val req = Request.Builder().url(url("/indexer/test")).post(i.raw.toString().toRequestBody(JSON)).build()
        val slow = client.newBuilder().readTimeout(90, TimeUnit.SECONDS).build()
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            slow.newCall(req).execute().use { r ->
                if (r.isSuccessful) return@use null
                val text = r.body?.string().orEmpty()
                val reasons = runCatching { ApiJson.parseToJsonElement(text).objects().mapNotNull { it.str("errorMessage") } }.getOrDefault(emptyList())
                reasons.joinToString("\n").ifBlank { "HTTP ${r.code}" }
            }
        }
    }

    /** Searches every enabled indexer (or [indexerIds]); slow indexers make this take a while. */
    suspend fun search(query: String, indexerIds: List<Int> = emptyList()): List<SearchResult> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val ids = indexerIds.joinToString("") { "&indexerIds=$it" }
        val slow = client.newBuilder().readTimeout(120, TimeUnit.SECONDS).build()
        return Api.json(slow, Api.get(url("/search?query=$q&type=search&limit=100$ids"))).objects().mapNotNull { o ->
            SearchResult(
                raw = o,
                guid = o.str("guid") ?: return@mapNotNull null,
                indexerId = o.int("indexerId") ?: 0,
                indexer = o.str("indexer"),
                title = o.str("title").orEmpty(),
                size = o.long("size") ?: 0,
                ageHours = o.dbl("ageHours"),
                seeders = o.int("seeders"),
                leechers = o.int("leechers"),
                grabs = o.int("grabs"),
                protocol = o.str("protocol"),
                categories = o.arr("categories").objects().mapNotNull { it.str("name") },
            )
        }
    }

    /** Sends a search result to the download client configured in Prowlarr. */
    suspend fun grab(r: SearchResult) {
        val body = buildJsonObject {
            put("guid", r.guid)
            put("indexerId", r.indexerId)
        }
        Api.send(client, Api.postJson(url("/search"), body))
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
