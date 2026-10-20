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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** Manual dependency container; one per process. */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, appScope)
    val http = Http()
    val servers = ServerStore(context, appScope)
    val status = ServerStatus(http, appScope)

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
