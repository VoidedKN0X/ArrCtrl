package com.vknxworks.arrctrl.data.api

import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

/** A backup the server keeps of its own database and settings. */
data class ServerBackup(
    val id: Int,
    val name: String,
    /** scheduled, manual or update. */
    val type: String,
    val size: Long,
    val time: Instant?,
)

/** One version in the server's update list. */
data class ServerUpdate(
    val version: String,
    val branch: String?,
    val releaseDate: Instant?,
    val installed: Boolean,
    val installedOn: Instant?,
    val installable: Boolean,
    val latest: Boolean,
    val added: List<String>,
    val fixed: List<String>,
)

/** What the server says about itself and how it is updated. */
data class ServerVersion(
    val version: String?,
    val branch: String?,
    val isDocker: Boolean,
    /** builtIn, script, external, apt or docker (config/host); null when it couldn't be read. */
    val updateMechanism: String?,
    /** The package maintainer's note about updates, when the server has one. */
    val updateMessage: String?,
) {
    /** The server can install an update itself when asked (as its own web page offers "Install latest"). */
    val canInstall: Boolean get() = !isDocker && (updateMechanism == "builtIn" || updateMechanism == "script")
}

data class ServerLogEntry(
    val id: Int,
    val time: Instant?,
    /** trace, debug, info, warn, error, fatal. */
    val level: String,
    val logger: String?,
    val message: String,
    val exception: String?,
    val exceptionType: String?,
)

/**
 * The system pages Radarr, Sonarr, Lidarr and Prowlarr share (same paths under /api/v3 or /api/v1, from
 * their OpenAPI specs): system/backup, update, log, system/status, config/host and the Backup and
 * ApplicationUpdate commands.
 */
class ArrSystemApi(private val http: Http, val server: Server) {
    private val client get() = http.client(server)
    private fun url(path: String) = server.api(path)

    /** GET /system/backup, newest first. */
    suspend fun backups(): List<ServerBackup> =
        Api.json(client, Api.get(url("/system/backup"))).objects().mapNotNull { o ->
            ServerBackup(
                id = o.int("id") ?: return@mapNotNull null,
                name = o.str("name").orEmpty(),
                type = o.str("type").orEmpty(),
                size = o.long("size") ?: 0,
                time = Format.instant(o.str("time")),
            )
        }.sortedByDescending { it.time ?: Instant.EPOCH }

    /** POST /command {"name":"Backup"}: the server makes a manual backup in the background. */
    suspend fun backupNow() = command("Backup")

    /** DELETE /system/backup/{id}: removes the backup file from the server. */
    suspend fun deleteBackup(id: Int) = Api.send(client, Api.delete(url("/system/backup/$id")))

    /** GET /update, newest first as the server gives it. */
    suspend fun updates(): List<ServerUpdate> =
        Api.json(client, Api.get(url("/update"))).objects().mapNotNull { o ->
            val changes = o.obj("changes")
            ServerUpdate(
                version = o.str("version") ?: return@mapNotNull null,
                branch = o.str("branch"),
                releaseDate = Format.instant(o.str("releaseDate")),
                installed = o.bool("installed") ?: false,
                installedOn = Format.instant(o.str("installedOn")),
                installable = o.bool("installable") ?: false,
                latest = o.bool("latest") ?: false,
                added = changes.arr("new").strings(),
                fixed = changes.arr("fixed").strings(),
            )
        }

    /** GET /system/status and /config/host (the second may be refused; then [ServerVersion.updateMechanism] is null). */
    suspend fun version(): ServerVersion {
        val status = Api.json(client, Api.get(url("/system/status"))) as? JsonObject
        val host = runCatching { Api.json(client, Api.get(url("/config/host"))) as? JsonObject }.getOrNull()
        return ServerVersion(
            version = status.str("version"),
            branch = status.str("branch") ?: host.str("branch"),
            isDocker = status.bool("isDocker") ?: false,
            updateMechanism = host.str("updateMechanism"),
            updateMessage = status.str("packageUpdateMechanismMessage"),
        )
    }

    /** POST /command {"name":"ApplicationUpdate"}: the server downloads and installs its latest update, then restarts. */
    suspend fun installUpdate() = command("ApplicationUpdate")

    /**
     * GET /log, newest first. [level] is null for everything, or info, warn or error, which the server reads
     * as "this level and worse".
     */
    suspend fun logs(page: Int, level: String?, pageSize: Int = 50): Pair<List<ServerLogEntry>, Int> {
        val filter = if (level != null) "&level=$level" else ""
        val body = Api.json(client, Api.get(url("/log?page=$page&pageSize=$pageSize&sortKey=time&sortDirection=descending$filter"))) as? JsonObject
        val items = body.arr("records").objects().mapNotNull { o ->
            ServerLogEntry(
                id = o.int("id") ?: return@mapNotNull null,
                time = Format.instant(o.str("time")),
                level = o.str("level").orEmpty().lowercase(),
                logger = o.str("logger"),
                message = o.str("message").orEmpty(),
                exception = o.str("exception"),
                exceptionType = o.str("exceptionType"),
            )
        }
        return items to (body.int("totalRecords") ?: items.size)
    }

    private suspend fun command(name: String) {
        Api.send(client, Api.postJson(url("/command"), buildJsonObject { put("name", name) }))
    }

    companion object {
        /** The services with these system pages. */
        val TYPES = setOf(ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR, ServiceType.PROWLARR)
    }
}
