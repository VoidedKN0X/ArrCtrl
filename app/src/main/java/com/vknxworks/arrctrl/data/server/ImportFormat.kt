package com.vknxworks.arrctrl.data.server

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Reads the ArrCtrl import format (documented in the README):
 *
 * ```
 * { "arrctrl": 1, "servers": [
 *     { "type": "radarr", "name": "Radarr 4K", "url": "http://192.168.1.10:7879", "apiKey": "…" },
 *     { "type": "qbittorrent", "url": "http://192.168.1.10:8080", "username": "admin", "password": "…" }
 * ] }
 * ```
 *
 * A bare list of servers is accepted too. Field names are matched loosely ("apiKey", "api_key", "apikey").
 */
object ImportFormat {
    sealed interface Entry {
        data class Ok(val server: Server) : Entry
        /** [index] is 1-based; [reason] is one of [Problem]. */
        data class Bad(val index: Int, val reason: Problem, val detail: String = "") : Entry
    }

    enum class Problem { UNKNOWN_TYPE, BAD_URL, MISSING_KEY, MISSING_LOGIN }

    class FormatException(message: String) : Exception(message)

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    fun parse(text: String): List<Entry> {
        val trimmed = text.trim().removePrefix("﻿")
        if (trimmed.isEmpty()) throw FormatException("empty")
        val root = runCatching { json.parseToJsonElement(trimmed) }.getOrElse { throw FormatException("not json") }
        val list: List<JsonElement> = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["servers"] as? JsonArray) ?: throw FormatException("no servers")
            else -> throw FormatException("not json")
        }
        return list.mapIndexed { i, el -> entry(i + 1, el as? JsonObject ?: JsonObject(emptyMap())) }
    }

    private fun entry(index: Int, o: JsonObject): Entry {
        val rawType = o.str("type", "service", "app")
        val type = ServiceType.of(rawType) ?: return Entry.Bad(index, Problem.UNKNOWN_TYPE, rawType.orEmpty())
        val rawUrl = o.str("url", "address", "host").orEmpty()
        val url = Urls.normalise(rawUrl) ?: return Entry.Bad(index, Problem.BAD_URL, rawUrl)
        val apiKey = o.str("apiKey", "api_key", "apikey", "key", "token").orEmpty().trim()
        val username = o.str("username", "user").orEmpty()
        val password = o.str("password", "pass").orEmpty()
        when {
            type.auth == AuthKind.API_KEY && apiKey.isEmpty() -> return Entry.Bad(index, Problem.MISSING_KEY, url)
            type.auth == AuthKind.QBITTORRENT && apiKey.isEmpty() && username.isEmpty() ->
                return Entry.Bad(index, Problem.MISSING_LOGIN, url)
        }
        val cert = o.str("certSha256", "cert_sha256", "fingerprint").orEmpty()
            .replace(":", "").replace(" ", "").lowercase()
            .takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
            .orEmpty()
        return Entry.Ok(
            Server(
                type = type,
                name = o.str("name").orEmpty().ifBlank { "" },
                url = url,
                apiKey = apiKey,
                username = if (apiKey.isEmpty()) username else "",
                password = if (apiKey.isEmpty()) password else "",
                certSha256 = cert,
            ),
        )
    }

    /** First non-blank string among [names], compared case-insensitively. */
    private fun JsonObject.str(vararg names: String): String? {
        for (n in names) {
            val v = entries.firstOrNull { it.key.equals(n, ignoreCase = true) }?.value as? JsonPrimitive ?: continue
            val s = v.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
            return s
        }
        return null
    }
}
