package com.vknxworks.arrctrl.data.net

import com.vknxworks.arrctrl.data.server.ServerStore
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.Request

/**
 * Picks the right client for each image request: one under a saved server's address goes through that
 * server's client (its API key, its pinned certificate); anything else goes through the plain client
 * without credentials.
 */
class ServerCallFactory(private val http: Http, private val servers: ServerStore) : Call.Factory {
    override fun newCall(request: Request): Call {
        val url = request.url
        val server = servers.servers.value
            .filter { s -> s.httpUrl?.let { under(it, url) } == true }
            .maxByOrNull { it.url.length }
        return (if (server != null) http.client(server) else http.base).newCall(request)
    }

    /** Same scheme, host and port as [base], and a path inside the server's own path. */
    private fun under(base: HttpUrl, url: HttpUrl): Boolean {
        if (base.scheme != url.scheme || !base.host.equals(url.host, ignoreCase = true) || base.port != url.port) return false
        val prefix = base.encodedPath.trimEnd('/') + "/"
        return url.encodedPath.startsWith(prefix, ignoreCase = true)
    }
}
