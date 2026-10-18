package com.vknxworks.arrctrl

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.content.IntentCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.data.settings.ThemeChoice
import com.vknxworks.arrctrl.ui.activity.extractTorrentLinks
import com.vknxworks.arrctrl.ui.nav.AppRoot
import com.vknxworks.arrctrl.ui.theme.ArrCtrlTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val c = container
        setContent {
            val settings by c.settings.settings.collectAsStateWithLifecycle()
            val dark = when (settings.theme) {
                ThemeChoice.SYSTEM -> isSystemInDarkTheme()
                ThemeChoice.LIGHT -> false
                ThemeChoice.DARK, ThemeChoice.BLACK -> true
            }
            // Keep status and navigation bar icons readable when the app theme differs from the system's.
            DisposableEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            ArrCtrlTheme(settings.theme, settings.dynamicColor) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Magnet links (opened or shared) and .torrent files (opened or shared) go to the Add torrent screen.
     * Shared text without a magnet or link is ignored.
     */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        val incoming = when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                when (uri.scheme?.lowercase()) {
                    "magnet" -> IncomingTorrent(link = uri.toString())
                    "content", "file" -> IncomingTorrent(fileUri = uri.toString())
                    else -> null
                }
            }
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> IncomingTorrent(fileUri = stream.toString())
                    text != null && extractTorrentLinks(text).isNotEmpty() -> IncomingTorrent(link = extractTorrentLinks(text).joinToString("\n"))
                    else -> null
                }
            }
            else -> null
        } ?: return
        container.pendingTorrent.value = incoming
    }
}
