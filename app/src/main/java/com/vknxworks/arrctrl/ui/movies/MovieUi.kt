package com.vknxworks.arrctrl.ui.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.vknxworks.arrctrl.ui.theme.CtrlColors
import com.vknxworks.arrctrl.ui.theme.CtrlShapes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.ui.components.Chip

/** Poster from the server's own image cache; the title shows underneath while it loads or if there's none. */
@Composable
fun Poster(url: String?, title: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(CtrlShapes.Poster)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.CardBorder, CtrlShapes.Poster),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(8.dp),
        )
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
fun badgeLabel(b: MovieBadge): String = stringResource(
    when (b) {
        MovieBadge.DOWNLOADING -> R.string.badge_downloading
        MovieBadge.DOWNLOADED -> R.string.badge_downloaded
        MovieBadge.MISSING -> R.string.badge_missing
        MovieBadge.IN_CINEMAS -> R.string.badge_in_cinemas
        MovieBadge.ANNOUNCED -> R.string.badge_announced
    },
)

@Composable
fun BadgeChip(b: MovieBadge) {
    val (bg, fg) = when (b) {
        MovieBadge.DOWNLOADING -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        MovieBadge.DOWNLOADED -> CtrlColors.Text to CtrlColors.OnAccent
        MovieBadge.MISSING -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        MovieBadge.IN_CINEMAS -> CtrlColors.Raised to CtrlColors.Text
        MovieBadge.ANNOUNCED -> CtrlColors.Raised to CtrlColors.Muted
    }
    Chip(badgeLabel(b), bg, fg)
}

/** Scrim colour behind text drawn on top of artwork. */
val Scrim = Color.Black.copy(alpha = 0.55f)
