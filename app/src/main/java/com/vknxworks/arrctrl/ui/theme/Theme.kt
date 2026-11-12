package com.vknxworks.arrctrl.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.data.settings.ThemeChoice

/** One Ctrl / Signal palette. [CtrlColors.Dark] and [CtrlColors.Light] are the two there are. */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    /** Accent used as text or an icon on the background (darker in light mode so it stays readable). */
    val accentText: Color,
    val background: Color,
    /** Cards, sheets, dialogs, the bottom bar. */
    val surface: Color,
    /** Raised surfaces inside cards. */
    val raised: Color,
    /** Card borders. */
    val cardBorder: Color,
    /** Borders on controls (buttons, fields, the profile picture, the bottom bar). */
    val controlBorder: Color,
    /** Dividers between list rows. */
    val divider: Color,
    val text: Color,
    val muted: Color,
    /** Disabled, stopped and unmonitored things. */
    val stopped: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    /** Progress bar track. */
    val track: Color,
    /** Switch track and knob when off. */
    val switchOff: Color,
    val switchOffKnob: Color,
    /** Empty PIN cell border. */
    val pinEmpty: Color,
)

/** The palette in use: dark or light, following the phone's dark theme setting. */
val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

/**
 * Ctrl / Signal: the shared look of the Ctrl series. Follows the phone's dark/light setting, with one
 * accent colour. Change [Accent] (and [Light]'s accentText) to recolour the whole app.
 *
 * The colour properties below read the current palette, so they're only available in composables.
 */
object CtrlColors {
    /** The one accent colour, as a fill: hero cards, main buttons, the selected tab, switches, PIN dots. */
    val Accent = Color(0xFFB48CFF)
    /** Text and icons drawn on the accent, in both modes. */
    val OnAccent = Color(0xFF08080A)

    val Dark = CtrlPalette(
        isDark = true,
        accentText = Accent,
        background = Color(0xFF08080A),
        surface = Color(0xFF141417),
        raised = Color(0xFF1C1C21),
        cardBorder = Color(0xFF232328),
        controlBorder = Color(0xFF2A2A2F),
        divider = Color(0xFF1E1E23),
        text = Color(0xFFF4F4F2),
        muted = Color(0xFFA3A3AB),
        stopped = Color(0xFF8C8C94),
        error = Color(0xFFFF6B6B),
        onError = Color(0xFF08080A),
        errorContainer = Color(0xFF2A1517),
        onErrorContainer = Color(0xFFFFB3B3),
        track = Color(0xFF26262C),
        switchOff = Color(0xFF2A2A30),
        switchOffKnob = Color(0xFF8C8C94),
        pinEmpty = Color(0xFF3A3A41),
    )

    /** "Signal Light". */
    val Light = CtrlPalette(
        isDark = false,
        accentText = Color(0xFF6A3BDB),
        background = Color(0xFFF5F5F1),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECE7),
        cardBorder = Color(0xFFDEDED7),
        controlBorder = Color(0xFFD2D2CA),
        divider = Color(0xFFE6E6E0),
        text = Color(0xFF0B0B0D),
        muted = Color(0xFF5C5C64),
        stopped = Color(0xFF74747C),
        error = Color(0xFFD93636),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFBE4E4),
        onErrorContainer = Color(0xFF7A1A1A),
        track = Color(0xFFE2E2DC),
        switchOff = Color(0xFFDCDCD5),
        switchOffKnob = Color(0xFF74747C),
        pinEmpty = Color(0xFFC9C9C1),
    )

    val current: CtrlPalette @Composable @ReadOnlyComposable get() = LocalCtrlColors.current

    val AccentText: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.accentText
    val Background: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.surface
    val Raised: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.raised
    val CardBorder: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.cardBorder
    val ControlBorder: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.controlBorder
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.divider
    val Text: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.text
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.muted
    val Stopped: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.stopped
    val Error: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.error
    val Track: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.track
    val SwitchOff: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.switchOff
    val SwitchOffKnob: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.switchOffKnob
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.pinEmpty
}

/** Kept for code that refers to the accent by its old name. */
val ArrAccent = CtrlColors.Accent

/**
 * Material colour scheme for a palette. `primary` is the accent as text and icons (readable on the
 * background); `primaryContainer` is the accent as a fill with dark content. In dark mode both are the accent.
 */
internal fun schemeFor(p: CtrlPalette): ColorScheme {
    val base = if (p.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = p.accentText,
        onPrimary = if (p.isDark) CtrlColors.OnAccent else Color.White,
        primaryContainer = CtrlColors.Accent,
        onPrimaryContainer = CtrlColors.OnAccent,
        inversePrimary = CtrlColors.Accent,
        secondary = p.text,
        onSecondary = p.background,
        secondaryContainer = p.track,
        onSecondaryContainer = p.text,
        // "Done" states (downloaded, complete, healthy) use the plain text colour; the accent stays for "active".
        tertiary = p.text,
        onTertiary = p.background,
        tertiaryContainer = p.raised,
        onTertiaryContainer = p.text,
        background = p.background,
        onBackground = p.text,
        surface = p.background,
        onSurface = p.text,
        surfaceVariant = p.raised,
        onSurfaceVariant = p.muted,
        surfaceTint = Color.Transparent,
        surfaceBright = p.raised,
        surfaceDim = p.background,
        surfaceContainerLowest = p.background,
        surfaceContainerLow = p.surface,
        surfaceContainer = p.surface,
        surfaceContainerHigh = p.raised,
        surfaceContainerHighest = p.track,
        inverseSurface = p.text,
        inverseOnSurface = p.background,
        outline = p.controlBorder,
        outlineVariant = p.cardBorder,
        error = p.error,
        onError = p.onError,
        errorContainer = p.errorContainer,
        onErrorContainer = p.onErrorContainer,
        scrim = Color.Black,
    )
}

internal val Dark = schemeFor(CtrlColors.Dark)
internal val Light = schemeFor(CtrlColors.Light)

/**
 * Home screen widgets: the same schemes, but with surface-coloured containers so the widget
 * background (which Glance takes from a container colour) stays plain instead of accent.
 */
private fun widgetScheme(scheme: ColorScheme, p: CtrlPalette) = scheme.copy(
    primaryContainer = p.surface,
    onPrimaryContainer = p.text,
    secondaryContainer = p.surface,
    onSecondaryContainer = p.text,
    tertiaryContainer = p.surface,
    surfaceVariant = p.track,
)

internal val WidgetDark = widgetScheme(Dark, CtrlColors.Dark)
internal val WidgetLight = widgetScheme(Light, CtrlColors.Light)


/** Space Grotesk: all UI text and headings. */
val SpaceGrotesk = FontFamily(
    groteskFont(400),
    groteskFont(500),
    groteskFont(600),
    groteskFont(700),
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
 * ArrCtrl's theme: Signal dark or Signal Light, following the phone's dark theme setting. There's no
 * in-app choice; the old theme and wallpaper-colour settings are still read (so nothing breaks) but
 * no longer change anything.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ArrCtrlTheme(choice: ThemeChoice = ThemeChoice.SYSTEM, dynamic: Boolean = false, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) CtrlColors.Dark else CtrlColors.Light
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/**
 * Space Grotesk at [weight], from the one variable font file (300 to 700): one file covers every weight the
 * app uses, instead of a separate file for each.
 */
@OptIn(ExperimentalTextApi::class)
private fun groteskFont(weight: Int) = Font(
    R.font.space_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)
