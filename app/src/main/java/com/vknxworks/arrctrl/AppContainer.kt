package com.vknxworks.arrctrl

import android.content.Context
import com.vknxworks.arrctrl.data.api.ArrApi
import com.vknxworks.arrctrl.data.api.AutobrrApi
import com.vknxworks.arrctrl.data.api.BazarrApi
import com.vknxworks.arrctrl.data.api.CleanuparrApi
import com.vknxworks.arrctrl.data.api.ProwlarrApi
import com.vknxworks.arrctrl.data.api.QbitApi
import com.vknxworks.arrctrl.data.api.RadarrApi
import com.vknxworks.arrctrl.data.api.SonarrApi
import com.vknxworks.arrctrl.data.net.Http
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServerStatus
import com.vknxworks.arrctrl.data.server.ServerStore
import com.vknxworks.arrctrl.data.settings.SettingsRepository
import com.vknxworks.arrctrl.data.lock.LockVault
import com.vknxworks.arrctrl.data.notify.NotifyChecker
import com.vknxworks.arrctrl.tile.AltSpeedTileService
import com.vknxworks.arrctrl.ui.lock.AppLock
import com.vknxworks.arrctrl.widget.WidgetSnapshot
import com.vknxworks.arrctrl.widget.WidgetStore
import com.vknxworks.arrctrl.widget.Widgets
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, appScope)
    val http = Http()

    /** The app lock's PIN, fingerprint key and the key that seals API keys and passwords while locked. */
    val vault = LockVault(context, "arrctrl")

    val servers = ServerStore(context, appScope, vault).apply {
        // Background checks and push sign in while the app is locked, so with notifications on the
        // secrets stay encrypted with the Keystore only (as without the app lock).
        sealPolicy = { vault.enabled && settings.current().let { !it.notifyEnabled && !it.pushEnabled } }
    }
    val status = ServerStatus(http, appScope)

    /**
     * Background work (widgets, the Quick Settings tile) can't read the API keys and passwords right
     * now: they're sealed with the app lock, and ArrCtrl is locked.
     */
    suspend fun backgroundBlocked(): Boolean = vault.enabled && !vault.isOpen && servers.anySealed()

    /** Turns the app lock on with [pin]. */
    suspend fun enableLock(pin: String) {
        // Unlocked first, so the lock screen doesn't flash up while the PIN is being saved.
        AppLock.markUnlocked()
        withContext(Dispatchers.Default) { vault.setUp(pin) }
        runCatching { servers.applySealing() }
    }

    /** Turns the app lock off (after the PIN was confirmed): secrets go back to Keystore-only encryption. */
    suspend fun disableLock() {
        val sealed = servers.sealPolicy
        servers.sealPolicy = { false }
        try {
            servers.applySealing()
        } finally {
            servers.sealPolicy = sealed
        }
        vault.disable()
    }

    /** "Forgot PIN?": the sealed secrets can't be read any more, so remove every server and start over. */
    suspend fun forgetEverything() {
        servers.removeAll()
        http.forgetAll()
        withContext(Dispatchers.IO) {
            NotifyChecker.reset(context)
            runCatching { WidgetStore.save(context, WidgetSnapshot(noCalendarServers = true, noDownloadServers = true)) }
        }
        vault.disable()
        AppLock.markUnlocked()
        runCatching { Widgets.redraw(context) }
        AltSpeedTileService.refresh(context)
    }

    /** Locked: wipe the key from memory, and with sealed secrets drop them and every qBittorrent session too. */
    fun onLocked() {
        vault.close()
        servers.reload()
        http.forgetAll()
    }

    fun onUnlocked() {
        AppLock.markUnlocked()
        servers.reload()
        appScope.launch {
            // Notifications may have been turned on or off since the secrets were last sealed.
            runCatching { servers.applySealing() }
            if (Widgets.anyPlaced(context)) Widgets.refreshNow(context)
        }
        AltSpeedTileService.refresh(context)
    }

    init {
        AppLock.onLocked = { onLocked() }
        // Turning notifications on unseals the secrets (background checks sign in while locked), and
        // turning them off seals them again. Only possible while unlocked.
        appScope.launch {
            settings.changes.map { it.notifyEnabled || it.pushEnabled }.distinctUntilChanged().drop(1).collect {
                if (vault.isOpen) runCatching { servers.applySealing() }
            }
        }
    }

    fun arr(server: Server) = ArrApi(http, server)
    fun qbit(server: Server) = QbitApi(http, server)
    fun radarr(server: Server) = RadarrApi(http, server)
    fun sonarr(server: Server) = SonarrApi(http, server)
    fun prowlarr(server: Server) = ProwlarrApi(http, server)
    fun bazarr(server: Server) = BazarrApi(http, server)
    fun autobrr(server: Server) = AutobrrApi(http, server)
    fun cleanuparr(server: Server) = CleanuparrApi(http, server)

    /** A magnet link or .torrent file handed to the app (share sheet, magnet: link), waiting to be added. */
    val pendingTorrent = MutableStateFlow<IncomingTorrent?>(null)

    /** Where a tapped notification wants to go (see Alert.open), waiting for the UI. */
    val pendingOpen = MutableStateFlow<String?>(null)
}

/** What another app gave us to add: a magnet/URL, or a .torrent file's content URI. */
data class IncomingTorrent(val link: String? = null, val fileUri: String? = null)

val Context.container: AppContainer
    get() = (applicationContext as ArrCtrlApp).container
