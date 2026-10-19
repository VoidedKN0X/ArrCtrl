package com.vknxworks.arrctrl.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vknxworks.arrctrl.R
import com.vknxworks.arrctrl.container
import com.vknxworks.arrctrl.data.server.ServiceType
import com.vknxworks.arrctrl.ui.components.LocalBottomClearance
import com.vknxworks.arrctrl.ui.components.MessageView
import com.vknxworks.arrctrl.ui.components.ServerErrorCard
import com.vknxworks.arrctrl.ui.components.TabTitle
import com.vknxworks.arrctrl.ui.nav.AccountRoute
import com.vknxworks.arrctrl.ui.nav.LocalNavigator
import com.vknxworks.arrctrl.ui.nav.MovieRoute
import com.vknxworks.arrctrl.ui.nav.SeasonRoute
import com.vknxworks.arrctrl.ui.theme.Gutter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private enum class KindFilter(val label: Int) { ALL(R.string.calendar_both), MOVIES(R.string.tab_movies), SERIES(R.string.tab_series) }

/** Days the agenda shows at first, and how many more each "Show more" adds. */
private const val AGENDA_STEP = 28L

@Composable
fun statusColor(s: EntryStatus): Color = when (s) {
    EntryStatus.DOWNLOADED -> MaterialTheme.colorScheme.tertiary
    EntryStatus.MISSING -> MaterialTheme.colorScheme.error
    EntryStatus.UPCOMING -> MaterialTheme.colorScheme.primary
    EntryStatus.UNMONITORED -> MaterialTheme.colorScheme.outline
}

@Composable
fun CalendarScreen() {
    val c = LocalContext.current.container
    val nav = LocalNavigator.current
    val servers by c.servers.servers.collectAsStateWithLifecycle()
    val hasAny = servers.any { it.type == ServiceType.RADARR || it.type == ServiceType.SONARR }

    if (!hasAny) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            TabTitle(stringResource(R.string.tab_calendar))
            MessageView(
                Icons.Outlined.CalendarMonth,
                stringResource(R.string.empty_calendar_title),
                stringResource(R.string.empty_calendar_body),
                Modifier.padding(bottom = LocalBottomClearance.current),
                actionLabel = stringResource(R.string.add_server),
                onAction = { nav.navigate(AccountRoute) },
            )
        }
        return
    }

    val model: CalendarModel = viewModel { CalendarModel(c) }
    val settings by c.settings.settings.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val kind = runCatching { KindFilter.valueOf(settings.calendarKind) }.getOrDefault(KindFilter.ALL)
    val monitoredOnly = settings.calendarMonitoredOnly
    val month = settings.calendarMonth

    val today = remember { LocalDate.now() }
    var agendaStart by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var agendaDays by rememberSaveable { mutableStateOf(AGENDA_STEP) }
    var shownMonth by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    var refreshing by remember { mutableStateOf(false) }
    val ym = YearMonth.parse(shownMonth)
    val firstDayOfWeek = remember { WeekFields.of(Locale.getDefault()).firstDayOfWeek }

    // The range on screen: the agenda window, or every day the month grid shows (including spill-over days).
    val (from, to) = if (month) {
        val first = ym.atDay(1)
        val gridStart = first.minusDays(((first.dayOfWeek.value - firstDayOfWeek.value + 7) % 7).toLong())
        gridStart to gridStart.plusDays(41)
    } else {
        LocalDate.ofEpochDay(agendaStart) to today.plusDays(agendaDays)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(from, to, monitoredOnly, servers) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { runCatching { model.load(from, to, monitoredOnly, force = true) } }
    }

    val shown = model.entries.filter {
        when (kind) {
            KindFilter.ALL -> true
            KindFilter.MOVIES -> it.kind == EntryKind.MOVIE
            KindFilter.SERIES -> it.kind == EntryKind.EPISODE
        }
    }
    val multiServer = servers.count { it.type == ServiceType.RADARR || it.type == ServiceType.SONARR } > 2 ||
        servers.count { it.type == ServiceType.RADARR } > 1 || servers.count { it.type == ServiceType.SONARR } > 1

    fun open(e: CalendarEntry) {
        when (e.kind) {
            EntryKind.MOVIE -> e.movieId?.let { nav.navigate(MovieRoute(e.server.id, it)) }
            EntryKind.EPISODE -> if (e.seriesId != null && e.season != null) nav.navigate(SeasonRoute(e.server.id, e.seriesId, e.season))
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        TabTitle(stringResource(R.string.tab_calendar)) {
            IconButton(onClick = {
                agendaStart = today.toEpochDay(); agendaDays = AGENDA_STEP
                shownMonth = YearMonth.from(today).toString(); selectedDay = today.toEpochDay()
            }) { Icon(Icons.Outlined.Today, stringResource(R.string.calendar_today)) }
            IconButton(onClick = { scope.launch { c.settings.update { it.copy(calendarMonth = !it.calendarMonth) } } }) {
                Icon(
                    if (month) Icons.Outlined.ViewAgenda else Icons.Outlined.CalendarMonth,
                    stringResource(if (month) R.string.calendar_agenda else R.string.calendar_month),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KindFilter.entries.forEach { k ->
                FilterChip(
                    selected = k == kind,
                    onClick = { scope.launch { c.settings.update { it.copy(calendarKind = k.name) } } },
                    label = { Text(stringResource(k.label)) },
                )
            }
            FilterChip(
                selected = monitoredOnly,
                onClick = { scope.launch { c.settings.update { it.copy(calendarMonitoredOnly = !it.calendarMonitoredOnly) } } },
                label = { Text(stringResource(R.string.calendar_monitored_only)) },
            )
        }
        Legend()

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { refreshing = true; runCatching { model.load(from, to, monitoredOnly, force = true) }; refreshing = false } },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            if (month) {
                MonthView(
                    ym = ym,
                    gridStart = from,
                    firstDayOfWeek = firstDayOfWeek,
                    today = today,
                    selected = LocalDate.ofEpochDay(selectedDay),
                    entries = shown,
                    model = model,
                    multiServer = multiServer,
                    onPrev = { shownMonth = ym.minusMonths(1).toString() },
                    onNext = { shownMonth = ym.plusMonths(1).toString() },
                    onSelect = { selectedDay = it.toEpochDay() },
                    onOpen = ::open,
                )
            } else {
                AgendaView(
                    from = from,
                    today = today,
                    entries = shown,
                    model = model,
                    multiServer = multiServer,
                    onEarlier = { agendaStart -= 7 },
                    onMore = { agendaDays += AGENDA_STEP },
                    onOpen = ::open,
                )
            }
        }
    }
}

@Composable
private fun Legend() {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            EntryStatus.DOWNLOADED to R.string.badge_downloaded,
            EntryStatus.MISSING to R.string.badge_missing,
            EntryStatus.UPCOMING to R.string.calendar_upcoming,
            EntryStatus.UNMONITORED to R.string.badge_unmonitored,
        ).forEach { (s, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor(s)))
                Spacer(Modifier.width(5.dp))
                Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun dayHeader(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.calendar_today)
    today.plusDays(1) -> stringResource(R.string.calendar_tomorrow)
    today.minusDays(1) -> stringResource(R.string.calendar_yesterday)
    else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) } +
        ", " + day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
}

@Composable
private fun AgendaView(
    from: LocalDate,
    today: LocalDate,
    entries: List<CalendarEntry>,
    model: CalendarModel,
    multiServer: Boolean,
    onEarlier: () -> Unit,
    onMore: () -> Unit,
    onOpen: (CalendarEntry) -> Unit,
) {
    val byDay = entries.groupBy { it.day }.toSortedMap()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomClearance.current)) {
        items(model.errors.entries.toList(), key = { "err-" + it.key.id }) { (s, r) -> ServerErrorCard(s, r) }
        item(key = "earlier") {
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = onEarlier, shape = CircleShape) {
                    Text(stringResource(if (from.isBefore(today)) R.string.calendar_even_earlier else R.string.calendar_earlier))
                }
            }
        }
        if (!model.loaded) {
            item(key = "loading") { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        } else if (byDay.isEmpty()) {
            item(key = "empty") { MessageView(Icons.Outlined.EventBusy, stringResource(R.string.calendar_nothing), stringResource(R.string.calendar_nothing_body)) }
        }
        byDay.forEach { (day, list) ->
            item(key = "h-$day") {
                Text(
                    dayHeader(day, today),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (day == today) FontWeight.Bold else null,
                    modifier = Modifier.padding(start = Gutter, end = Gutter, top = 14.dp, bottom = 4.dp),
                )
            }
            items(list, key = { it.key }) { e -> EntryRow(e, multiServer) { onOpen(e) } }
        }
        if (model.loaded) {
            item(key = "more") {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    if (model.loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else OutlinedButton(onClick = onMore, shape = CircleShape) { Text(stringResource(R.string.calendar_show_more)) }
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: CalendarEntry, multiServer: Boolean, onClick: () -> Unit) {
    val color = statusColor(e.status)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).height(40.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.width(56.dp)) {
            Text(
                when (e.kind) {
                    EntryKind.EPISODE -> e.at.atZone(ZoneId.systemDefault()).format(CalendarModel.TIME)
                    EntryKind.MOVIE -> stringResource(
                        when (e.release) {
                            MovieRelease.CINEMA -> R.string.calendar_cinema
                            MovieRelease.PHYSICAL -> R.string.calendar_physical
                            else -> R.string.calendar_digital
                        },
                    )
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (e.kind == EntryKind.MOVIE) Icons.Outlined.Movie else Icons.Outlined.Tv,
                    null,
                    Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val extra = listOfNotNull(
                e.subtitle,
                if (e.premiere && e.kind == EntryKind.EPISODE) stringResource(R.string.calendar_premiere) else null,
                if (e.finale) stringResource(R.string.episode_finale) else null,
                if (multiServer) e.server.name else null,
            ).joinToString(" · ")
            if (extra.isNotBlank()) {
                Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MonthView(
    ym: YearMonth,
    gridStart: LocalDate,
    firstDayOfWeek: java.time.DayOfWeek,
    today: LocalDate,
    selected: LocalDate,
    entries: List<CalendarEntry>,
    model: CalendarModel,
    multiServer: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate) -> Unit,
    onOpen: (CalendarEntry) -> Unit,
) {
    val byDay = entries.groupBy { it.day }
    val dayEntries = byDay[selected].orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomClearance.current)) {
        items(model.errors.entries.toList(), key = { "err-" + it.key.id }) { (s, r) -> ServerErrorCard(s, r) }
        item(key = "nav") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, stringResource(R.string.calendar_previous_month)) }
                Text(
                    ym.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) } + " " + ym.year,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                if (model.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, stringResource(R.string.calendar_next_month)) }
            }
        }
        item(key = "weekdays") {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter - 4.dp)) {
                (0 until 7).forEach { i ->
                    Text(
                        firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        (0 until 6).forEach { week ->
            item(key = "w$week") {
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter - 4.dp)) {
                    (0 until 7).forEach { d ->
                        val day = gridStart.plusDays((week * 7 + d).toLong())
                        DayCell(
                            day = day,
                            inMonth = YearMonth.from(day) == ym,
                            isToday = day == today,
                            isSelected = day == selected,
                            entries = byDay[day].orEmpty(),
                            modifier = Modifier.weight(1f),
                        ) { onSelect(day) }
                    }
                }
            }
        }
        item(key = "sel") {
            Text(
                dayHeader(selected, today),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Gutter, end = Gutter, top = 16.dp, bottom = 4.dp),
            )
        }
        if (dayEntries.isEmpty()) {
            item(key = "none") {
                Text(
                    stringResource(R.string.calendar_day_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Gutter, vertical = 8.dp),
                )
            }
        }
        items(dayEntries, key = { it.key }) { e -> EntryRow(e, multiServer) { onOpen(e) } }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    entries: List<CalendarEntry>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.small
    Column(
        modifier
            .aspectRatio(0.85f)
            .padding(2.dp)
            .clip(shape)
            .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .then(if (isToday) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = when {
                isToday -> MaterialTheme.colorScheme.primary
                inMonth -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.outline
            },
        )
        Spacer(Modifier.height(4.dp))
        // Up to three dots, one per entry, in that entry's status colour; "+n" for the rest.
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight(0.4f)) {
            entries.take(3).forEach { e -> Box(Modifier.size(6.dp).clip(CircleShape).background(statusColor(e.status))) }
        }
        if (entries.size > 3) {
            Text("+${entries.size - 3}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
