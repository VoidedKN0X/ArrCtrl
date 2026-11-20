package com.vknxworks.arrctrl.data.api

import com.vknxworks.arrctrl.data.net.ConnectionTest
import com.vknxworks.arrctrl.data.net.TestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** A server answered with an HTTP error. */
class ApiException(val code: Int, message: String) : Exception(message)

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

/** Small helpers over OkHttp for JSON APIs. All run on the IO dispatcher. */
object Api {
    suspend fun json(client: OkHttpClient, request: Request): JsonElement? = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { r ->
            check(r)
            val text = r.body?.string().orEmpty()
            if (text.isBlank()) null else runCatching { ApiJson.parseToJsonElement(text) }.getOrNull()
        }
    }

    /**
     * [json] for the few answers that can be many megabytes (a whole movie, series or artist library): the
     * JSON is read from the connection as it arrives instead of first being kept as one long text. Only an
     * answer that is UTF-8 and starts with `[` or `{` is read this way; anything else (another character
     * set, a byte order mark, leading blanks, an empty or non-JSON answer) goes exactly the way [json] does.
     */
    suspend fun jsonLarge(client: OkHttpClient, request: Request): JsonElement? = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { r ->
            check(r)
            val body = r.body
            if (body == null) {
                null
            } else {
                val charset = body.contentType()?.charset()
                val source = body.source()
                // Looks at the first byte without taking it, so the ordinary way below still gets everything.
                val streamed = (charset == null || charset == Charsets.UTF_8) && source.request(1) &&
                    source.buffer[0L].let { it == '['.code.toByte() || it == '{'.code.toByte() }
                if (streamed) {
                    parseJsonStream(source.inputStream())
                } else {
                    val text = body.string()
                    if (text.isBlank()) null else runCatching { ApiJson.parseToJsonElement(text) }.getOrNull()
                }
            }
        }
    }

    suspend fun text(client: OkHttpClient, request: Request): String = withContext(Dispatchers.IO) {
        client.newCall(request).execute().use { r ->
            check(r)
            r.body?.string().orEmpty()
        }
    }

    suspend fun send(client: OkHttpClient, request: Request) {
        withContext(Dispatchers.IO) { client.newCall(request).execute().use { check(it) } }
    }

    fun get(url: String): Request = Request.Builder().url(url).header("Accept", "application/json").get().build()

    fun delete(url: String): Request = Request.Builder().url(url).delete().build()

    fun postJson(url: String, body: JsonObject): Request =
        Request.Builder().url(url).post(body.toString().toRequestBody(JSON_TYPE)).build()

    fun post(url: String, body: RequestBody): Request = Request.Builder().url(url).post(body).build()

    private fun check(r: Response) {
        if (!r.isSuccessful) throw ApiException(r.code, r.message.ifBlank { "HTTP ${r.code}" })
    }
}

/** Maps any failure to the same results the connection test uses, so errors read the same everywhere. */
fun Throwable.toTestResult(): TestResult = when (this) {
    is ApiException -> if (code == 401 || code == 403) TestResult.BadKey else TestResult.HttpError(code)
    else -> ConnectionTest.classify(this)
}
