package com.vknxworks.arrctrl.data.net

import com.vknxworks.arrctrl.data.server.ServerStore
import okhttp3.Call
import okhttp3.Request

/**
 * Picks the right client for each image request: one under a saved server's address goes through that
 * server's client (its API key, its pinned certificate); anything else goes through the plain client
 * without credentials.
 */
class ServerCallFactory(private val http: Http, private val servers: ServerStore) : Call.Factory {
    override fun newCall(request: Request): Call {
        val url = request.url.toString()
        val server = servers.servers.value
            .filter { url.startsWith(it.url + "/", ignoreCase = true) }
            .maxByOrNull { it.url.length }
        return (if (server != null) http.client(server) else http.base).newCall(request)
    }
}
