package com.vknxworks.arrctrl.data.net

import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException
import kotlin.coroutines.cancellation.CancellationException

/** What a connection test found. */
sealed interface TestResult {
    /** Connected and authenticated; [version] when the service reports one. */
    data class Ok(val version: String?) : TestResult

    /** Something answered, but it isn't the expected service. [found] names it when it says what it is. */
    data class WrongService(val found: String?) : TestResult

    /** The API key (or qBittorrent API key) was refused. */
    data object BadKey : TestResult

    /** qBittorrent refused the username or password. */
    data object BadLogin : TestResult

    /** qBittorrent has banned this IP after too many failed sign-ins. */
    data object Banned : TestResult

    /** The certificate isn't trusted. [changed] when it differs from one the user trusted before. */
    data class Untrusted(val changed: Boolean) : TestResult

    /** https failed for another reason (e.g. the port only speaks plain http). */
    data class TlsFailed(val detail: String) : TestResult

    data object HostNotFound : TestResult
    data object Refused : TestResult
    data object Timeout : TestResult
    data class Unreachable(val detail: String) : TestResult
    data class HttpError(val code: Int) : TestResult
}

/** Checks that a server is reachable, is the service it claims to be, and accepts the credentials. */
object ConnectionTest {
    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    suspend fun run(http: Http, server: Server, draft: Boolean): TestResult = withContext(Dispatchers.IO) {
        val client = if (draft) http.draftClient(server) else http.client(server)
        try {
            when (server.type) {
                ServiceType.RADARR, ServiceType.SONARR, ServiceType.PROWLARR -> arr(client, server)
                ServiceType.BAZARR -> bazarr(client, server)
                ServiceType.QBITTORRENT -> qbittorrent(client, server)
                ServiceType.AUTOBRR -> autobrr(client, server)
                ServiceType.CLEANUPARR -> cleanuparr(client, server)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            classify(e)
        }
    }

    // ---------- Per service ----------

    /** Radarr, Sonarr and Prowlarr: GET system/status, which names the app. */
    private fun arr(client: OkHttpClient, s: Server): TestResult = get(client, s.api("/system/status")) { r ->
        authFailure(r.code)?.let { return@get it }
        if (r.code == 404) return@get TestResult.WrongService(null)
        if (!r.isSuccessful) return@get TestResult.HttpError(r.code)
        val o = obj(r) ?: return@get TestResult.WrongService(null)
        val app = o.str("appName")
        val expected = s.type.id
        when {
            app.equals(expected, ignoreCase = true) -> TestResult.Ok(o.str("version"))
            app != null -> TestResult.WrongService(app)
            else -> TestResult.WrongService(null)
        }
    }

    /** Bazarr: GET /api/system/status → { data: { bazarr_version } }. */
    private fun bazarr(client: OkHttpClient, s: Server): TestResult = get(client, s.api("/system/status")) { r ->
        authFailure(r.code)?.let { return@get it }
        if (r.code == 404) return@get TestResult.WrongService(null)
        if (!r.isSuccessful) return@get TestResult.HttpError(r.code)
        val data = obj(r)?.get("data") as? JsonObject ?: return@get TestResult.WrongService(null)
        val v = data.str("bazarr_version") ?: return@get TestResult.WrongService(null)
        TestResult.Ok(v)
    }

    /** autobrr: GET /api/config, whose fields are specific to autobrr. */
    private fun autobrr(client: OkHttpClient, s: Server): TestResult = get(client, s.api("/config")) { r ->
        authFailure(r.code)?.let { return@get it }
        if (r.code == 404) return@get TestResult.WrongService(null)
        if (!r.isSuccessful) return@get TestResult.HttpError(r.code)
        val o = obj(r) ?: return@get TestResult.WrongService(null)
        if (o.containsKey("check_for_updates") || o.containsKey("log_max_backups")) TestResult.Ok(o.str("version"))
        else TestResult.WrongService(null)
    }

    /** Cleanuparr: GET /api/v2/stats (the v1 stats endpoint is deprecated). */
    private fun cleanuparr(client: OkHttpClient, s: Server): TestResult = get(client, s.api("/v2/stats?hours=1")) { r ->
        authFailure(r.code)?.let { return@get it }
        if (r.code == 404) return@get TestResult.WrongService(null)
        if (!r.isSuccessful) return@get TestResult.HttpError(r.code)
        val o = obj(r) ?: return@get TestResult.WrongService(null)
        val keys = o.keys.map { it.lowercase() }
        if ("events" in keys && "strikes" in keys) TestResult.Ok(null) else TestResult.WrongService(null)
    }

    /**
     * qBittorrent: with an API key, straight to app/version. Otherwise sign in first. Older versions
     * answer a failed sign-in with 200 "Fails.", newer ones with 401; 403 means this IP is banned.
     */
    private fun qbittorrent(client: OkHttpClient, s: Server): TestResult {
        if (s.usesLogin) {
            val form = FormBody.Builder().add("username", s.username).add("password", s.password).build()
            val login = Request.Builder().url(s.api("/auth/login")).post(form).build()
            val result: TestResult? = client.newCall(login).execute().use { r ->
                val body = r.body?.string().orEmpty().trim()
                when {
                    r.code == 403 -> TestResult.Banned
                    r.code == 401 -> TestResult.BadLogin
                    r.code == 404 -> TestResult.WrongService(null)
                    !r.isSuccessful -> TestResult.HttpError(r.code)
                    body.startsWith("Fails", ignoreCase = true) -> TestResult.BadLogin
                    body.isEmpty() || body.startsWith("Ok", ignoreCase = true) -> null
                    else -> TestResult.WrongService(null)
                }
            }
            if (result != null) return result
        }
        return get(client, s.api("/app/version")) { r ->
            if (r.code == 401 || r.code == 403) return@get if (s.usesLogin) TestResult.BadLogin else TestResult.BadKey
            if (r.code == 404) return@get TestResult.WrongService(null)
            if (!r.isSuccessful) return@get TestResult.HttpError(r.code)
            val v = r.body?.string().orEmpty().trim()
            if (VERSION.matches(v)) TestResult.Ok(v.removePrefix("v")) else TestResult.WrongService(null)
        }
    }

    private val VERSION = Regex("""^v?\d+(\.\d+)+.*$""")

    // ---------- Helpers ----------

    private fun authFailure(code: Int): TestResult? = if (code == 401 || code == 403) TestResult.BadKey else null

    private inline fun get(client: OkHttpClient, url: String, read: (Response) -> TestResult): TestResult {
        val req = Request.Builder().url(url).header("Accept", "application/json").get().build()
        return client.newCall(req).execute().use(read)
    }

    private fun obj(r: Response): JsonObject? {
        val text = r.body?.string() ?: return null
        return runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    fun classify(e: Throwable): TestResult {
        if (isCertError(e)) return TestResult.Untrusted(changed = causes(e).any { it is PinMismatchException })
        return when (e) {
            is UnknownHostException -> TestResult.HostNotFound
            is ConnectException, is NoRouteToHostException -> TestResult.Refused
            is SocketTimeoutException -> TestResult.Timeout
            is SSLException -> TestResult.TlsFailed(e.message.orEmpty())
            is IOException -> TestResult.Unreachable(e.message.orEmpty())
            else -> TestResult.Unreachable(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    /** True when a failure is about the certificate (untrusted, wrong name, pin changed), not about reaching the server. */
    fun isCertError(e: Throwable): Boolean = causes(e).any { t ->
        t is CertificateException || t is SSLPeerUnverifiedException ||
            t.message.orEmpty().let { it.contains("Trust anchor", ignoreCase = true) || it.contains("certificate", ignoreCase = true) }
    }

    private fun causes(e: Throwable): Sequence<Throwable> = generateSequence(e) { it.cause?.takeIf { c -> c !== it } }
}
