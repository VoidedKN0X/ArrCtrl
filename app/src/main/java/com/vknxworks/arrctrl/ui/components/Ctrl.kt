package com.vknxworks.arrctrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.net.TestResult
import com.vknxworks.arrctrl.data.server.ServerStatus
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.YouRoute
import com.vknxworks.arrctrl.ui.theme.CtrlColors
import com.vknxworks.arrctrl.ui.theme.CtrlShapes
import com.vknxworks.arrctrl.ui.theme.CtrlType
import com.vknxworks.arrctrl.ui.theme.Gutter

// Ctrl / Signal building blocks, shared by every screen.

// ---------- Top bars ----------

/** "ARR/CTRL", with the slash in the accent colour. */
@Composable
fun CtrlWordmark(modifier: Modifier = Modifier) {
    val name = stringResource(R.string.wordmark_name)
    Text(
        buildAnnotatedString {
            append(name)
            withStyle(SpanStyle(color = CtrlColors.AccentText)) { append("/") }
            append("CTRL")
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.Text,
        maxLines = 1,
        modifier = modifier,
    )
}

/** A 44dp square control in the top bar style: surface fill, hairline border, 14dp corners. */
@Composable
fun CtrlSquareButton(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = CtrlColors.Surface,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(CtrlShapes.Control)
            .background(container)
            .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Control)
            .clickable(role = Role.Button, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Back button for pushed screens. */
@Composable
fun CtrlBackButton(modifier: Modifier = Modifier, container: Color = CtrlColors.Surface) {
    val nav = LocalNavigator.current
    val label = stringResource(R.string.back)
    CtrlSquareButton(onClick = nav::back, contentDescription = label, modifier = modifier, container = container) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, label, Modifier.size(20.dp), tint = CtrlColors.Text)
    }
}

/**
 * The profile picture, top right on every main screen. Tapping it opens the You page with servers,
 * settings and About (there is no settings tab). A small accent square shows once a server answered.
 */
@Composable
fun CtrlAvatar(modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    val status by LocalContext.current.container.status.state.collectAsStateWithLifecycle()
    val connected = status.values.any { it is ServerStatus.Check.Done && it.result is TestResult.Ok }
    val label = stringResource(R.string.you_title)
    Box(modifier.size(44.dp)) {
        CtrlSquareButton(onClick = { nav.navigate(YouRoute) }, contentDescription = label) {
            Icon(Icons.Outlined.Person, label, Modifier.size(22.dp), tint = CtrlColors.Text)
        }
        if (connected) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(3.dp, 3.dp)
                    .size(10.dp)
                    .clip(CtrlShapes.Dot)
                    .background(CtrlColors.Background)
                    .padding(2.dp)
                    .clip(CtrlShapes.Dot)
                    .background(CtrlColors.Accent),
            )
        }
    }
}

/** Main-screen top bar: wordmark on the left, [actions] and the profile picture on the right. */
@Composable
fun CtrlTopBar(modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 12.dp, bottom = 4.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CtrlWordmark()
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        Spacer(Modifier.width(6.dp))
        CtrlAvatar()
    }
}

/** Top bar for pushed screens: square back button and a title in Space Grotesk. */
@Composable
fun CtrlDetailTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(start = Gutter, end = 12.dp, top = 10.dp, bottom = 10.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            CtrlBackButton()
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

// ---------- Labels, dots, cards ----------

/** Mono, upper-case section label in the muted colour. */
@Composable
fun CtrlLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted, style: TextStyle = CtrlType.Label) {
    Text(text.uppercase(), style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** 8dp square status dot. */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    Box(modifier.size(size).clip(CtrlShapes.Dot).background(color))
}

/** Secondary card: surface, hairline border, 22dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CtrlShapes.Card)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.CardBorder, CtrlShapes.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

/** The one most important thing on a screen: accent fill with dark content, 26dp corners. */
@Composable
fun CtrlHeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(CtrlShapes.Hero).background(CtrlColors.Accent).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

/** A 1dp divider along the top of a list row. */
@Composable
fun Modifier.topDivider(color: Color = CtrlColors.Divider): Modifier = drawBehind {
    drawLine(color, Offset(0f, 0.5f), Offset(size.width, 0.5f), strokeWidth = 1.dp.toPx())
}

/** 6dp progress bar with square-ish ends: light fill by default, accent for the main metric. */
@Composable
fun CtrlProgressBar(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = CtrlColors.Text,
    trackColor: Color = CtrlColors.Track,
    height: Dp = 6.dp,
) {
    val p = progress().coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(height).clip(CtrlShapes.Dot).background(trackColor)) {
        if (p > 0f) Box(Modifier.fillMaxWidth(p).fillMaxHeight().clip(CtrlShapes.Dot).background(color))
    }
}

// ---------- Controls ----------

/** Rounded-rect switch, 52x32dp. Accent track when on, grey when off. */
@Composable
fun CtrlSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchOff, label = "switch-track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.SwitchOffKnob, label = "switch-knob")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch-x")
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(
            value = checked,
            enabled = enabled,
            role = Role.Switch,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = true),
            onValueChange = onCheckedChange,
        )
    } else {
        Modifier
    }
    Box(
        modifier
            .size(52.dp, 32.dp)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(RoundedCorner10)
            .background(track)
            .then(toggle)
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = x).size(24.dp).clip(RoundedCorner7).background(knob))
    }
}

private val RoundedCorner10 = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
private val RoundedCorner7 = androidx.compose.foundation.shape.RoundedCornerShape(7.dp)

/** Main call to action: accent fill, dark text, 56dp tall, 18dp corners. */
@Composable
fun CtrlPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = CtrlShapes.Cta,
        colors = ButtonDefaults.buttonColors(containerColor = CtrlColors.Accent, contentColor = CtrlColors.OnAccent),
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
    }
}

/** Accent-filled colours for a Material [Button] (dark content on the accent, in both modes). */
@Composable
fun ctrlPrimaryButtonColors(): androidx.compose.material3.ButtonColors =
    ButtonDefaults.buttonColors(containerColor = CtrlColors.Accent, contentColor = CtrlColors.OnAccent)

/** Secondary button: surface fill with a hairline border. */
@Composable
fun CtrlSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = CtrlShapes.Button,
        colors = ButtonDefaults.outlinedButtonColors(containerColor = CtrlColors.Surface, contentColor = CtrlColors.Text),
        border = androidx.compose.foundation.BorderStroke(1.dp, CtrlColors.ControlBorder),
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

/** Text field: surface fill, hairline border, 16dp corners, accent border and cursor when focused. */
@Composable
fun CtrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        shape = CtrlShapes.Field,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = CtrlColors.Surface,
            unfocusedContainerColor = CtrlColors.Surface,
            disabledContainerColor = CtrlColors.Surface,
            errorContainerColor = CtrlColors.Surface,
            focusedBorderColor = CtrlColors.AccentText,
            unfocusedBorderColor = CtrlColors.ControlBorder,
            disabledBorderColor = CtrlColors.CardBorder,
            cursorColor = CtrlColors.AccentText,
            focusedLabelColor = CtrlColors.AccentText,
            unfocusedLabelColor = CtrlColors.Muted,
            focusedLeadingIconColor = CtrlColors.Text,
            unfocusedLeadingIconColor = CtrlColors.Muted,
            focusedTrailingIconColor = CtrlColors.Text,
            unfocusedTrailingIconColor = CtrlColors.Muted,
            focusedPlaceholderColor = CtrlColors.Muted,
            unfocusedPlaceholderColor = CtrlColors.Muted,
        ),
    )
}

// ---------- Dialogs and sheets ----------

/** Alert dialog: surface fill, 26dp corners, hairline border. */
@Composable
fun CtrlAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier.border(1.dp, CtrlColors.CardBorder, CtrlShapes.Dialog),
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = CtrlShapes.Dialog,
        containerColor = CtrlColors.Surface,
        iconContentColor = CtrlColors.AccentText,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
    )
}

/** Bottom sheet: surface fill and 26dp top corners, no outline (the scrim separates it; a border modifier would be drawn before the sheet's own offset, in the wrong place). */
@Composable
fun CtrlSheet(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = CtrlColors.Surface,
        shape = CtrlShapes.Sheet,
        tonalElevation = 0.dp,
        content = content,
    )
}

// ---------- Bottom navigation ----------

data class TabItem(val label: String, val icon: ImageVector)

/**
 * Floating bottom bar: 64dp tall, 22dp corners, near-opaque surface with a hairline border. The selected
 * tab is an accent-filled rounded rect with its icon and label; the others are icons only.
 */
@Composable
fun CtrlBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(64.dp)
            .clip(CtrlShapes.Bar)
            .background(CtrlColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, CtrlColors.ControlBorder, CtrlShapes.Bar)
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            val bg by animateColorAsState(if (selected) CtrlColors.Accent else Color.Transparent, label = "tab-bg")
            val fg by animateColorAsState(if (selected) CtrlColors.OnAccent else CtrlColors.Muted, label = "tab-fg")
            Row(
                Modifier
                    .height(48.dp)
                    .then(if (selected) Modifier else Modifier.width(48.dp))
                    .clip(CtrlShapes.NavItem)
                    .background(bg)
                    .selectable(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                    )
                    .padding(horizontal = if (selected) 16.dp else 0.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, contentDescription = if (selected) null else item.label, tint = fg, modifier = Modifier.size(20.dp))
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                        color = fg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------- Filter chips ----------

/** Filter chip: 10dp corners, accent fill when selected, hairline border otherwise. */
@Composable
fun CtrlFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = CtrlShapes.Chip,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = CtrlColors.Surface,
            labelColor = CtrlColors.Muted,
            iconColor = CtrlColors.Muted,
            selectedContainerColor = CtrlColors.Accent,
            selectedLabelColor = CtrlColors.OnAccent,
            selectedLeadingIconColor = CtrlColors.OnAccent,
            selectedTrailingIconColor = CtrlColors.OnAccent,
        ),
        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = CtrlColors.ControlBorder,
            selectedBorderColor = CtrlColors.Accent,
        ),
    )
}

/** Segmented buttons in Signal colours: accent fill on the selected segment. */
@Composable
fun ctrlSegmentedColors(): androidx.compose.material3.SegmentedButtonColors = androidx.compose.material3.SegmentedButtonDefaults.colors(
    activeContainerColor = CtrlColors.Accent,
    activeContentColor = CtrlColors.OnAccent,
    activeBorderColor = CtrlColors.Accent,
    inactiveContainerColor = CtrlColors.Surface,
    inactiveContentColor = CtrlColors.Muted,
    inactiveBorderColor = CtrlColors.ControlBorder,
)

// ---------- Popup menus ----------

private val MenuShape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

/**
 * Popup menu in Signal style: flat surface, 16dp corners, 1dp card border and no shadow or tonal tint
 * (Material's shadow is drawn inside the popup window and shows as a grey box).
 */
@Composable
fun CtrlDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: androidx.compose.ui.unit.DpOffset = androidx.compose.ui.unit.DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = MenuShape,
        containerColor = CtrlColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, CtrlColors.CardBorder),
        content = content,
    )
}

/** The exposed (anchored to a field) variant of [CtrlDropdownMenu], with the same look. */
@Composable
fun androidx.compose.material3.ExposedDropdownMenuBoxScope.CtrlExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MenuShape,
        containerColor = CtrlColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, CtrlColors.CardBorder),
        content = content,
    )
}

/** Menu item colours from the current palette; [destructive] items use the error colour. */
@Composable
fun ctrlMenuItemColors(destructive: Boolean = false): androidx.compose.material3.MenuItemColors =
    androidx.compose.material3.MenuDefaults.itemColors(
        textColor = if (destructive) CtrlColors.Error else CtrlColors.Text,
        leadingIconColor = if (destructive) CtrlColors.Error else CtrlColors.Muted,
        trailingIconColor = CtrlColors.Muted,
        disabledTextColor = CtrlColors.Stopped,
        disabledLeadingIconColor = CtrlColors.Stopped,
        disabledTrailingIconColor = CtrlColors.Stopped,
    )

/** A row in a [CtrlDropdownMenu]. */
@Composable
fun CtrlDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    androidx.compose.material3.DropdownMenuItem(
        text = text,
        onClick = onClick,
        modifier = modifier,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        enabled = enabled,
        colors = ctrlMenuItemColors(destructive),
    )
}
