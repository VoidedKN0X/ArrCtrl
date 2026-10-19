package com.vknxworks.arrctrl.data.server

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.arrctrl.data.settings.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** On-disk form of a [Server]. API key and password are AES-GCM encrypted with a Keystore key. */
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

/** All configured servers, in the order they were added. */
class ServerStore(private val context: Context, scope: CoroutineScope) {
    private val key = stringPreferencesKey("servers_v1")
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val serializer = ListSerializer(StoredServer.serializer())

    val servers: StateFlow<List<Server>> = context.appDataStore.data
        .map { decode(it[key]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

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
        context.appDataStore.edit { p ->
            val next = transform(decode(p[key]))
            p[key] = encode(next)
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
                apiKey = KeystoreCrypto.decrypt(s.apiKeyEnc),
                username = s.username,
                password = KeystoreCrypto.decrypt(s.passwordEnc),
                certSha256 = s.certSha256,
            )
        }
    }

    private fun encode(list: List<Server>): String = json.encodeToString(
        serializer,
        list.map { s ->
            StoredServer(
                id = s.id,
                type = s.type.id,
                name = s.name,
                url = s.url,
                apiKeyEnc = KeystoreCrypto.encrypt(s.apiKey),
                username = s.username,
                passwordEnc = KeystoreCrypto.encrypt(s.password),
                certSha256 = s.certSha256,
            )
        },
    )
}
