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
    /** Names that only exist on the user's own network or tailnet (Tailscale MagicDNS is `*.ts.net`). */
    private val LOCAL_NAME = Regex("""^(localhost|[^.]+|.*\.(local|lan|home|internal|home\.arpa|ts\.net))$""", RegexOption.IGNORE_CASE)
    private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    /**
     * True for a host that can only be on the user's own network or tailnet: loopback, private and
     * link-local IPv4 (127/8, 10/8, 172.16/12, 192.168/16, 169.254/16), Tailscale's 100.64/10,
     * loopback, unique-local and link-local IPv6 (::1, fc00::/7 including Tailscale's
     * fd7a:115c:a1e0::/48, fe80::/10), single-label names and .local/.lan/.home/.internal/.home.arpa/.ts.net.
     * Only used to pick the default scheme; nothing is looked up.
     */
    fun isLocalHost(rawHost: String): Boolean {
        val h = rawHost.trim().trim('[', ']').lowercase()
        if (h.isEmpty()) return false
        if (IPV4.matches(h)) {
            val b = h.split('.').map { it.toIntOrNull() ?: return false }
            if (b.any { it !in 0..255 }) return false
            return b[0] == 127 || b[0] == 10 ||
                (b[0] == 172 && b[1] in 16..31) ||
                (b[0] == 192 && b[1] == 168) ||
                (b[0] == 169 && b[1] == 254) ||
                (b[0] == 100 && b[1] in 64..127)
        }
        if (h.contains(':')) {
            if (h == "::1") return true
            val first = h.substringBefore(':').ifEmpty { return false }.padStart(4, '0').take(4)
            val word = first.toIntOrNull(16) ?: return false
            return (word and 0xFE00) == 0xFC00 || (word and 0xFFC0) == 0xFE80
        }
        return LOCAL_NAME.matches(h)
    }

    /**
     * Cleans up a typed address: trims it, drops a trailing slash, and adds a scheme when there is none:
     * http:// for addresses on the user's own network or tailnet (see [isLocalHost]), like 192.168.1.10,
     * 100.101.102.103 or "nas.lan", and https:// for anything else, including public IP addresses.
     * A typed http:// or https:// is always kept. Returns null when it still isn't a valid http(s) address.
     */
    fun normalise(raw: String): String? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) {
            val host = s.substringBefore('/').substringBefore('?').let { h ->
                if (h.startsWith("[")) h.substringBefore(']') + "]" else h.substringBeforeLast(':', h)
            }
            s = (if (isLocalHost(host)) "http://" else "https://") + s
        }
        val url = s.toHttpUrlOrNull() ?: return null
        val path = url.encodedPath.trimEnd('/')
        val port = if (url.port == HttpUrl.defaultPort(url.scheme)) "" else ":${url.port}"
        val host = if (url.host.contains(':')) "[${url.host}]" else url.host
        return "${url.scheme}://$host$port$path"
    }
}
