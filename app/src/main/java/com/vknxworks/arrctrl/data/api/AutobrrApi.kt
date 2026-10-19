package com.vknxworks.arrctrl.data.api

import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.server.Server
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

data class AutobrrFilter(val id: Int, val name: String, val enabled: Boolean, val priority: Int)

/** PUSH_APPROVED, PUSH_REJECTED, PUSH_ERROR or PENDING, per action that ran. */
data class AutobrrAction(val status: String, val action: String?, val client: String?, val rejections: List<String>)

data class AutobrrRelease(
    val id: Long,
    val name: String,
    val title: String?,
    val indexer: String?,
    val filter: String?,
    /** FILTER_APPROVED, PENDING or (older databases) FILTER_REJECTED */
    val filterStatus: String,
    val rejections: List<String>,
    val size: Long,
    val time: Instant?,
    val actions: List<AutobrrAction>,
) {
    /** The outcome worth showing: an action error, a push, a rejection, or still pending. */
    val outcome: String
        get() = when {
            actions.any { it.status == "PUSH_ERROR" } -> "PUSH_ERROR"
            actions.any { it.status == "PUSH_APPROVED" } -> "PUSH_APPROVED"
            actions.any { it.status == "PUSH_REJECTED" } -> "PUSH_REJECTED"
            filterStatus == "FILTER_REJECTED" -> "FILTER_REJECTED"
            filterStatus == "FILTER_APPROVED" -> "FILTER_APPROVED"
            else -> "PENDING"
        }
}

data class AutobrrStats(val total: Long, val filtered: Long, val filterRejected: Long, val approved: Long, val rejected: Long, val errors: Long)

data class IrcChannel(val name: String, val enabled: Boolean, val monitoring: Boolean, val lastAnnounce: Instant?)

data class IrcNetwork(
    val id: Long,
    val name: String,
    val enabled: Boolean,
    val server: String,
    val port: Int,
    val connected: Boolean,
    val healthy: Boolean,
    val connectedSince: Instant?,
    val nick: String?,
    val errors: List<String>,
    val channels: List<IrcChannel>,
)

/** autobrr API. Never reads or shows the IRC and channel passwords it also returns. */
class AutobrrApi(private val http: Http, val server: Server) {
    private val client get() = http.client(server)
    private fun url(path: String) = server.api(path)

    /** Go's zero time ("0001-01-01…") means "never". */
    private fun time(s: String?): Instant? = Format.instant(s)?.takeIf { it.isAfter(Instant.EPOCH) }

    suspend fun filters(): List<AutobrrFilter> = Api.json(client, Api.get(url("/filters"))).objects().mapNotNull { o ->
        AutobrrFilter(o.int("id") ?: return@mapNotNull null, o.str("name").orEmpty(), o.bool("enabled") ?: false, o.int("priority") ?: 0)
    }.sortedWith(compareByDescending<AutobrrFilter> { it.priority }.thenBy { it.name.lowercase() })

    suspend fun setFilterEnabled(id: Int, on: Boolean) {
        val body = buildJsonObject { put("enabled", on) }
        Api.send(client, Request.Builder().url(url("/filters/$id/enabled")).put(body.toString().toRequestBody(JSON)).build())
    }

    suspend fun releases(offset: Int, limit: Int = 50): Pair<List<AutobrrRelease>, Long> {
        val o = Api.json(client, Api.get(url("/release?limit=$limit&offset=$offset"))) as? JsonObject
        val items = o.arr("data").objects().mapNotNull { r ->
            AutobrrRelease(
                id = r.long("id") ?: return@mapNotNull null,
                name = r.str("name") ?: r.str("title").orEmpty(),
                title = r.str("title"),
                indexer = r.obj("indexer").str("name") ?: r.obj("indexer").str("identifier"),
                filter = r.str("filter"),
                filterStatus = r.str("filter_status").orEmpty(),
                rejections = r.arr("rejections").strings(),
                size = r.long("size") ?: 0,
                time = time(r.str("timestamp")),
                actions = r.arr("action_status").objects().map { a ->
                    AutobrrAction(a.str("status").orEmpty(), a.str("action"), a.str("client"), a.arr("rejections").strings())
                },
            )
        }
        return items to (o.long("count") ?: items.size.toLong())
    }

    suspend fun stats(): AutobrrStats {
        val o = Api.json(client, Api.get(url("/release/stats"))) as? JsonObject
        return AutobrrStats(
            total = o.long("total_count") ?: 0,
            filtered = o.long("filtered_count") ?: 0,
            filterRejected = o.long("filter_rejected_count") ?: 0,
            approved = o.long("push_approved_count") ?: 0,
            rejected = o.long("push_rejected_count") ?: 0,
            errors = o.long("push_error_count") ?: 0,
        )
    }

    suspend fun irc(): List<IrcNetwork> = Api.json(client, Api.get(url("/irc"))).objects().mapNotNull { n ->
        IrcNetwork(
            id = n.long("id") ?: return@mapNotNull null,
            name = n.str("name").orEmpty(),
            enabled = n.bool("enabled") ?: false,
            server = n.str("server").orEmpty(),
            port = n.int("port") ?: 0,
            connected = n.bool("connected") ?: false,
            healthy = n.bool("healthy") ?: false,
            connectedSince = time(n.str("connected_since")),
            nick = n.str("current_nick") ?: n.str("nick"),
            errors = n.arr("connection_errors").strings(),
            channels = n.arr("channels").objects().map { c ->
                IrcChannel(c.str("name").orEmpty(), c.bool("enabled") ?: false, c.bool("monitoring") ?: false, time(c.str("last_announce")))
            },
        )
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
