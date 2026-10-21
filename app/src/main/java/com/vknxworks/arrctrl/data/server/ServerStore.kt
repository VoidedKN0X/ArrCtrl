package com.vknxworks.arrctrl.data.server

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.arrctrl.data.lock.LockVault
import com.vknxworks.arrctrl.data.settings.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * On-disk form of a [Server]. API key and password are AES-GCM encrypted with a Keystore key, and with
 * the app lock on (and notifications off) also sealed with the lock's vault key first.
 */
@Serializable
private data class StoredServer(
    val id: String,
    val type: String,
    val name: String,
    val url: String,
    val apiKeyEnc: String = "",
    val username: String = "",
    val passwordEnc: String = "",
    val certSha256: String = "",
)

/**
 * All configured servers, in the order they were added. While the app lock is locked, sealed API keys
 * and passwords read as empty: they can only be decrypted again after unlocking.
 */
class ServerStore(private val context: Context, scope: CoroutineScope, private val vault: LockVault) {
    private val key = stringPreferencesKey("servers_v1")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val serializer = ListSerializer(StoredServer.serializer())

    /** Whether secrets are sealed with the app lock's vault key when they're saved (set by AppContainer). */
    @Volatile
    var sealPolicy: suspend () -> Boolean = { false }

    @Volatile private var lastRaw: String? = null
    private val _servers = MutableStateFlow<List<Server>>(emptyList())
    val servers: StateFlow<List<Server>> = _servers.asStateFlow()

    init {
        scope.launch {
            context.appDataStore.data.map { it[key] }.distinctUntilChanged().collect { raw ->
                lastRaw = raw
                _servers.value = decode(raw)
            }
        }
    }

    /** Decodes the servers again: after unlocking (sealed secrets can be read) or locking (they can't). */
    fun reload() {
        _servers.value = decode(lastRaw)
    }

    /** Whether any saved secret is sealed with the app lock, so it can't be read while locked. */
    suspend fun anySealed(): Boolean {
        val raw = context.appDataStore.data.first()[key] ?: return false
        return hasSealed(raw)
    }

    private fun hasSealed(raw: String): Boolean =
        runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
            .any { s -> listOf(s.apiKeyEnc, s.passwordEnc).any { LockVault.isSealed(KeystoreCrypto.decrypt(it)) } }

    /** Seals or unseals every saved secret to match [sealPolicy]. Only while unlocked. */
    suspend fun applySealing() = edit { it }

    /** Removes every server and its secrets ("Forgot PIN?"). */
    suspend fun removeAll() {
        context.appDataStore.edit { it.remove(key) }
        lastRaw = null
        _servers.value = emptyList()
    }

    /** Reads the stored list directly; for background work, where [servers] may not have loaded yet. */
    suspend fun current(): List<Server> = decode(context.appDataStore.data.first()[key])

    fun byId(id: String): Server? = servers.value.firstOrNull { it.id == id }
    fun ofType(type: ServiceType): List<Server> = servers.value.filter { it.type == type }

    /** Adds [server], or replaces the one with the same id. */
    suspend fun save(server: Server) = edit { list ->
        val i = list.indexOfFirst { it.id == server.id }
        if (i >= 0) list.toMutableList().also { it[i] = server } else list + server
    }

    suspend fun remove(id: String) = edit { list -> list.filterNot { it.id == id } }

    /**
     * Adds imported servers. One that points at the same service and address as an existing
     * server replaces its credentials instead of adding a duplicate; its trusted certificate is kept
     * unless the import names another one.
     */
    suspend fun import(incoming: List<Server>) = edit { list ->
        val out = list.toMutableList()
        for (s in incoming) {
            val i = out.indexOfFirst { it.sameEndpoint(s) }
            if (i >= 0) out[i] = s.copy(id = out[i].id, certSha256 = s.certSha256.ifBlank { out[i].certSha256 }) else out += s
        }
        out
    }

    private suspend fun edit(transform: (List<Server>) -> List<Server>) {
        val seal = sealPolicy()
        context.appDataStore.edit { p ->
            // Saving while locked would write the sealed secrets back empty.
            if (!vault.isOpen && p[key]?.let(::hasSealed) == true) throw LockedException()
            val next = transform(decode(p[key]))
            p[key] = encode(next, seal)
        }
    }

    private fun decode(raw: String?): List<Server> {
        if (raw.isNullOrBlank()) return emptyList()
        val stored = runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
        return stored.mapNotNull { s ->
            val type = ServiceType.of(s.type) ?: return@mapNotNull null
            Server(
                id = s.id,
                type = type,
                name = s.name,
                url = s.url,
                apiKey = open(KeystoreCrypto.decrypt(s.apiKeyEnc)),
                username = s.username,
                password = open(KeystoreCrypto.decrypt(s.passwordEnc)),
                certSha256 = s.certSha256,
            )
        }
    }

    private fun encode(list: List<Server>, seal: Boolean): String = json.encodeToString(
        serializer,
        list.map { s ->
            StoredServer(
                id = s.id,
                type = s.type.id,
                name = s.name,
                url = s.url,
                apiKeyEnc = KeystoreCrypto.encrypt(sealIf(s.apiKey, seal)),
                username = s.username,
                passwordEnc = KeystoreCrypto.encrypt(sealIf(s.password, seal)),
                certSha256 = s.certSha256,
            )
        },
    )

    /** Empty while the secret is sealed and the app is locked. */
    private fun open(plain: String): String = if (LockVault.isSealed(plain)) vault.open(plain).orEmpty() else plain

    private fun sealIf(plain: String, on: Boolean): String = if (plain.isNotEmpty() && on) vault.seal(plain) ?: plain else plain
}

/** Saving was refused because the secrets are sealed and ArrCtrl is locked (it would write them back empty). */
class LockedException : IllegalStateException("locked")
