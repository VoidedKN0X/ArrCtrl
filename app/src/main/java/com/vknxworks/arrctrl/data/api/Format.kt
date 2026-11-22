package com.vknxworks.arrctrl.data.api

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Locale

/** Number formatting shared by the screens. Units are binary (KiB = 1024) but labelled the familiar way. */
object Format {
    fun bytes(n: Long?): String {
        val v = n ?: return "–"
        if (v < 1024) return "$v B"
        val units = listOf("KB", "MB", "GB", "TB", "PB")
        var d = v.toDouble() / 1024
        var i = 0
        while (d >= 1024 && i < units.lastIndex) { d /= 1024; i++ }
        return String.format(Locale.getDefault(), if (d >= 100) "%.0f %s" else "%.1f %s", d, units[i])
    }

    fun speed(bytesPerSecond: Long?): String = if ((bytesPerSecond ?: 0) <= 0) "0 B/s" else bytes(bytesPerSecond) + "/s"

    /**
     * Seconds → "2h 5m", "4m 10s" in English, with the phone's own short units in other languages (ICU's
     * narrow format); null for unknown or effectively infinite (qBittorrent uses 8640000).
     */
    fun eta(seconds: Long?): String? {
        val s = seconds ?: return null
        if (s < 0 || s >= 8_640_000) return null
        val d = s / 86_400; val h = (s % 86_400) / 3600; val m = (s % 3600) / 60; val sec = s % 60
        val measures = when {
            d > 0 -> arrayOf(Measure(d, MeasureUnit.DAY), Measure(h, MeasureUnit.HOUR))
            h > 0 -> arrayOf(Measure(h, MeasureUnit.HOUR), Measure(m, MeasureUnit.MINUTE))
            m > 0 -> arrayOf(Measure(m, MeasureUnit.MINUTE), Measure(sec, MeasureUnit.SECOND))
            else -> arrayOf(Measure(sec, MeasureUnit.SECOND))
        }
        return narrow(measures)
    }

    /**
     * One ICU [MeasureFormat] for the current language, made again only when the language changes: making
     * one loads locale data, too slow for every torrent row on every 2-second poll. ICU formats aren't
     * thread-safe, so use is serialised.
     */
    private var narrowCache: Pair<Locale, MeasureFormat>? = null

    @Synchronized
    private fun narrow(measures: Array<Measure>): String {
        val locale = Locale.getDefault()
        val f = narrowCache?.takeIf { it.first == locale }?.second
            ?: MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.NARROW).also { narrowCache = locale to it }
        return f.formatMeasures(*measures)
    }

    /** Radarr/Sonarr "timeleft" ("1.02:03:04" or "02:03:04") → seconds. */
    fun arrTimeLeft(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val (days, rest) = if (raw.contains('.') && raw.indexOf('.') < raw.indexOf(':')) {
            raw.substringBefore('.').toLongOrNull() to raw.substringAfter('.')
        } else 0L to raw
        val parts = rest.substringBefore('.').split(':').mapNotNull { it.toLongOrNull() }
        if (parts.size != 3) return null
        return ((days ?: 0) * 86_400) + parts[0] * 3600 + parts[1] * 60 + parts[2]
    }

    fun ratio(r: Double?): String = if (r == null || r < 0) "–" else String.format(Locale.getDefault(), "%.2f", r)

    fun percent(p: Double?): String = String.format(Locale.getDefault(), "%.0f%%", ((p ?: 0.0) * 100).coerceIn(0.0, 100.0))

    fun instant(iso: String?): Instant? = iso?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() ?: runCatching { Instant.parse(it) }.getOrNull() }

    /** Sonarr episode code: S01E02. */
    fun episode(season: Int?, episode: Int?): String? =
        if (season == null || episode == null) null else String.format(Locale.ROOT, "S%02dE%02d", season, episode)

    fun ago(from: Instant?, now: Instant = Instant.now()): Duration? = from?.let { Duration.between(it, now) }
}
