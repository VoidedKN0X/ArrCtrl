package com.vknxworks.arrctrl.data.notify

import android.content.Context
import com.vknxworks.arrctrl.data.crypto.DataKeyStore
import com.vknxworks.arrctrl.data.crypto.SealedDataException
import com.vknxworks.arrctrl.data.crypto.SealedFile
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * What the notification checks saw on each server last time ([NotifyChecker.Seen]): torrent hashes, download
 * ids, health messages and disk paths. Kept in one file, `notify_seen.json`, encrypted at rest with the app's
 * data key ([SealedFile]). Without a usable key it's written as an ordinary file, and both kinds are read.
 *
 * Earlier versions kept the same data as `seen_<server id>` entries in the `notify_state` preferences. Those
 * are read once, and only removed after the file holding them was written, read back and compared.
 *
 * This is a memory of things the servers can tell again, not something you typed in: if the file can't be
 * opened (the key is gone, or the file is damaged) it's deleted and every server starts from a fresh
 * starting point, exactly as when notifications are first turned on.
 *
 * Loading and saving ask the secure hardware for the key the first time, so they belong on a background
 * thread (the checks run in WorkManager). [clear] never does, and is safe on the main thread.
 */
class SeenStore(private val context: Context, private val keys: DataKeyStore) {
    private val file = File(context.filesDir, FILE)
    private val tmp = File(context.filesDir, "$FILE.tmp")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), NotifyChecker.Seen.serializer())
    private val lock = Any()

    /** Everything that's remembered, once it has been read in this run. */
    private var map: MutableMap<String, NotifyChecker.Seen>? = null

    /** The old preference entries may still be there; they go once the file is known to be good. */
    private var legacyPending = true

    private fun prefs() = context.getSharedPreferences(NotifyChecker.PREFS, Context.MODE_PRIVATE)

    /** What was seen on server [id] at the last check, or null when it was never checked. */
    fun load(id: String): NotifyChecker.Seen? {
        val key = key()
        return synchronized(lock) { all(key)[id] }
    }

    fun save(id: String, seen: NotifyChecker.Seen) {
        val key = key()
        synchronized(lock) {
            all(key)[id] = seen
            persist(key)
        }
    }

    /** Forgets every server that isn't in [ids] (servers that were removed). */
    fun retain(ids: Set<String>) {
        val key = key()
        synchronized(lock) {
            if (all(key).keys.retainAll(ids)) persist(key)
        }
    }

    /** Forgets everything, so every server starts from a fresh starting point. */
    fun clear() {
        synchronized(lock) {
            map = HashMap()
            try {
                file.delete()
                tmp.delete()
            } catch (ignored: Exception) {
            }
            dropLegacy()
            legacyPending = false
        }
    }

    /** The data key, made the first time. Null when the secure hardware can't give one: ordinary files then. */
    private fun key(): ByteArray? = try {
        keys.get(create = true)
    } catch (e: Exception) {
        null
    }

    private fun all(key: ByteArray?): MutableMap<String, NotifyChecker.Seen> {
        map?.let { return it }
        val m = readFile(key) ?: fromPrefs()
        map = m
        return m
    }

    /** The file's contents; null when there is no file yet. A file that can't be opened is deleted (see above). */
    private fun readFile(key: ByteArray?): MutableMap<String, NotifyChecker.Seen>? {
        if (!file.exists()) return null
        return try {
            val bytes = file.readBytes()
            val plain = if (SealedFile.isSealed(bytes)) {
                SealedFile.open(key ?: throw SealedDataException("no data key"), bytes)
            } else {
                bytes
            }
            HashMap(json.decodeFromString(serializer, String(plain, Charsets.UTF_8)))
        } catch (e: Exception) {
            try {
                file.delete()
            } catch (ignored: Exception) {
            }
            // Never fall back to the older copy in the preferences: it would report old events again.
            dropLegacy()
            legacyPending = false
            HashMap()
        }
    }

    /** What an earlier version (or a save that couldn't write the file) left in the preferences. */
    private fun fromPrefs(): MutableMap<String, NotifyChecker.Seen> {
        val out = HashMap<String, NotifyChecker.Seen>()
        try {
            for ((k, v) in prefs().all) {
                if (!k.startsWith(LEGACY_PREFIX) || v !is String) continue
                val seen = try {
                    json.decodeFromString(NotifyChecker.Seen.serializer(), v)
                } catch (e: Exception) {
                    null
                }
                if (seen != null) out[k.removePrefix(LEGACY_PREFIX)] = seen
            }
        } catch (e: Exception) {
        }
        return out
    }

    private fun dropLegacy() {
        try {
            val p = prefs()
            val old = p.all.keys.filter { it.startsWith(LEGACY_PREFIX) }
            if (old.isNotEmpty()) p.edit().apply { old.forEach { remove(it) } }.apply()
        } catch (e: Exception) {
        }
    }

    /**
     * Writes everything to the file in one step (temp file, synced, then renamed over the old one). The old
     * preference entries are removed only after the new file was read back and holds exactly what was written.
     * If the file can't be written, the data goes to the preferences as before, so nothing is forgotten.
     */
    private fun persist(key: ByteArray?) {
        val m = map ?: return
        val plain = try {
            json.encodeToString(serializer, m).toByteArray(Charsets.UTF_8)
        } catch (e: Exception) {
            return
        }
        try {
            val bytes = key?.let { SealedFile.sealChecked(it, plain) } ?: plain
            SealedFile.writeAtomic(file, tmp, bytes)
            if (legacyPending) {
                val back = file.readBytes()
                val same = if (SealedFile.isSealed(back)) {
                    key != null && SealedFile.open(key, back).contentEquals(plain)
                } else {
                    back.contentEquals(plain)
                }
                if (same) {
                    dropLegacy()
                    legacyPending = false
                }
            }
        } catch (e: Exception) {
            // The file is out of date now: remove it and keep the data in the preferences, as earlier versions did.
            try {
                file.delete()
                tmp.delete()
                val edit = prefs().edit()
                for ((id, seen) in m) edit.putString(LEGACY_PREFIX + id, json.encodeToString(NotifyChecker.Seen.serializer(), seen))
                edit.apply()
                legacyPending = true
            } catch (ignored: Exception) {
            }
        }
    }

    companion object {
        const val FILE = "notify_seen.json"
        private const val LEGACY_PREFIX = "seen_"
    }
}
