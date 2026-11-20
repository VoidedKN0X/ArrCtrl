package com.vknxworks.arrctrl.data.api

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.io.InputStream

/**
 * Lenient readers for API responses. Every field is optional: a missing or oddly typed field reads as
 * null (or the given default), never as a crash, so new or older server versions keep working.
 */
val ApiJson = Json { isLenient = true; ignoreUnknownKeys = true; explicitNulls = false }

/**
 * Reads one JSON value straight from a stream of UTF-8 bytes, without first holding the whole text in
 * memory. Gives null for anything that isn't JSON, like parsing the text would; a connection that breaks
 * while reading still fails with its own error.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun parseJsonStream(input: InputStream): JsonElement? =
    try {
        ApiJson.decodeFromStream<JsonElement>(input)
    } catch (_: IllegalArgumentException) {
        // What a JSON error is (SerializationException). An IOException is not caught here.
        null
    }

private fun JsonObject.prim(key: String): JsonPrimitive? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }

fun JsonObject?.str(key: String): String? = this?.prim(key)?.contentOrNull?.takeIf { it.isNotEmpty() }
fun JsonObject?.long(key: String): Long? = this?.prim(key)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() ?: it.contentOrNull?.toLongOrNull() }
fun JsonObject?.int(key: String): Int? = long(key)?.toInt()
fun JsonObject?.dbl(key: String): Double? = this?.prim(key)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
fun JsonObject?.bool(key: String): Boolean? = this?.prim(key)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }
fun JsonObject?.obj(key: String): JsonObject? = this?.get(key) as? JsonObject
fun JsonObject?.arr(key: String): JsonArray? = this?.get(key) as? JsonArray

fun JsonElement?.objects(): List<JsonObject> = (this as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
fun JsonArray?.objects(): List<JsonObject> = this?.mapNotNull { it as? JsonObject }.orEmpty()
fun JsonArray?.strings(): List<String> = this?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
