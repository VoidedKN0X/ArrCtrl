package com.vknxworks.arrctrl

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.IntentCompat
import androidx.compose.runtime.LaunchedEffect
import com.vknxworks.arrctrl.ui.components.ScreenPrivacy
import com.vknxworks.arrctrl.ui.lock.AppLockGate
import com.vknxworks.arrctrl.ui.lock.lockCovering
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.data.notify.Notifications
import com.vknxworks.arrctrl.ui.activity.extractTorrentLinks
import com.vknxworks.arrctrl.ui.activity.isForeignContentUri
import com.vknxworks.arrctrl.ui.nav.AppRoot
import com.vknxworks.arrctrl.ui.links.DeepLink
import com.vknxworks.arrctrl.ui.theme.ArrCtrlTheme

/** Status and navigation bar colours: the Signal backgrounds, dark (#08080A) and light (#F5F5F1). */
private const val BAR_DARK = 0xFF08080A.toInt()
private const val BAR_LIGHT = 0xFFF5F5F1.toInt()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // A blank Recents card until the settings have been read ("Hide in recent apps" below).
        setRecentsScreenshotEnabled(false)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            // Signal follows the phone's dark theme: bars in the background colour, with light icons in
            // dark mode and dark icons in light mode.
            val dark = isSystemInDarkTheme()
            DisposableEffect(dark) {
                val bars = if (dark) SystemBarStyle.dark(BAR_DARK) else SystemBarStyle.light(BAR_LIGHT, BAR_DARK)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose { }
            }
            // "Hide in screenshots": FLAG_SECURE (also blanks the Recents preview). Always on, whatever the
            // switch says, while an API key, password or pasted server settings, the lock screen or a PIN screen are on screen.
            val secure = settings.hideScreenshots || ScreenPrivacy.forced > 0 || lockCovering(c.vault)
            // "Hide in recent apps": a blank Recents card; screenshots still work. Always with the app lock on,
            // so Recents never shows what's behind the lock.
            val blankRecents = settings.hideRecents || c.vault.enabled
            LaunchedEffect(secure, blankRecents) {
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                setRecentsScreenshotEnabled(!blankRecents)
            }
            ArrCtrlTheme(settings.theme, settings.dynamicColor) {
                // Nothing of the app is shown (or kept on screen) while the app lock is locked.
                AppLockGate { AppRoot() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Magnet links (opened or shared) and .torrent files (opened or shared) go to the Add torrent screen.
     * Shared text without a magnet or link is ignored, and so are file:// paths and ArrCtrl's own
     * content URIs (another app must not get ArrCtrl to read its own private files).
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(Notifications.EXTRA_OPEN)?.let { open ->
            container.pendingOpen.value = open
            return
        }
        if (intent.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES") {
            container.pendingOpen.value = "activity"
            return
        }
        // arrctrl:// links: checked here, opened by the UI only after the app lock (AppRoot isn't shown while locked).
        if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme.equals(DeepLink.SCHEME, ignoreCase = true)) {
            DeepLink.parse(intent.data)?.let { container.pendingOpen.value = it }
            return
        }
        val incoming = when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                when (uri.scheme?.lowercase()) {
                    "magnet" -> IncomingTorrent(link = uri.toString())
                    "content" -> if (isForeignContentUri(this, uri)) IncomingTorrent(fileUri = uri.toString()) else null
                    else -> null
                }
            }
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> if (isForeignContentUri(this, stream)) IncomingTorrent(fileUri = stream.toString()) else null
                    text != null && extractTorrentLinks(text).isNotEmpty() -> IncomingTorrent(link = extractTorrentLinks(text).joinToString("\n"))
                    else -> null
                }
            }
            else -> null
        } ?: return
        container.pendingTorrent.value = incoming
    }
}
