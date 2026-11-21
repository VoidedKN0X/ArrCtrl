package com.vknxworks.arrctrl.data.api

import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The server refused a change and said why (its validation messages), in its own words. */
class ServerRefusedException(val reasons: List<String>) : Exception(reasons.joinToString("\n"))

/**
 * Quality profiles and custom formats of Radarr, Sonarr and Lidarr (qualityprofile, customformat and
 * customformat/schema under /api/v3 or /api/v1, from their OpenAPI specs). Resources are kept as the JSON
 * the server sent, so a save sends back every field, also the ones the app doesn't show.
 */
class ArrProfilesApi(private val http: Http, val server: Server) {
    private val client get() = http.client(server)
    private fun url(path: String) = server.api(path)

    suspend fun qualityProfiles(): List<JsonObject> =
        Api.json(client, Api.get(url("/qualityprofile"))).objects().sortedBy { it.str("name").orEmpty().lowercase() }

    /** PUT /qualityprofile/{id} with the whole profile. Returns it as the server saved it. */
    suspend fun saveQualityProfile(profile: JsonObject): JsonObject {
        val id = profile.int("id") ?: error("profile without an id")
        return write(Request.Builder().url(url("/qualityprofile/$id")).put(profile.toString().toRequestBody(JSON)).build()) ?: profile
    }

    suspend fun customFormats(): List<JsonObject> =
        Api.json(client, Api.get(url("/customformat"))).objects().sortedBy { it.str("name").orEmpty().lowercase() }

    /** The kinds of condition a custom format can have, each with its empty fields (GET /customformat/schema). */
    suspend fun customFormatSchema(): List<JsonObject> =
        Api.json(client, Api.get(url("/customformat/schema"))).objects()

    /** PUT /customformat/{id} for an existing format, POST /customformat for a new one (no id yet). */
    suspend fun saveCustomFormat(format: JsonObject): JsonObject {
        val id = format.int("id")?.takeIf { it > 0 }
        val body = format.toString().toRequestBody(JSON)
        val request = if (id != null) Request.Builder().url(url("/customformat/$id")).put(body).build()
        else Request.Builder().url(url("/customformat")).post(body).build()
        return write(request) ?: format
    }

    /** DELETE /customformat/{id}: the server also drops it from every quality profile's scores. */
    suspend fun deleteCustomFormat(id: Int) = Api.send(client, Api.delete(url("/customformat/$id")))

    /**
     * Sends a change. When the server refuses it with its validation answer (HTTP 400 and a list of
     * `errorMessage`s), those messages are passed on instead of a bare "HTTP 400".
     */
    private suspend fun write(request: Request): JsonObject? = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val json = if (text.isBlank()) null else runCatching { ApiJson.parseToJsonElement(text) }.getOrNull()
            if (!r.isSuccessful) {
                val reasons = when (json) {
                    is JsonArray -> json.objects().mapNotNull { it.str("errorMessage") }
                    is JsonObject -> listOfNotNull(json.str("message") ?: json.str("errorMessage"))
                    else -> emptyList()
                }
                if (r.code == 400 && reasons.isNotEmpty()) throw ServerRefusedException(reasons.take(5))
                throw ApiException(r.code, r.message.ifBlank { "HTTP ${r.code}" })
            }
            json as? JsonObject
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** The services with quality profiles and custom formats. */
        val TYPES = setOf(ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR)
    }
}
