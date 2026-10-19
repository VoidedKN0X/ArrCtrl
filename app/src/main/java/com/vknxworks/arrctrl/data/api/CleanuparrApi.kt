package com.vknxworks.arrctrl.data.api

import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.server.Server
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.Instant

data class ClientHealth(val name: String, val type: String?, val healthy: Boolean, val error: String?)

data class CleanupStats(
    val hours: Int,
    val events: Int,
    val eventsBySeverity: Map<String, Int>,
    val strikes: Int,
    val strikesByType: Map<String, Int>,
    val recovered: Int,
    val removals: Int,
    val removalsByReason: Map<String, Int>,
    val jobRuns: Int,
    val jobsCompleted: Int,
    val jobsFailed: Int,
    val downloadClients: List<ClientHealth>,
    val arrInstances: List<ClientHealth>,
)

data class CleanupEvent(val id: String, val time: Instant?, val type: String, val message: String, val severity: String)

data class Strike(val id: String, val type: String, val time: Instant?, val title: String, val dryRun: Boolean)

data class CleanupJob(val jobType: String, val name: String, val status: String, val schedule: String?, val next: Instant?, val previous: Instant?) {
    /** Cleanuparr refuses to run its Seeker job by hand. */
    val canTrigger: Boolean get() = !jobType.equals("Seeker", ignoreCase = true)
}

/** Cleanuparr API. Stats come from /api/v2/stats; the deprecated v1 endpoint is never used. */
class CleanuparrApi(private val http: Http, val server: Server) {
    private val client get() = http.client(server)
    private fun url(path: String) = server.api(path)

    private fun counts(o: JsonObject?): Map<String, Int> =
        o?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.content?.toIntOrNull()?.let { k to it } }?.toMap().orEmpty()

    private fun health(list: List<JsonObject>) = list.map { h ->
        ClientHealth(h.str("name") ?: h.str("clientName").orEmpty(), h.str("type") ?: h.str("clientTypeName"), h.bool("isHealthy") ?: false, h.str("errorMessage"))
    }

    suspend fun stats(hours: Int): CleanupStats {
        val o = Api.json(client, Api.get(url("/v2/stats?hours=$hours"))) as? JsonObject
        val ev = o.obj("events"); val st = o.obj("strikes"); val rm = o.obj("removals"); val jobs = o.obj("jobs"); val hl = o.obj("health")
        return CleanupStats(
            hours = o.int("timeframeHours") ?: hours,
            events = ev.int("total") ?: 0,
            eventsBySeverity = counts(ev.obj("bySeverity")),
            strikes = st.int("total") ?: 0,
            strikesByType = counts(st.obj("byType")),
            recovered = st.int("recovered") ?: 0,
            removals = rm.int("total") ?: 0,
            removalsByReason = counts(rm.obj("byReason")),
            jobRuns = jobs.int("total") ?: 0,
            jobsCompleted = jobs.int("completed") ?: 0,
            jobsFailed = jobs.int("failed") ?: 0,
            downloadClients = health(hl.arr("downloadClients").objects()),
            arrInstances = health(hl.arr("arrInstances").objects()),
        )
    }

    suspend fun events(page: Int, pageSize: Int = 30): Pair<List<CleanupEvent>, Int> {
        val o = Api.json(client, Api.get(url("/events?page=$page&pageSize=$pageSize"))) as? JsonObject
        val items = o.arr("items").objects().map { e ->
            CleanupEvent(e.str("id").orEmpty(), Format.instant(e.str("timestamp")), e.str("eventType").orEmpty(), e.str("message").orEmpty(), e.str("severity").orEmpty())
        }
        return items to (o.int("totalCount") ?: items.size)
    }

    suspend fun recentStrikes(count: Int = 20): List<Strike> = Api.json(client, Api.get(url("/strikes/recent?count=$count"))).objects().map { s ->
        Strike(s.str("id").orEmpty(), s.str("type").orEmpty(), Format.instant(s.str("createdAt")), s.str("title").orEmpty(), s.bool("isDryRun") ?: false)
    }

    suspend fun jobs(): List<CleanupJob> = Api.json(client, Api.get(url("/jobs"))).objects().map { j ->
        CleanupJob(
            jobType = j.str("jobType") ?: j.str("name").orEmpty(),
            name = j.str("name") ?: j.str("jobType").orEmpty(),
            status = j.str("status").orEmpty(),
            schedule = j.str("schedule"),
            next = Format.instant(j.str("nextRunTime")),
            previous = Format.instant(j.str("previousRunTime")),
        )
    }

    /** Runs a job once now (POST /api/jobs/{type}/trigger, no body). */
    suspend fun trigger(jobType: String) = Api.send(client, Api.post(url("/jobs/$jobType/trigger"), ByteArray(0).toRequestBody(null)))

    /** Download client health as Cleanuparr sees it. */
    suspend fun clientHealth(): List<ClientHealth> = health(Api.json(client, Api.get(url("/health"))).objects())
}
