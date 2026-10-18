package com.vknxworks.arrctrl.ui.activity

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.vknxworks.arrctrl.AppContainer
import com.vknxworks.arrctrl.data.api.HistoryItem
import com.vknxworks.arrctrl.data.api.QbitApi
import com.vknxworks.arrctrl.data.api.QueueItem
import com.vknxworks.arrctrl.data.api.Torrent
import com.vknxworks.arrctrl.data.api.Transfer
import com.vknxworks.arrctrl.data.api.toTestResult
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Everything on the Activity tab, merged across every Radarr, Sonarr and qBittorrent server.
 * A server that fails only adds an entry to the matching error map; the others keep working.
 */
class ActivityModel(private val c: AppContainer) : ViewModel() {
    private fun servers(vararg types: ServiceType): List<Server> = c.servers.servers.value.filter { it.type in types }

    val arrServers: List<Server> get() = servers(ServiceType.RADARR, ServiceType.SONARR)
    val qbitServers: List<Server> get() = servers(ServiceType.QBITTORRENT)

    // ---------- Queue ----------

    var queue by mutableStateOf<List<QueueItem>>(emptyList()); private set
    var queueErrors by mutableStateOf<Map<Server, TestResult>>(emptyMap()); private set
    var queueLoaded by mutableStateOf(false); private set

    suspend fun refreshQueue() = coroutineScope {
        val results = arrServers.map { s -> async { s to runCatching { c.arr(s).queue() } } }.awaitAll()
        results.forEach { (_, r) -> r.exceptionOrNull()?.let { if (it is CancellationException) throw it } }
        queue = results.flatMap { (_, r) -> r.getOrNull().orEmpty() }
            .sortedWith(compareByDescending<QueueItem> { it.hasWarning }.thenBy { it.etaSeconds ?: Long.MAX_VALUE }.thenBy { it.title.lowercase() })
        queueErrors = results.mapNotNull { (s, r) -> r.exceptionOrNull()?.let { s to it.toTestResult() } }.toMap()
        queueLoaded = true
    }

    // ---------- Torrents ----------

    private val syncLock = Mutex()
    private val sync = mutableMapOf<String, QbitApi.SyncState>()

    var torrents by mutableStateOf<List<Torrent>>(emptyList()); private set
    var torrentErrors by mutableStateOf<Map<Server, TestResult>>(emptyMap()); private set
    var torrentsLoaded by mutableStateOf(false); private set
    /** Speeds added up over every qBittorrent server; alternative limits on when any server has them on. */
    var transfer by mutableStateOf<Transfer?>(null); private set
    var categories by mutableStateOf<Map<String, List<String>>>(emptyMap()); private set

    suspend fun refreshTorrents() = syncLock.withLock {
        coroutineScope {
            val list = qbitServers
            sync.keys.retainAll(list.map { it.id + it.url }.toSet())
            val results = list.map { s ->
                async {
                    val state = sync.getOrPut(s.id + s.url) { QbitApi.SyncState() }
                    s to runCatching { c.qbit(s).also { it.sync(state) } to state }
                }
            }.awaitAll()
            results.forEach { (_, r) -> r.exceptionOrNull()?.let { if (it is CancellationException) throw it } }
            val ok = results.mapNotNull { (s, r) -> r.getOrNull()?.let { s to it } }
            torrents = ok.flatMap { (_, p) -> p.first.torrents(p.second) }
                .sortedWith(compareByDescending<Torrent> { it.addedOn }.thenBy { it.name.lowercase() })
            transfer = ok.map { (_, p) -> p.first.transfer(p.second) }.takeIf { it.isNotEmpty() }?.let { all ->
                Transfer(
                    dlSpeed = all.sumOf { it.dlSpeed },
                    upSpeed = all.sumOf { it.upSpeed },
                    altSpeed = all.any { it.altSpeed },
                    freeSpace = all.mapNotNull { it.freeSpace }.minOrNull(),
                )
            }
            categories = ok.associate { (s, p) -> s.id to p.second.categories.toList() }
            torrentErrors = results.mapNotNull { (s, r) -> r.exceptionOrNull()?.let { s to it.toTestResult() } }.toMap()
            torrentsLoaded = true
        }
    }

    /** Turns alternative speed limits on (or off) everywhere, based on the combined state. */
    suspend fun toggleAltSpeed() {
        val target = transfer?.altSpeed != true
        syncLock.withLock {
            for (s in qbitServers) {
                val state = sync[s.id + s.url]
                val current = state?.let { c.qbit(s).transfer(it).altSpeed } ?: false
                if (current != target) c.qbit(s).toggleAltSpeed()
            }
        }
        refreshTorrents()
    }

    // ---------- History ----------

    private val historyPages = mutableMapOf<String, Int>()
    private val historyTotals = mutableMapOf<String, Int>()
    private var historyAll = listOf<HistoryItem>()

    var history by mutableStateOf<List<HistoryItem>>(emptyList()); private set
    var historyErrors by mutableStateOf<Map<Server, TestResult>>(emptyMap()); private set
    var historyLoading by mutableStateOf(false); private set
    var historyLoaded by mutableStateOf(false); private set
    var historyEnd by mutableStateOf(false); private set

    /** [reset] starts again from the newest events; otherwise loads the next page from every server. */
    suspend fun loadHistory(reset: Boolean) = coroutineScope {
        if (historyLoading) return@coroutineScope
        historyLoading = true
        try {
            if (reset) {
                historyPages.clear()
                historyTotals.clear()
                historyAll = emptyList()
            }
            val list = arrServers.filter { s ->
                val loaded = (historyPages[s.id] ?: 0) * PAGE
                val total = historyTotals[s.id]
                total == null || loaded < total
            }
            val results = list.map { s ->
                async { s to runCatching { c.arr(s).history((historyPages[s.id] ?: 0) + 1, PAGE) } }
            }.awaitAll()
            results.forEach { (_, r) -> r.exceptionOrNull()?.let { if (it is CancellationException) throw it } }
            results.forEach { (s, r) ->
                r.getOrNull()?.let { (items, total) ->
                    historyPages[s.id] = (historyPages[s.id] ?: 0) + 1
                    historyTotals[s.id] = total
                    historyAll = historyAll + items
                }
            }
            historyAll = historyAll.distinctBy { it.key }.sortedByDescending { it.date }
            history = historyAll
            historyErrors = results.mapNotNull { (s, r) -> r.exceptionOrNull()?.let { s to it.toTestResult() } }.toMap()
            historyEnd = arrServers.all { s -> (historyTotals[s.id] ?: 0) <= (historyPages[s.id] ?: 0) * PAGE }
            historyLoaded = true
        } finally {
            historyLoading = false
        }
    }

    companion object {
        private const val PAGE = 50
    }
}
