package com.vknxworks.arrctrl.data.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** One row of a quality profile: a single quality, or a group of qualities that count as one. */
data class QualityRow(
    /** Position in the profile's "items" list (lowest quality first, as the server keeps it). */
    val index: Int,
    val name: String,
    val allowed: Boolean,
    /** What "cutoff" holds when this row is the one to upgrade until: the quality's id, or the group's id. */
    val cutoffId: Int,
    /** Names of the qualities inside a group; empty for a single quality. */
    val members: List<String>,
)

/** A custom format's score in a quality profile. */
data class FormatScore(val formatId: Int, val name: String, val score: Int)

enum class ProfileProblem { NAME_BLANK, NOTHING_ALLOWED, CUTOFF_NOT_ALLOWED }

/**
 * Changes to quality profiles and custom formats, made on the JSON exactly as the server sent it
 * (QualityProfileResource and CustomFormatResource in Radarr's, Sonarr's and Lidarr's API), so every
 * field the app doesn't know goes back untouched. Nothing here talks to a server.
 */
object ProfileEdits {
    fun JsonObject.with(key: String, value: JsonElement): JsonObject = JsonObject(this + (key to value))
    fun JsonObject.with(key: String, value: String): JsonObject = with(key, JsonPrimitive(value))
    fun JsonObject.with(key: String, value: Boolean): JsonObject = with(key, JsonPrimitive(value))
    fun JsonObject.with(key: String, value: Int): JsonObject = with(key, JsonPrimitive(value))

    private fun JsonObject.withItemAt(key: String, index: Int, change: (JsonObject) -> JsonObject): JsonObject {
        val list = arr(key) ?: return this
        if (index !in list.indices) return this
        return with(key, JsonArray(list.mapIndexed { i, el -> if (i == index && el is JsonObject) change(el) else el }))
    }

    // ---------- Quality profiles ----------

    fun qualityRows(profile: JsonObject): List<QualityRow> = profile.arr("items").objects().mapIndexedNotNull { i, o ->
        val members = o.arr("items").objects()
        if (members.isNotEmpty()) {
            // A group: its own id and name; the qualities inside count as one.
            QualityRow(i, o.str("name") ?: members.mapNotNull { it.obj("quality").str("name") }.joinToString(" / "), o.bool("allowed") ?: false, o.int("id") ?: return@mapIndexedNotNull null, members.mapNotNull { it.obj("quality").str("name") })
        } else {
            val q = o.obj("quality")
            QualityRow(i, q.str("name") ?: return@mapIndexedNotNull null, o.bool("allowed") ?: false, q.int("id") ?: return@mapIndexedNotNull null, emptyList())
        }
    }

    /** Allows or disallows the row at [index]; the qualities inside a group follow the group. */
    fun setAllowed(profile: JsonObject, index: Int, allowed: Boolean): JsonObject = profile.withItemAt("items", index) { item ->
        val inner = item.arr("items")
        val changed = item.with("allowed", allowed)
        if (inner == null || inner.isEmpty()) changed
        else changed.with("items", JsonArray(inner.map { el -> if (el is JsonObject) el.with("allowed", allowed) else el }))
    }

    fun setCutoff(profile: JsonObject, cutoffId: Int): JsonObject = profile.with("cutoff", cutoffId)

    fun formatScores(profile: JsonObject): List<FormatScore> = profile.arr("formatItems").objects().mapNotNull { o ->
        FormatScore(o.int("format") ?: return@mapNotNull null, o.str("name").orEmpty(), o.int("score") ?: 0)
    }

    fun setFormatScore(profile: JsonObject, formatId: Int, score: Int): JsonObject {
        val list = profile.arr("formatItems") ?: return profile
        return profile.with("formatItems", JsonArray(list.map { el -> if (el is JsonObject && el.int("format") == formatId) el.with("score", score) else el }))
    }

    /** What the server would refuse, checked before sending. Null when the profile can be saved. */
    fun problem(profile: JsonObject): ProfileProblem? {
        val rows = qualityRows(profile)
        return when {
            profile.str("name").isNullOrBlank() -> ProfileProblem.NAME_BLANK
            rows.none { it.allowed } -> ProfileProblem.NOTHING_ALLOWED
            rows.none { it.allowed && it.cutoffId == profile.int("cutoff") } -> ProfileProblem.CUTOFF_NOT_ALLOWED
            else -> null
        }
    }

    // ---------- Custom formats ----------

    /** An empty custom format, for "New custom format". */
    fun newFormat(): JsonObject = JsonObject(
        mapOf("name" to JsonPrimitive(""), "includeCustomFormatWhenRenaming" to JsonPrimitive(false), "specifications" to JsonArray(emptyList())),
    )

    fun specs(format: JsonObject): List<JsonObject> = format.arr("specifications").objects()

    fun setSpec(format: JsonObject, index: Int, change: (JsonObject) -> JsonObject): JsonObject = format.withItemAt("specifications", index, change)

    fun removeSpec(format: JsonObject, index: Int): JsonObject {
        val list = format.arr("specifications") ?: return format
        return format.with("specifications", JsonArray(list.filterIndexed { i, _ -> i != index }))
    }

    /** Adds a condition from the server's schema list (GET customformat/schema), named after its kind until renamed. */
    fun addSpec(format: JsonObject, schema: JsonObject): JsonObject {
        val spec = JsonObject(schema.filterKeys { it != "presets" && it != "id" })
            .with("name", schema.str("name") ?: schema.str("implementationName") ?: schema.str("implementation").orEmpty())
        return format.with("specifications", JsonArray((format.arr("specifications") ?: JsonArray(emptyList())) + spec))
    }

    fun fields(spec: JsonObject): List<JsonObject> = spec.arr("fields").objects()

    fun setField(spec: JsonObject, fieldName: String, value: JsonElement): JsonObject {
        val list = spec.arr("fields") ?: return spec
        return spec.with("fields", JsonArray(list.map { el -> if (el is JsonObject && el.str("name") == fieldName) el.with("value", value) else el }))
    }

    /** A typed number for a "number" field: whole numbers stay whole unless the field is a decimal one. Null for text that isn't a number. */
    fun number(text: String, isFloat: Boolean): JsonPrimitive? {
        val t = text.trim().replace(',', '.')
        if (t.isEmpty()) return null
        if (!isFloat) t.toLongOrNull()?.let { return JsonPrimitive(it) }
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { JsonPrimitive(it) }
    }
}
