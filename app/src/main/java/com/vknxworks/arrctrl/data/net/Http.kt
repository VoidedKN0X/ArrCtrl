package com.vknxworks.arrctrl.data.net

import com.vknxworks.arrctrl.BuildConfig
import com.vknxworks.arrctrl.data.server.AuthKind
import com.vknxworks.arrctrl.data.server.Server
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * HTTP for every server. One shared connection pool; each server gets a light client on top with
 * its own certificate pin, credentials and (for qBittorrent) cookie session.
 */
class Http {
    val base: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "ArrCtrl/${BuildConfig.VERSION_NAME} (Android)").build())
        }
        .build()

    private val jars = ConcurrentHashMap<String, SessionJar>()

    /** Client for a saved server; its qBittorrent session is kept between calls. */
    fun client(server: Server): OkHttpClient = build(server, jars.getOrPut(server.id + "|" + server.url) { SessionJar(server.httpUrl) })

    /** Client for a draft (connection test before saving): a fresh session every time. */
    fun draftClient(server: Server): OkHttpClient = build(server, SessionJar(server.httpUrl))

    /** Forgets every qBittorrent session (the app locked, or every server was removed). */
    fun forgetAll() = jars.clear()

    /** Forgets a server's qBittorrent session, e.g. after its password changed or it was removed. */
    fun forget(server: Server) {
        jars.keys.filter { it.startsWith(server.id + "|") }.forEach { jars.remove(it) }
    }

    private fun build(server: Server, jar: SessionJar): OkHttpClient {
        val b = base.newBuilder().addNetworkInterceptor(AuthInterceptor(server))
        if (server.type.auth == AuthKind.QBITTORRENT) b.cookieJar(jar)
        val pin = server.certSha256
        val host = server.httpUrl?.host
        if (pin.isNotBlank() && host != null) {
            val tm = Tls.PinnedTrustManager(pin) {}
            b.sslSocketFactory(Tls.sslContext(tm).socketFactory, tm)
            b.hostnameVerifier(Tls.pinnedHostnameVerifier(host, pin))
        }
        return b.build()
    }
}

/**
 * Adds the server's credentials, but only to requests for that server's own host. This runs for every
 * hop of a redirect, so an API key never follows a redirect to another site, and never drops from
 * https to plain http. A redirect that leaves the server can't carry anything else either: a sign-in
 * form (qBittorrent password), an uploaded .torrent file or any other change is refused instead of being
 * re-sent (OkHttp re-sends the body on 307/308 redirects).
 */
private class AuthInterceptor(private val server: Server) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val base = server.httpUrl ?: return chain.proceed(req)
        if (!sameServer(base, req.url)) {
            if (req.method != "GET" && req.method != "HEAD") {
                throw RedirectBlockedException()
            }
            return chain.proceed(req)
        }
        val key = server.apiKey
        if (key.isBlank()) return chain.proceed(req)
        return chain.proceed(req.newBuilder().header(server.type.authHeader, server.type.authValue(key)).build())
    }
}

/**
 * True when [url] is the server at [base]: the same host and port, or an http -> https upgrade on the same
 * host (reverse proxies do this). Never https -> http.
 */
private fun sameServer(base: HttpUrl, url: HttpUrl): Boolean {
    if (!url.host.equals(base.host, ignoreCase = true)) return false
    if (base.isHttps && !url.isHttps) return false
    return url.port == base.port || (!base.isHttps && url.isHttps)
}

/**
 * In-memory cookie store for one qBittorrent server. Cookies are only taken from and sent to that server's
 * own address (cookies alone don't care about the port or, without the Secure flag, about https).
 */
private class SessionJar(private val base: HttpUrl?) : CookieJar {
    private val cookies = ConcurrentHashMap<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (base == null || !sameServer(base, url)) return
        for (c in cookies) this.cookies[c.domain + "|" + c.path + "|" + c.name] = c
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (base == null || !sameServer(base, url)) return emptyList()
        val now = System.currentTimeMillis()
        cookies.entries.removeAll { it.value.expiresAt < now }
        return cookies.values.filter { it.matches(url) }
    }
}

/** A change was redirected to another address; it was stopped before anything was sent there. */
class RedirectBlockedException : IOException("redirect to another host blocked")
