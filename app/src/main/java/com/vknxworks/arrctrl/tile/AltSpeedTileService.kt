package com.vknxworks.arrctrl.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.vknxworks.arrctrl.MainActivity
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.api.QbitApi
import com.vknxworks.arrctrl.data.notify.Notifications
import com.vknxworks.arrctrl.data.server.Server
import com.vknxworks.arrctrl.data.server.ServiceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Quick Settings tile for qBittorrent's alternative speed limits ("turtle mode").
 * Like the button on the Torrents tab, it covers every qBittorrent server: on when all of them are on;
 * a tap turns them all on, or all off when they already are.
 */
class AltSpeedTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    /** Each server's state; null when it couldn't be reached. */
    private suspend fun states(servers: List<Server>): List<Pair<Server, Boolean?>> = withContext(Dispatchers.IO) {
        coroutineScope {
            servers.map { s ->
                async {
                    s to runCatching {
                        val api = applicationContext.container.qbit(s)
                        val st = QbitApi.SyncState()
                        api.sync(st)
                        api.transfer(st).altSpeed
                    }.getOrNull()
                }
            }.awaitAll()
        }
    }

    private suspend fun qbits(): List<Server> = applicationContext.container.servers.current().filter { it.type == ServiceType.QBITTORRENT }

    private fun render(servers: List<Server>, states: List<Pair<Server, Boolean?>>?) {
        val tile = qsTile ?: return
        val known = states.orEmpty().mapNotNull { it.second }
        tile.label = getString(R.string.tile_alt_speed)
        when {
            servers.isEmpty() -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_no_server)
            }
            states == null -> {
                tile.subtitle = getString(R.string.tile_checking)
            }
            known.isEmpty() -> {
                tile.state = Tile.STATE_INACTIVE
                tile.subtitle = getString(R.string.tile_unreachable)
            }
            else -> {
                val on = known.all { it }
                tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.subtitle = when {
                    known.size < states.size -> getString(R.string.tile_some_unreachable)
                    on -> getString(R.string.tile_on)
                    known.any { it } -> getString(R.string.tile_mixed)
                    else -> getString(R.string.tile_off)
                }
            }
        }
        tile.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = scope.launch {
            if (applicationContext.container.backgroundBlocked()) {
                renderLocked()
                return@launch
            }
            val servers = qbits()
            render(servers, null)
            render(servers, states(servers))
        }
    }

    override fun onClick() {
        super.onClick()
        job?.cancel()
        job = scope.launch {
            // The app lock sealed the logins and ArrCtrl is locked: open it, to its lock screen.
            if (applicationContext.container.backgroundBlocked()) {
                openApp()
                return@launch
            }
            val servers = qbits()
            if (servers.isEmpty()) {
                openApp()
                return@launch
            }
            val before = states(servers)
            val reachable = before.filter { it.second != null }
            if (reachable.isEmpty()) {
                render(servers, before)
                return@launch
            }
            val target = !reachable.all { it.second == true }
            qsTile?.let { it.state = if (target) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; it.updateTile() }
            withContext(Dispatchers.IO) {
                reachable.filter { it.second != target }.forEach { (s, _) ->
                    runCatching { applicationContext.container.qbit(s).toggleAltSpeed() }
                }
            }
            render(servers, states(servers))
        }
    }

    private fun renderLocked() {
        val tile = qsTile ?: return
        tile.label = getString(R.string.tile_alt_speed)
        tile.state = Tile.STATE_INACTIVE
        tile.subtitle = getString(R.string.tile_locked)
        tile.updateTile()
    }

    /** No qBittorrent server yet, or ArrCtrl is locked: open ArrCtrl. */
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("arrctrl://open/activity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Notifications.EXTRA_OPEN, "activity")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** Asks the system to refresh the tile, e.g. after the Torrents tab toggled the limits. */
        fun refresh(context: Context) {
            runCatching { TileService.requestListeningState(context, ComponentName(context, AltSpeedTileService::class.java)) }
        }
    }
}
