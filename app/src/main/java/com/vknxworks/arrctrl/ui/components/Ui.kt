package com.vknxworks.arrctrl.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.ui.theme.CtrlColors
import com.vknxworks.arrctrl.ui.theme.CtrlShapes
import com.vknxworks.arrctrl.ui.theme.Gutter

// ---------- Top bars ----------

/** Top bar for pushed screens: square back button and a title (see [CtrlDetailTopBar]). */
@Composable
fun ScreenTopBar(
    title: String,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CtrlDetailTopBar(title, showBack = showBack, actions = actions)
}

/**
 * Main tabs: the ARR/CTRL wordmark with [trailing] actions and the profile picture, then the tab's
 * name as a big headline.
 */
@Composable
fun TabTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        CtrlTopBar(actions = trailing)
        Text(
            title,
            style = MaterialTheme.typography.headlineLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 10.dp),
        )
    }
}

/** The profile picture; tapping it opens the You page with servers, settings and About (there is no settings tab). */
@Composable
fun ProfileButton() {
    CtrlAvatar()
}

// ---------- Grouped lists (settings, account, More) ----------

/** A card of related rows separated by hairlines, with an optional mono label above it. */
@Composable
fun Group(
    title: String? = null,
    modifier: Modifier = Modifier,
    footer: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
        if (title != null) {
            CtrlLabel(title, Modifier.padding(start = 4.dp, bottom = 8.dp))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(CtrlShapes.Card)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.CardBorder, CtrlShapes.Card),
            content = content,
        )
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
fun GroupRow(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        Modifier
            .fillMaxWidth()
            .topDivider(CtrlColors.CardBorder)
            .heightIn(min = 56.dp)
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                Modifier.size(36.dp).clip(CtrlShapes.Chip).background(CtrlColors.Raised),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(20.dp), tint = iconTint.copy(alpha = alpha)) }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor.copy(alpha = alpha), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (showChevron && enabled) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GroupSwitch(title: String, subtitle: String? = null, checked: Boolean, icon: ImageVector? = null, onChange: (Boolean) -> Unit) {
    GroupRow(title, subtitle, icon, onClick = { onChange(!checked) }) {
        CtrlSwitch(checked, onCheckedChange = onChange)
    }
}

/** A row showing the current value; tapping opens a sheet to pick another. */
@Composable
fun <T> GroupChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    icon: ImageVector? = null,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second.orEmpty()
    GroupRow(title, current, icon, showChevron = true, onClick = { open = true })
    if (open) {
        ChoiceSheet(title, options, selected, onDismiss = { open = false }) { onPick(it); open = false }
    }
}

@Composable
fun <T> ChoiceSheet(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onDismiss: () -> Unit,
    onPick: (T) -> Unit,
) {
    CtrlSheet(onDismissRequest = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.navigationBarsPadding(), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(options, key = { it.second }) { (value, label) ->
                val isSel = value == selected
                Row(
                    Modifier.fillMaxWidth().topDivider().clickable { onPick(value) }.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSel) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

// ---------- Empty and error states ----------

@Composable
fun MessageView(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CtrlShapes.Hero)
                    .background(CtrlColors.Surface)
                    .border(1.dp, CtrlColors.CardBorder, CtrlShapes.Hero),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            if (body != null) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                CtrlPrimaryButton(actionLabel, onAction)
            }
        }
    }
}

// ---------- Privacy ----------

/**
 * Screens that always get FLAG_SECURE, whatever "Hide in screenshots and recent apps" says (MainActivity
 * applies it). A count rather than a flag, so two such screens overlapping during a transition can't
 * clear it early.
 */
object ScreenPrivacy {
    var forced by androidx.compose.runtime.mutableIntStateOf(0)
        private set

    internal fun acquire() { forced++ }
    internal fun release() { forced = (forced - 1).coerceAtLeast(0) }
}

/**
 * Shields a window while a PIN, lock, secret or confirmation screen shows on it: other apps' floating windows
 * (overlays such as chat heads) are hidden over it, so they can't cover or imitate it. [on] = false lifts it
 * again, so overlays keep working everywhere else in the app.
 *
 * [hideFromAccessibility] (lock and PIN screens only) also hides the content, on Android 14+, from apps that
 * misuse accessibility access; real accessibility tools such as TalkBack keep working. It stays off for screens
 * with API keys and passwords, so password managers that fill through accessibility keep working there.
 */
fun android.view.Window.shield(on: Boolean, hideFromAccessibility: Boolean = false) {
    runCatching { setHideOverlayWindows(on) }
    runCatching {
        decorView.setAccessibilityDataSensitive(
            if (on && hideFromAccessibility) android.view.View.ACCESSIBILITY_DATA_SENSITIVE_YES
            else android.view.View.ACCESSIBILITY_DATA_SENSITIVE_AUTO,
        )
    }
}

/** Shields the dialog it's called from (a destructive or security confirmation) for as long as it shows. */
@Composable
fun ShieldDialogWindow() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        window?.shield(true)
        onDispose { window?.shield(false) }
    }
}

/**
 * Blocks screenshots, screen recording and casting while it's on screen. For screens that show API keys,
 * passwords or pasted server settings.
 */
@Composable
fun SecureWindow() {
    androidx.compose.runtime.DisposableEffect(Unit) {
        ScreenPrivacy.acquire()
        onDispose { ScreenPrivacy.release() }
    }
}
