package com.vknxworks.arrctrl.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Lenient readers for API responses. Every field is optional: a missing or oddly typed field reads as
 * null (or the given default), never as a crash, so new or older server versions keep working.
 */
val ApiJson = Json { isLenient = true; ignoreUnknownKeys = true; explicitNulls = false }

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
