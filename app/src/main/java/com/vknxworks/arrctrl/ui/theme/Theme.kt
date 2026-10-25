package com.vknxworks.arrctrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.data.settings.ThemeChoice

/**
 * Ctrl / Signal: the shared look of the Ctrl series. Dark only, near-black with one accent colour.
 * Change [Accent] to recolour the whole app.
 */
object CtrlColors {
    /** The one accent colour. Everything that is "on", selected or the main thing on screen uses it. */
    val Accent = Color(0xFFB48CFF)
    /** Text and icons drawn on the accent. */
    val OnAccent = Color(0xFF08080A)

    val Background = Color(0xFF08080A)
    /** Cards, sheets, dialogs, the bottom bar. */
    val Surface = Color(0xFF141417)
    /** Raised surfaces inside cards. */
    val Raised = Color(0xFF1C1C21)
    /** Card borders. */
    val CardBorder = Color(0xFF232328)
    /** Borders on controls (buttons, fields, the profile picture, the bottom bar). */
    val ControlBorder = Color(0xFF2A2A2F)
    /** Dividers between list rows. */
    val Divider = Color(0xFF1E1E23)
    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    /** Disabled, stopped and unmonitored things. */
    val Stopped = Color(0xFF8C8C94)
    val Error = Color(0xFFFF6B6B)
    /** Progress bar track. */
    val Track = Color(0xFF26262C)
    /** Switch track when off. */
    val SwitchOff = Color(0xFF2A2A30)
    /** Empty PIN cell border. */
    val PinEmpty = Color(0xFF3A3A41)
}

/** Kept for code (and the widgets) that refer to the accent by its old name. */
val ArrAccent = CtrlColors.Accent

internal val Dark = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.OnAccent,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.Text,
    onSecondary = CtrlColors.OnAccent,
    secondaryContainer = CtrlColors.Track,
    onSecondaryContainer = CtrlColors.Text,
    // "Done" states (downloaded, complete, healthy) are drawn in plain white, the accent stays for "active".
    tertiary = CtrlColors.Text,
    onTertiary = CtrlColors.OnAccent,
    tertiaryContainer = CtrlColors.Raised,
    onTertiaryContainer = CtrlColors.Text,
    background = CtrlColors.Background,
    onBackground = CtrlColors.Text,
    surface = CtrlColors.Background,
    onSurface = CtrlColors.Text,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    surfaceTint = Color.Transparent,
    surfaceBright = CtrlColors.Raised,
    surfaceDim = CtrlColors.Background,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = CtrlColors.Surface,
    surfaceContainer = CtrlColors.Surface,
    surfaceContainerHigh = CtrlColors.Raised,
    surfaceContainerHighest = CtrlColors.Track,
    inverseSurface = CtrlColors.Text,
    inverseOnSurface = CtrlColors.Background,
    outline = CtrlColors.ControlBorder,
    outlineVariant = CtrlColors.CardBorder,
    error = CtrlColors.Error,
    onError = CtrlColors.OnAccent,
    errorContainer = Color(0xFF2A1517),
    onErrorContainer = Color(0xFFFFB3B3),
    scrim = Color.Black,
)

/**
 * Home screen widgets: the same dark scheme, but with surface-coloured containers so the widget
 * background (which Glance takes from a container colour) stays near-black instead of accent.
 */
internal val WidgetScheme = Dark.copy(
    primaryContainer = CtrlColors.Surface,
    onPrimaryContainer = CtrlColors.Text,
    secondaryContainer = CtrlColors.Surface,
    onSecondaryContainer = CtrlColors.Text,
    tertiaryContainer = CtrlColors.Surface,
    surfaceVariant = CtrlColors.Track,
)

private fun spaceGroteskSemiBold() = Font(
    R.font.space_grotesk_variable,
    FontWeight.SemiBold,
    variationSettings = FontVariation.Settings(FontVariation.weight(600)),
)

/** Space Grotesk: all UI text and headings. */
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    spaceGroteskSemiBold(),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

/** JetBrains Mono: numbers, data readouts, section labels and the wordmark. */
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0, family: FontFamily = SpaceGrotesk) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

/** Styles outside the Material type scale. */
object CtrlType {
    /** Section labels: mono, upper case, wide tracking, muted. Upper-case the text yourself. */
    val Label = style(11, 14, FontWeight.Medium, 0.16, JetBrainsMono)
    /** Label on an accent card. */
    val LabelStrong = style(11, 14, FontWeight.SemiBold, 0.16, JetBrainsMono)
    /** "ARR/CTRL". */
    val Wordmark = style(13, 16, FontWeight.SemiBold, 0.14, JetBrainsMono)
    /** Small mono readouts (sizes, ETAs, addresses, statuses). */
    val Mono = style(12, 16, FontWeight.Normal, 0.0, JetBrainsMono)
    val MonoMedium = style(12, 16, FontWeight.Medium, 0.0, JetBrainsMono)
    /** Status next to a list row ("RUN", "SEED"). */
    val Status = style(11, 14, FontWeight.Medium, 0.1, JetBrainsMono)
    /** Big numbers on the hero card. */
    val HeroNumber = style(64, 60, FontWeight.Bold, -0.05)
    /** Mid-size numbers on secondary cards. */
    val CardNumber = style(30, 34, FontWeight.SemiBold, -0.03)
    /** PIN pad keys. */
    val Key = style(26, 30, FontWeight.Medium, 0.0, JetBrainsMono)
}

private val AppTypography = Typography(
    displayLarge = style(56, 56, FontWeight.Bold, -0.04),
    displayMedium = style(48, 50, FontWeight.Bold, -0.04),
    displaySmall = style(40, 44, FontWeight.Bold, -0.035),
    headlineLarge = style(40, 44, FontWeight.Bold, -0.035),
    headlineMedium = style(36, 40, FontWeight.Bold, -0.035),
    headlineSmall = style(26, 30, FontWeight.Bold, -0.025),
    titleLarge = style(18, 24, FontWeight.SemiBold, -0.005),
    titleMedium = style(16, 22, FontWeight.Medium),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 22, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 17, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium, 0.02),
)

/** Corner radii of the Signal look. */
object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Dialog = RoundedCornerShape(26.dp)
    val Sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val Control = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(16.dp)
    val Cta = RoundedCornerShape(18.dp)
    val Field = RoundedCornerShape(16.dp)
    val Poster = RoundedCornerShape(14.dp)
    val Badge = RoundedCornerShape(9.dp)
    val Chip = RoundedCornerShape(10.dp)
    /** Status dots: small squares with slightly rounded corners. */
    val Dot = RoundedCornerShape(2.dp)
    val Bar = RoundedCornerShape(22.dp)
    val NavItem = RoundedCornerShape(16.dp)
}

private val AppShapes = Shapes(
    extraSmall = CtrlShapes.Badge,
    small = CtrlShapes.Chip,
    medium = CtrlShapes.Control,
    large = CtrlShapes.Card,
    extraLarge = CtrlShapes.Dialog,
)

/** Horizontal page margin used everywhere. */
val Gutter = 20.dp

/**
 * ArrCtrl's theme. Signal is dark only: the old theme and wallpaper-colour settings are still read
 * (so nothing breaks) but no longer change anything.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ArrCtrlTheme(choice: ThemeChoice = ThemeChoice.DARK, dynamic: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Dark, typography = AppTypography, shapes = AppShapes, content = content)
}
