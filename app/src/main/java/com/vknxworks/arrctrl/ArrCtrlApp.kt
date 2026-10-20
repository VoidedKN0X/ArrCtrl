package com.vknxworks.arrctrl

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.vknxworks.arrctrl.data.net.ServerCallFactory
import com.vknxworks.arrctrl.data.notify.NotifySchedule
import com.vknxworks.arrctrl.data.notify.Notifications
import com.vknxworks.arrctrl.widget.Widgets
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ArrCtrlApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannels(this)
        // Widgets on the home screen: make sure their refresh is scheduled, and freshen them when the app starts.
        container.appScope.launch {
            if (Widgets.anyPlaced(this@ArrCtrlApp)) {
                Widgets.schedule(this@ArrCtrlApp)
                Widgets.refreshNow(this@ArrCtrlApp)
            }
        }
        // Keep background checks, ongoing updates and the registration with ntfy in line with the settings.
        container.appScope.launch {
            container.settings.changes
                .map { listOf(it.notifyEnabled, it.notifyInterval, it.notifyOngoing, it.pushEnabled) to it }
                .distinctUntilChanged { a, b -> a.first == b.first }
                .collect { (_, s) -> runCatching { NotifySchedule.apply(this@ArrCtrlApp, s) } }
        }
    }

    /** Posters and fanart come from your own servers, through the same clients (keys, pinned certificates) as everything else. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { ServerCallFactory(container.http, container.servers) })) }
            .build()
}
