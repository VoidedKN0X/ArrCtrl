package com.vknxworks.arrctrl.data.server

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/**
 * One configured service instance. Secrets ([apiKey], [password]) are held in memory in plain
 * text and only ever stored encrypted (see [ServerStore]).
 */
data class Server(
    val id: String = UUID.randomUUID().toString(),
    val type: ServiceType,
    val name: String,
    /** Normalised base address, no trailing slash. */
    val url: String,
    val apiKey: String = "",
    /** qBittorrent only, when not using an API key. */
    val username: String = "",
    val password: String = "",
    /** SHA-256 (hex, lower case) of a self-signed certificate the user trusted; "" when none. */
    val certSha256: String = "",
) {
    val httpUrl: HttpUrl? get() = url.toHttpUrlOrNull()
    val isEncrypted: Boolean get() = url.startsWith("https://", ignoreCase = true)

    /** qBittorrent signs in with username and password unless an API key is set. */
    val usesLogin: Boolean get() = type.auth == AuthKind.QBITTORRENT && apiKey.isBlank()

    /** Absolute API URL for [path] (e.g. "/system/status"). */
    fun api(path: String): String = url + type.apiBase + path

    /** True when this and [other] point at the same service, so an import updates instead of duplicating. */
    fun sameEndpoint(other: Server): Boolean = type == other.type && url.equals(other.url, ignoreCase = true)
}

object Urls {
    private val LOCAL_HOST = Regex("""^(\d{1,3}(\.\d{1,3}){3}|\[[0-9a-fA-F:]+]|localhost|[^.]+|.*\.(local|lan|home|internal|home\.arpa))$""", RegexOption.IGNORE_CASE)

    /**
     * Cleans up a typed address: trims it, drops a trailing slash, and adds a scheme when there is none
     * (http:// for IP addresses and local names like "nas.lan", https:// for anything else).
     * Returns null when it still isn't a valid http(s) address.
     */
    fun normalise(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) {
            val host = s.substringBefore('/').substringBefore('?').let { h ->
                if (h.startsWith("[")) h.substringBefore(']') + "]" else h.substringBeforeLast(':', h)
            }
            s = (if (LOCAL_HOST.matches(host)) "http://" else "https://") + s
        }
        val url = s.toHttpUrlOrNull() ?: return null
        val path = url.encodedPath.trimEnd('/')
        val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
        val host = if (url.host.contains(':')) "[${url.host}]" else url.host
        return "${url.scheme}://$host$port$path"
    }
}
