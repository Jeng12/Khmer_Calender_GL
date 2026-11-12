package com.aistudio.khmercalendar.ui.tabs

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.aistudio.khmercalendar.ui.components.CalendarAgendaShimmer
import com.aistudio.khmercalendar.ui.components.KhmerDatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.aistudio.khmercalendar.alarm.scheduleAlarm
import com.aistudio.khmercalendar.alarm.cancelReminder
import com.aistudio.khmercalendar.calendar.KhmerCalendarHelper
import com.aistudio.khmercalendar.calendar.KhmerDate
import com.aistudio.khmercalendar.core.*
import com.aistudio.khmercalendar.data.AppStore
import com.aistudio.khmercalendar.data.CalendarApiNote
import com.aistudio.khmercalendar.data.Holiday
import com.aistudio.khmercalendar.data.HolidayRepository
import com.aistudio.khmercalendar.data.SyncRepository
import com.aistudio.khmercalendar.data.WorkCycleEngine
import com.aistudio.khmercalendar.ui.theme.*
import com.aistudio.khmercalendar.widget.WidgetPrefs
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

// Data model for monthly agenda items
@Immutable
data class AgendaItem(
    val day: Int,
    val type: AgendaType,
    val title: String,
    val subtitle: String,
    val isPast: Boolean,
    val icon: String? = null,
    /** Unique within one month's agenda; lets the list reuse rows across updates. */
    val key: String = ""
)

enum class AgendaType { HOLIDAY, NOTE, REMINDER }

private val CellShape = RoundedCornerShape(12.dp)
private val CardShape = RoundedCornerShape(18.dp)
private val PillShape = RoundedCornerShape(50)

/** Working days of one month from the locally stored rotating-shift schedule. */
private fun localWorkingDays(
    base: AppStore.ShiftCycle?,
    snapshots: Map<String, List<String?>>,
    year: Int,
    month: Int,
    dayCount: Int
): Map<Int, AppStore.ShiftDef> {
    if (base == null || !base.isConfigured) return emptyMap()
    val result = HashMap<Int, AppStore.ShiftDef>()
    for (d in 1..dayCount) {
        val cycle = AppStore.cycleForDate(base, snapshots, year, month, d)
        WorkCycleEngine.shiftForDate(cycle, year, month, d)?.let { result[d] = it }
    }
    return result
}

private fun shiftTimeLabel(shift: AppStore.ShiftDef): String =
    "%02d:%02d–%02d:%02d".format(shift.startHour, shift.startMin, shift.endHour, shift.endMin)

@Composable
fun CalendarTabContent(
    year: Int,
    month: Int,
    selectedDay: Int,
    onMonthChange: (Int, Int) -> Unit,
    onDayChange: (Int) -> Unit,
    onGoToToday: () -> Unit = {}
) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // Refresh trigger — bumping this re-reads notes/reminders/holidays from store.
    var agendaVersion by remember { mutableIntStateOf(0) }
    // Version that each remote source last force-refreshed for, so a single edit
    // forces one refetch instead of disabling the cache for every later month.
    val forcedVersions = remember { intArrayOf(0, 0) }

    LaunchedEffect(Unit) { WidgetPrefs.refresh(context) }

    // Re-read "today" whenever the app returns to the foreground, so the marker
    // is right after the app has been left open across midnight.
    var today by remember { mutableStateOf(LocalDate.now()) }
    LifecycleResumeEffect(Unit) {
        today = LocalDate.now()
        onPauseOrDispose { }
    }
    val todayDayInMonth = if (today.year == year && today.monthValue == month) today.dayOfMonth else 0

    val cloudSyncEnabled = AppStore.isCloudSyncEnabled(context) &&
        context.getSharedPreferences(AppStore.SETTINGS_FILE, android.content.Context.MODE_PRIVATE)
            .getBoolean("cloud_sync_disclosure_seen", false)
    var apiOverlaysResult by remember(year, month, cloudSyncEnabled) {
        mutableStateOf(
            if (cloudSyncEnabled) {
                SyncRepository.loadCachedMonthOverlays(context, year, month, maxAgeMs = null)
                    ?.let { Result.success(it) }
            } else {
                null
            }
        )
    }
    LaunchedEffect(year, month, agendaVersion, cloudSyncEnabled) {
        if (cloudSyncEnabled) {
            apiOverlaysResult = SyncRepository.refreshMonth(
                context = context,
                year = year,
                month = month,
                forceRefresh = agendaVersion != forcedVersions[0]
            )
            forcedVersions[0] = agendaVersion
        }
    }
    val apiOverlays = apiOverlaysResult?.getOrNull()
        ?.takeIf { it.year == year && it.month == month }
    val apiNotesByDay = remember(apiOverlays) {
        apiOverlays?.notes.orEmpty().groupBy { it.date.dayOfMonth }
    }
    val apiEventsByDay = remember(apiOverlays) {
        apiOverlays?.events.orEmpty().mapNotNull { event ->
            event.date?.takeIf { it.year == year && it.monthValue == month }?.dayOfMonth?.let { it to event }
        }.groupBy({ it.first }, { it.second })
    }
    val apiHolidayEventsByDay = remember(apiOverlays) {
        apiOverlays?.holidayEvents.orEmpty().mapNotNull { event ->
            val date = event.occurrenceDate ?: event.date
            date.takeIf { it.year == year && it.monthValue == month }?.dayOfMonth?.let { it to event }
        }.groupBy({ it.first }, { it.second })
    }
    val apiWorkByDay: Map<Int, AppStore.ShiftDef> = remember(apiOverlays) {
        val result = HashMap<Int, AppStore.ShiftDef>()
        apiOverlays?.workShifts.orEmpty().forEach { workShift ->
            workShift.shiftTemplate?.toShiftDef()?.let { result[workShift.date.dayOfMonth] = it }
        }
        result
    }

    // Public holidays are fetched per *year*: moving between months of the same
    // year reuses the list instead of clearing it and asking the repository again.
    var holidaysState by remember { mutableStateOf<Pair<Int, List<Holiday>>?>(null) }
    LaunchedEffect(year, agendaVersion, cloudSyncEnabled) {
        HolidayRepository.fetchHolidays(
            context = context,
            year = year,
            forceRefresh = agendaVersion != forcedVersions[1],
            includeDatabaseEvents = cloudSyncEnabled
        ).onSuccess { holidaysState = year to it }
        forcedVersions[1] = agendaVersion
    }
    val yearHolidays = holidaysState?.takeIf { it.first == year }?.second.orEmpty()
    val holidaysByDay = remember(yearHolidays, month) {
        yearHolidays
            .filter { it.date.monthValue == month }
            .groupBy { it.date.dayOfMonth }
    }

    val daysList = remember(year, month) { KhmerCalendarHelper.getGregorianMonthDays(year, month) }
    val safeSelectedDay = selectedDay.coerceIn(1, daysList.size)
    val selectedKhmerDate = daysList[safeSelectedDay - 1]

    // Local store, read once per month (and again after an edit or a sync that
    // may have written remote rows locally). The grid and the agenda share it.
    val notesByDay = remember(year, month, agendaVersion, apiOverlays) {
        AppStore.getNotesForMonth(context, year, month)
    }
    val customHolidays = remember(month, agendaVersion, apiOverlays) {
        AppStore.customHolidaysForMonth(context, month)
    }
    val localReminders = remember(agendaVersion, apiOverlays) { AppStore.getReminders(context) }
    val scheduleCycle = remember(agendaVersion, apiOverlays) { AppStore.getShiftCycle(context) }
    val scheduleSnaps = remember(agendaVersion, apiOverlays) { AppStore.getCycleSnapshots(context) }
    val workByDay = remember(year, month, scheduleCycle, scheduleSnaps, apiWorkByDay) {
        apiWorkByDay + localWorkingDays(scheduleCycle, scheduleSnaps, year, month, daysList.size)
    }

    LaunchedEffect(year, month, customHolidays, cloudSyncEnabled) {
        if (!cloudSyncEnabled) return@LaunchedEffect
        var repairedAny = false
        customHolidays
            .filter { it.remoteHolidayEventId.isNullOrBlank() }
            .forEach { holiday ->
                val holidayDate = runCatching { LocalDate.of(year, holiday.month, holiday.day) }
                    .getOrNull()
                    ?: return@forEach
                SyncRepository.enqueueCustomHolidayUpsert(context, holiday, holidayDate)
                repairedAny = true
            }
        if (repairedAny) {
            SyncRepository.syncPending(context)
                .onSuccess {
                    WidgetPrefs.refresh(context)
                    agendaVersion++
                }
        }
    }

    var detailDialogDate by remember { mutableStateOf<KhmerDate?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }

    // Build the Monthly Agenda
    val holidayLabel = tr("ថ្ងៃឈប់សម្រាកសាធារណៈ", "Public Holiday")
    val customHolidayLabel = tr("ថ្ងៃបុណ្យផ្ទាល់ខ្លួន", "My Holiday")
    val noteLabel = tr("កំណត់ចំណាំ", "Note")
    val reminderFallback = tr("ការរំលឹក", "Reminder")

    val monthlyAgenda = remember(
        year,
        month,
        today,
        lang,
        daysList,
        holidaysByDay,
        notesByDay,
        customHolidays,
        localReminders,
        apiNotesByDay,
        apiEventsByDay,
        apiHolidayEventsByDay
    ) {
        val firstFutureDay = when {
            year < today.year || (year == today.year && month < today.monthValue) -> Int.MAX_VALUE
            year == today.year && month == today.monthValue -> today.dayOfMonth
            else -> 0
        }
        fun isPastDay(d: Int) = d < firstFutureDay

        val list = mutableListOf<AgendaItem>()
        val officialHolidayKeys = mutableSetOf<String>()

        // 1. Public holidays and Buddhist events from API/cache, with local calendar fallback.
        if (holidaysByDay.isNotEmpty()) {
            holidaysByDay.forEach { (d, holidays) ->
                holidays.forEach { holiday ->
                    officialHolidayKeys.add("${holiday.date}:${holiday.nameKh}:${holiday.nameEn}")
                    val title = if (lang == AppLanguage.EN) holiday.nameEn else holiday.nameKh
                    val subtitle = if (holiday.isBuddhist) {
                        tr(lang, "ព្រះពុទ្ធ", "Buddhist event")
                    } else {
                        holidayLabel
                    }
                    list.add(AgendaItem(d, AgendaType.HOLIDAY, title, subtitle, isPastDay(d), if (holiday.isBuddhist) "🪷" else null))
                }
            }
        } else {
            daysList.forEachIndexed { index, khDate ->
                val d = index + 1
                if (khDate.holiday != null) {
                    list.add(AgendaItem(d, AgendaType.HOLIDAY, localizeDual(lang, khDate.holiday!!), holidayLabel, isPastDay(d)))
                }
            }
        }

        // 2. Custom (user) holidays
        customHolidays.forEach { h ->
            list.add(AgendaItem(h.day, AgendaType.HOLIDAY, if (lang == AppLanguage.EN) h.nameEn else h.nameKm, customHolidayLabel, isPastDay(h.day)))
        }

        val localRemoteNoteIds = mutableSetOf<String>()
        val localRemoteEventIds = localReminders.mapNotNull { it.remoteEventId?.takeIf(String::isNotBlank) }.toSet()
        val localRemoteHolidayEventIds = customHolidays.mapNotNull {
            it.remoteHolidayEventId?.takeIf(String::isNotBlank)
        }.toSet()

        // 3. Notes (multiple per day)
        notesByDay.forEach { (d, notes) ->
            if (d > daysList.size) return@forEach
            notes.forEach { note ->
                note.remoteId?.takeIf { it.isNotBlank() }?.let(localRemoteNoteIds::add)
                list.add(AgendaItem(d, AgendaType.NOTE, note.text, noteLabel, isPastDay(d)))
            }
        }

        // 4. Remote API overlays
        apiNotesByDay.forEach { (d, notes) ->
            notes.forEach { note ->
                if (note.id !in localRemoteNoteIds) {
                    list.add(AgendaItem(d, AgendaType.NOTE, note.text, noteLabel, isPastDay(d)))
                }
            }
        }
        apiEventsByDay.forEach { (d, events) ->
            events.forEach { event ->
                if (event.id !in localRemoteEventIds) {
                    val subtitle = listOfNotNull(event.timeLabel, event.location)
                        .joinToString(" - ")
                        .ifBlank { reminderFallback }
                    list.add(AgendaItem(d, AgendaType.REMINDER, event.title, subtitle, isPastDay(d)))
                }
            }
        }
        apiHolidayEventsByDay.forEach { (d, holidays) ->
            holidays.forEach { event ->
                if (event.id !in localRemoteHolidayEventIds) {
                    val title = if (lang == AppLanguage.EN) event.nameEn else event.nameKm
                    val key = "${event.occurrenceDate ?: event.date}:${event.nameKm}:${event.nameEn}"
                    if (key !in officialHolidayKeys) {
                        list.add(AgendaItem(d, AgendaType.HOLIDAY, title, customHolidayLabel, isPastDay(d)))
                    }
                }
            }
        }

        // 5. Reminders / events (keyed off trigger time)
        val c = Calendar.getInstance()
        localReminders.forEach { r ->
            c.timeInMillis = r.triggerMs
            if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) + 1 == month) {
                val d = c.get(Calendar.DAY_OF_MONTH)
                val time = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
                val icon = if (r.kind == "shift") "🏭" else null
                val subtitle = if (r.message.isBlank()) time else "$time · ${r.message}"
                list.add(AgendaItem(d, AgendaType.REMINDER, r.title.ifBlank { reminderFallback }, subtitle, isPastDay(d), icon))
            }
        }

        list.sortedWith(compareBy({ it.day }, { it.type }))
            .mapIndexed { index, item -> item.copy(key = "${item.day}-${item.type.ordinal}-$index") }
    }
    val selectedDayAgenda = remember(monthlyAgenda, safeSelectedDay) {
        monthlyAgenda.filter { it.day == safeSelectedDay }
    }

    detailDialogDate?.let { dialogDate ->
        DayDetailDialog(
            date = dialogDate,
            lang = lang,
            officialHolidays = holidaysByDay[dialogDate.day].orEmpty(),
            remoteNotes = apiNotesByDay[dialogDate.day].orEmpty(),
            onDismiss = { detailDialogDate = null },
            onDataChange = { agendaVersion++ }
        )
    }
    if (showMonthPicker) {
        KhmerDatePickerDialog(
            initialDate = LocalDate.of(year, month, safeSelectedDay),
            onDateSelected = { date, _ ->
                showMonthPicker = false
                if (date.year != year || date.monthValue != month) onMonthChange(date.year, date.monthValue)
                onDayChange(date.dayOfMonth)
            },
            onDismiss = { showMonthPicker = false }
        )
    }

    val isLoadingAgenda = cloudSyncEnabled && apiOverlaysResult == null

    // Month stepping, clamped to the range the lunar tables cover.
    val currentOnMonthChange by rememberUpdatedState(onMonthChange)
    val stepMonth: (Int, Int, Int) -> Unit = remember {
        { fromYear, fromMonth, delta ->
            val index = fromYear * 12 + (fromMonth - 1) + delta
            val targetYear = index / 12
            if (targetYear in KhmerCalendarHelper.SUPPORTED_YEARS) {
                currentOnMonthChange(targetYear, index % 12 + 1)
            }
        }
    }
    val openDetail: (Int) -> Unit = { day ->
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        if (day != selectedDay) onDayChange(day)
        detailDialogDate = daysList.getOrNull(day - 1)
    }

    val lunarMonthsLabel = remember(daysList, lang) {
        val first = lunarMonth(lang, daysList.first().lunarMonthName)
        val last = lunarMonth(lang, daysList.last().lunarMonthName)
        if (first == last) first else "$first – $last"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header", contentType = "header") {
            CalendarHeader(
                title = if (lang == AppLanguage.EN) "${gregMonth(lang, month - 1)} $year"
                        else "${gregMonth(lang, month - 1)} ${num(lang, year)}",
                subtitle = tr(
                    "ខែ$lunarMonthsLabel · ព.ស. ${num(lang, selectedKhmerDate.BE)}",
                    "$lunarMonthsLabel · BE ${selectedKhmerDate.BE}"
                ),
                isOnToday = todayDayInMonth != 0 && safeSelectedDay == todayDayInMonth,
                onPrevious = { stepMonth(year, month, -1) },
                onNext = { stepMonth(year, month, 1) },
                onToday = onGoToToday,
                onTitleClick = { showMonthPicker = true }
            )
        }

        // Weekday labels + month grid + legend share one surface.
        item(key = "grid", contentType = "grid") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface, CardShape)
                    .border(1.dp, colors.border.copy(alpha = 0.6f), CardShape)
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WeekdayHeader(lang)

                AnimatedContent(
                    targetState = year * 12 + (month - 1),
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(
                            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                        ) { if (forward) it / 3 else -it / 3 } +
                         fadeIn(tween(durationMillis = 220, easing = FastOutSlowInEasing))) togetherWith
                        (slideOutHorizontally(
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                        ) { if (forward) -it / 4 else it / 4 } +
                         fadeOut(tween(durationMillis = 160, easing = FastOutSlowInEasing))) using
                        SizeTransform(clip = false)
                    },
                    label = "MonthGrid"
                ) { ym ->
                    val pageYear = ym / 12
                    val pageMonth = ym % 12 + 1
                    val isCurrent = pageYear == year && pageMonth == month
                    val pageDays = remember(ym) { KhmerCalendarHelper.getGregorianMonthDays(pageYear, pageMonth) }
                    val pageOffset = remember(ym) {
                        ((KhmerCalendarHelper.getSerialDay(pageYear, pageMonth, 1) + 2) % 7 + 7) % 7
                    }

                    // Per-displayed-month markers, so notes/holidays/work shifts stay
                    // correct for whichever month is on screen (including the page
                    // sliding out). The current month reuses the data loaded above.
                    val noteDays = remember(ym, isCurrent, notesByDay, apiNotesByDay, agendaVersion) {
                        if (isCurrent) notesByDay.keys + apiNotesByDay.keys
                        else AppStore.daysWithNotes(context, pageYear, pageMonth)
                    }
                    val holidayDays = remember(ym, isCurrent, yearHolidays, customHolidays, apiHolidayEventsByDay, agendaVersion) {
                        val result = HashSet<Int>()
                        yearHolidays.forEach {
                            if (it.date.year == pageYear && it.date.monthValue == pageMonth) result.add(it.date.dayOfMonth)
                        }
                        if (isCurrent) {
                            customHolidays.forEach { result.add(it.day) }
                            result.addAll(apiHolidayEventsByDay.keys)
                        } else {
                            AppStore.customHolidaysForMonth(context, pageMonth).forEach { result.add(it.day) }
                        }
                        result
                    }
                    val pageWork = remember(ym, isCurrent, workByDay, scheduleCycle, scheduleSnaps) {
                        if (isCurrent) workByDay
                        else localWorkingDays(scheduleCycle, scheduleSnaps, pageYear, pageMonth, pageDays.size)
                    }

                    MonthGrid(
                        days = pageDays,
                        startOffset = pageOffset,
                        lang = lang,
                        selectedDay = if (isCurrent) safeSelectedDay else 0,
                        todayDay = if (today.year == pageYear && today.monthValue == pageMonth) today.dayOfMonth else 0,
                        noteDays = noteDays,
                        holidayDays = holidayDays,
                        workByDay = pageWork,
                        onDayClick = onDayChange,
                        onDayLongClick = openDetail,
                        modifier = Modifier.pointerInput(ym) {
                            val threshold = 56.dp.toPx()
                            var dragged = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragged = 0f },
                                onDragCancel = { dragged = 0f },
                                onDragEnd = {
                                    if (dragged > threshold) stepMonth(pageYear, pageMonth, -1)
                                    else if (dragged < -threshold) stepMonth(pageYear, pageMonth, 1)
                                    dragged = 0f
                                },
                                onHorizontalDrag = { _, delta -> dragged += delta }
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendIndicator(LotusPink, tr("ថ្ងៃបុណ្យ", "Holiday"))
                    LegendIndicator(SkyBlue, tr("ចំណាំ", "Note"))
                    LegendIndicator(LightGold, tr("ការងារ", "Work"))
                }
            }
        }

        // Selected day summary — also the visible way into the day editor
        // (long-pressing a day still works).
        item(key = "selected", contentType = "selected") {
            SelectedDayCard(
                date = selectedKhmerDate,
                lang = lang,
                isToday = todayDayInMonth != 0 && safeSelectedDay == todayDayInMonth,
                shift = workByDay[safeSelectedDay],
                dayAgenda = selectedDayAgenda,
                onOpen = { detailDialogDate = selectedKhmerDate }
            )
        }

        // Monthly Agenda Section
        item(key = "agendaHeader", contentType = "agendaHeader") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp, start = 2.dp)
            ) {
                Text(
                    text = tr("កម្មវិធីប្រចាំខែ", "Monthly Agenda"),
                    style = TextStyle(color = colors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.semantics { heading() }
                )
                if (monthlyAgenda.isNotEmpty()) {
                    Text(
                        text = num(lang, monthlyAgenda.size),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentText,
                        modifier = Modifier
                            .background(TraditionalGold.copy(alpha = 0.15f), PillShape)
                            .padding(horizontal = 8.dp, vertical = 1.dp)
                    )
                }
                if (isLoadingAgenda) SyncingDot()
            }
        }

        when {
            monthlyAgenda.isNotEmpty() -> items(
                items = monthlyAgenda,
                key = { it.key },
                contentType = { "agenda" }
            ) { item ->
                AgendaItemRow(
                    item = item,
                    weekday = daysList.getOrNull(item.day - 1)?.let {
                        if (lang == AppLanguage.EN) it.dayOfWeekEn.take(3) else it.dayOfWeekShort
                    }.orEmpty(),
                    lang = lang,
                    isSelected = item.day == safeSelectedDay,
                    onClick = onDayChange,
                    modifier = Modifier.animateItem()
                )
            }
            // Nothing local to show yet and the cloud copy is still in flight.
            isLoadingAgenda -> item(key = "agendaLoading", contentType = "agendaLoading") {
                CalendarAgendaShimmer()
            }
            else -> item(key = "agendaEmpty", contentType = "agendaEmpty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, CardShape)
                        .padding(vertical = 22.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        tr("គ្មានកម្មវិធីសម្រាប់ខែនេះទេ", "No events for this month"),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text, textAlign = TextAlign.Center
                    )
                    Text(
                        tr("ជ្រើសថ្ងៃណាមួយ រួចចុច «បន្ថែម» ដើម្បីបង្កើតកំណត់ចំណាំ ឬការរំលឹក",
                           "Pick a day and tap Add to create a note, reminder or holiday"),
                        fontSize = 11.sp, lineHeight = 16.sp, color = colors.dim, textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarHeader(
    title: String,
    subtitle: String,
    isOnToday: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onTitleClick: () -> Unit
) {
    val colors = LocalAppColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(
                    onClickLabel = tr("ជ្រើសរើសខែ", "Choose month"),
                    role = Role.Button,
                    onClick = onTitleClick
                )
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = colors.dim)
            }
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = colors.accentText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // "Today" is filled while you are away from today, and quiet once there.
        Text(
            text = tr("ថ្ងៃនេះ", "Today"),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isOnToday) colors.dim else OnAccent,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .clip(PillShape)
                .then(
                    if (isOnToday) Modifier.border(1.dp, colors.border, PillShape)
                    else Modifier.background(TraditionalGold)
                )
                .clickable(role = Role.Button, onClick = onToday)
                .padding(horizontal = 14.dp, vertical = 7.dp)
        )
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = tr("ខែមុន", "Previous month"),
                tint = colors.accentText
            )
        }
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = tr("ខែបន្ទាប់", "Next month"),
                tint = colors.accentText
            )
        }
    }
}

@Composable
private fun WeekdayHeader(lang: AppLanguage) {
    val colors = LocalAppColors.current
    Row(modifier = Modifier.fillMaxWidth().clearAndSetSemantics { }) {
        weekdayLabels(lang).forEachIndexed { idx, label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (idx == 0 || idx == 6) CrimsonHoliday else colors.dim
            )
        }
    }
}

/**
 * One month of day cells. Kept separate from the tab so that selecting a day
 * only recomposes the two cells whose state changed: every other [DayCell]
 * receives identical parameters and is skipped.
 */
@Composable
private fun MonthGrid(
    days: List<KhmerDate>,
    startOffset: Int,
    lang: AppLanguage,
    selectedDay: Int,
    todayDay: Int,
    noteDays: Set<Int>,
    holidayDays: Set<Int>,
    workByDay: Map<Int, AppStore.ShiftDef>,
    onDayClick: (Int) -> Unit,
    onDayLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Cell text is built once per month/language instead of on every recomposition.
    val lunarLabels = remember(days, lang) { days.map { lunarDayLabel(lang, it) } }
    val descriptions = remember(days, lang) {
        days.map { d ->
            val weekday = if (lang == AppLanguage.EN) d.dayOfWeekEn else "ថ្ងៃ${d.dayOfWeek}"
            "$weekday ${num(lang, d.day)} ${gregMonth(lang, d.month - 1)}, " +
                "${lunarDayLabel(lang, d)} ${lunarMonth(lang, d.lunarMonthName)}"
        }
    }
    val rows = (startOffset + days.size + 6) / 7

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val day = row * 7 + col - startOffset + 1
                    if (day < 1 || day > days.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val info = days[day - 1]
                        val showsMoon = (info.isWaxing && (info.lunarDayVal == 1 || info.lunarDayVal == 8 || info.lunarDayVal == 15)) ||
                            (!info.isWaxing && info.lunarDayVal == 8)
                        val shift = workByDay[day]
                        DayCell(
                            day = day,
                            lunarLabel = lunarLabels[day - 1],
                            description = descriptions[day - 1],
                            moon = if (showsMoon) info.moonEmoji else null,
                            isSelected = day == selectedDay,
                            isToday = day == todayDay,
                            isHoliday = info.holiday != null || day in holidayDays,
                            isWeekend = col == 0 || col == 6,
                            hasNote = day in noteDays,
                            hasWork = shift != null,
                            isOvernightWork = shift?.isOvernight == true,
                            compactLabel = lang == AppLanguage.EN,
                            onClick = onDayClick,
                            onLongClick = onDayLongClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    lunarLabel: String,
    description: String,
    moon: String?,
    isSelected: Boolean,
    isToday: Boolean,
    isHoliday: Boolean,
    isWeekend: Boolean,
    hasNote: Boolean,
    hasWork: Boolean,
    isOvernightWork: Boolean,
    compactLabel: Boolean,
    onClick: (Int) -> Unit,
    onLongClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val workColor = if (isOvernightWork) LotusPink else LightGold
    val holidayText = tr("ថ្ងៃបុណ្យ", "Holiday")
    val noteText = tr("មានកំណត់ចំណាំ", "Has notes")
    val workText = tr("ថ្ងៃធ្វើការ", "Work day")
    val todayText = tr("ថ្ងៃនេះ", "Today")
    val detailsLabel = tr("បើកព័ត៌មានលម្អិត", "Open day details")

    Box(
        modifier = modifier
            .padding(1.dp)
            .aspectRatio(0.88f)
            .clip(CellShape)
            .then(
                when {
                    isSelected -> Modifier
                        .background(TraditionalGold.copy(alpha = 0.18f))
                        .border(1.5.dp, TraditionalGold, CellShape)
                    hasWork -> Modifier.background(workColor.copy(alpha = 0.12f))
                    else -> Modifier
                }
            )
            .semantics {
                selected = isSelected
                contentDescription = buildString {
                    if (isToday) append(todayText).append(", ")
                    append(description)
                    if (isHoliday) append(", ").append(holidayText)
                    if (hasNote) append(", ").append(noteText)
                    if (hasWork) append(", ").append(workText)
                }
            }
            .combinedClickable(
                onClick = { onClick(day) },
                onLongClickLabel = detailsLabel,
                onLongClick = { onLongClick(day) }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (moon != null) {
            Text(
                moon,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 2.dp, end = 3.dp).clearAndSetSemantics { }
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.clearAndSetSemantics { }
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .then(if (isToday) Modifier.background(TraditionalGold, CircleShape) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = day.toString(),
                    fontSize = 16.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.SemiBold,
                    color = when {
                        isToday    -> OnAccent
                        isSelected -> colors.accentText
                        isHoliday  -> LotusPink
                        isWeekend  -> CrimsonHoliday
                        else       -> colors.text
                    }
                )
            }
            Text(
                text = lunarLabel,
                fontSize = if (compactLabel) 8.sp else 9.sp,
                lineHeight = 12.sp,
                maxLines = 1,
                softWrap = false,
                color = if (isSelected) colors.accentText else colors.dim
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(6.dp)
            ) {
                if (isHoliday) Box(Modifier.size(5.dp).background(LotusPink, CircleShape))
                if (hasNote) Box(Modifier.width(10.dp).height(4.dp).background(SkyBlue, PillShape))
                if (hasWork) Box(Modifier.size(5.dp).background(workColor, CircleShape))
            }
        }
    }
}

@Composable
private fun SelectedDayCard(
    date: KhmerDate,
    lang: AppLanguage,
    isToday: Boolean,
    shift: AppStore.ShiftDef?,
    dayAgenda: List<AgendaItem>,
    onOpen: () -> Unit
) {
    val colors = LocalAppColors.current
    val holidays = dayAgenda.filter { it.type == AgendaType.HOLIDAY }
    val noteCount = dayAgenda.count { it.type == AgendaType.NOTE }
    val reminderCount = dayAgenda.count { it.type == AgendaType.REMINDER }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(colors.card)
            .border(1.dp, TraditionalGold.copy(alpha = 0.35f), CardShape)
            .clickable(onClickLabel = tr("បើកព័ត៌មានលម្អិត", "Open day details"), onClick = onOpen)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(date.moonEmoji, fontSize = 30.sp, modifier = Modifier.clearAndSetSemantics { })

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = buildString {
                    if (isToday) append(tr(lang, "ថ្ងៃនេះ · ", "Today · "))
                    if (lang == AppLanguage.EN) {
                        append("${date.dayOfWeekEn}, ${gregMonth(lang, date.month - 1)} ${date.day}")
                    } else {
                        append("ថ្ងៃ${date.dayOfWeek} ទី${num(lang, date.day)} ${gregMonth(lang, date.month - 1)}")
                    }
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = tr(
                    "${lunarDayLabel(lang, date)} ខែ${date.lunarMonthName} · ${date.zodiac}",
                    "${lunarDayLabel(lang, date)}, ${lunarMonth(lang, date.lunarMonthName)} · ${zodiac(lang, date.zodiac)}"
                ),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colors.subText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            holidays.forEach { holiday ->
                Text(
                    text = "${holiday.icon ?: "🏮"} ${holiday.title}",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LotusPink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (shift != null || noteCount > 0 || reminderCount > 0) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (shift != null) {
                        InfoPill(
                            text = "${shift.name} ${shiftTimeLabel(shift)}",
                            tint = if (shift.isOvernight) LotusPink else LightGold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (noteCount > 0) InfoPill("📝 ${num(lang, noteCount)}", SkyBlue)
                    if (reminderCount > 0) InfoPill("⏰ ${num(lang, reminderCount)}", TraditionalGold)
                }
            }
        }

        FilledTonalButton(
            onClick = onOpen,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = TraditionalGold,
                contentColor = OnAccent
            ),
            contentPadding = PaddingValues(start = 10.dp, end = 14.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(tr("បន្ថែម", "Add"), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun InfoPill(text: String, tint: Color, modifier: Modifier = Modifier) {
    val colors = LocalAppColors.current
    Text(
        text = text,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(tint.copy(alpha = 0.18f), PillShape)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** Pulsing dot shown while cloud data is in flight; animates in the layer, not by recomposing. */
@Composable
private fun SyncingDot() {
    val pulse = rememberInfiniteTransition(label = "dot")
    val dotAlpha = pulse.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "dotAlpha"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .graphicsLayer { alpha = dotAlpha.value }
            .background(TraditionalGold, CircleShape)
    )
}

@Composable
private fun AgendaItemRow(
    item: AgendaItem,
    weekday: String,
    lang: AppLanguage,
    isSelected: Boolean,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current
    val accent = when (item.type) {
        AgendaType.HOLIDAY -> LotusPink
        AgendaType.NOTE -> SkyBlue
        AgendaType.REMINDER -> TraditionalGold
    }
    val icon = item.icon ?: when (item.type) {
        AgendaType.HOLIDAY -> "🏮"
        AgendaType.NOTE -> "📝"
        AgendaType.REMINDER -> "⏰"
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (item.isPast) 0.6f else 1f }
            .clip(shape)
            .background(colors.card)
            .border(1.dp, if (isSelected) TraditionalGold.copy(alpha = 0.7f) else accent.copy(alpha = 0.28f), shape)
            .clickable { onClick(item.day) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Date badge
        Column(
            modifier = Modifier
                .width(46.dp)
                .background(accent.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
                .padding(vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                num(lang, item.day),
                fontSize = 17.sp, lineHeight = 21.sp, fontWeight = FontWeight.Bold, color = colors.text
            )
            Text(weekday, fontSize = 10.sp, lineHeight = 14.sp, color = colors.subText, maxLines = 1)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                item.title,
                fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = colors.text,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                item.subtitle,
                fontSize = 11.sp, lineHeight = 16.sp, color = colors.subText,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Text(icon, fontSize = 18.sp, modifier = Modifier.clearAndSetSemantics { })
    }
}

@Composable
private fun LegendIndicator(color: Color, label: String) {
    val colors = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(7.dp).background(color, CircleShape))
        Text(label, fontSize = 11.sp, lineHeight = 14.sp, color = colors.dim)
    }
}

@Composable
private fun DayDetailDialog(
    date: KhmerDate,
    lang: AppLanguage,
    officialHolidays: List<Holiday> = emptyList(),
    remoteNotes: List<CalendarApiNote> = emptyList(),
    onDismiss: () -> Unit,
    onDataChange: () -> Unit
) {
    val (_, _, plumSurface, plumCard, deepBorder, _, sandText, goldSubText, _) = LocalAppColors.current
    val accentText = LocalAppColors.current.accentText
    val context = LocalContext.current
    val widgetScope = rememberCoroutineScope()
    val selectedDate = remember(date.year, date.month, date.day) {
        LocalDate.of(date.year, date.month, date.day)
    }

    // Local refresh counter so the dialog re-reads the store as the user edits.
    var localVersion by remember { mutableIntStateOf(0) }
    fun bump() { localVersion++; onDataChange() }
    fun showApiSyncFailed(message: String = "Saved locally; API database sync failed") {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun syncSavedNote(localId: String, remoteId: String?, text: String) {
        if (!AppStore.isCloudSyncEnabled(context)) {
            widgetScope.launch { WidgetPrefs.refresh(context) }
            return
        }
        val clean = text.trim()
        SyncRepository.enqueueNoteUpsert(context, localId, selectedDate, clean, remoteId)
        widgetScope.launch {
            WidgetPrefs.refresh(context)
            SyncRepository.syncPending(context)
                .onSuccess {
                    bump()
                    WidgetPrefs.refresh(context)
                }
                .onFailure { showApiSyncFailed("Saved locally; will sync when online") }
        }
    }

    fun syncDeletedNote(remoteId: String?) {
        if (!AppStore.isCloudSyncEnabled(context)) {
            widgetScope.launch { WidgetPrefs.refresh(context) }
            return
        }
        if (remoteId.isNullOrBlank()) {
            widgetScope.launch { WidgetPrefs.refresh(context) }
            return
        }
        SyncRepository.enqueueNoteDelete(context, "remote-note-$remoteId", remoteId, selectedDate)
        widgetScope.launch {
            SyncRepository.syncPending(context)
                .onSuccess {
                    bump()
                    WidgetPrefs.refresh(context)
                }
                .onFailure { showApiSyncFailed("Deleted locally; will sync when online") }
        }
    }

    fun syncSavedReminder(reminder: AppStore.Reminder) {
        if (reminder.kind != "reminder") return
        if (!AppStore.isCloudSyncEnabled(context)) {
            widgetScope.launch { WidgetPrefs.refresh(context) }
            return
        }
        SyncRepository.enqueueReminderUpsert(context, reminder)
        widgetScope.launch {
            WidgetPrefs.refresh(context)
            SyncRepository.syncPending(context)
                .onSuccess {
                    bump()
                    WidgetPrefs.refresh(context)
                }
                .onFailure { showApiSyncFailed("Saved locally; will sync when online") }
        }
    }

    fun syncDeletedReminder(remoteEventId: String?, triggerMs: Long) {
        if (!AppStore.isCloudSyncEnabled(context)) {
            widgetScope.launch { WidgetPrefs.refresh(context) }
            return
        }
        if (remoteEventId.isNullOrBlank()) {
            widgetScope.launch { WidgetPrefs.refresh(context) }
            return
        }
        SyncRepository.enqueueReminderDelete(context, remoteEventId, triggerMs)
        widgetScope.launch {
            SyncRepository.syncPending(context)
                .onSuccess {
                    bump()
                    WidgetPrefs.refresh(context)
                }
                .onFailure { showApiSyncFailed("Deleted locally; will sync when online") }
        }
    }

    fun syncSavedCustomHoliday(holiday: AppStore.CustomHoliday) {
        if (!AppStore.isCloudSyncEnabled(context)) {
            widgetScope.launch { WidgetPrefs.refresh(context) }
            return
        }
        SyncRepository.enqueueCustomHolidayUpsert(context, holiday, selectedDate)
        widgetScope.launch {
            WidgetPrefs.refresh(context)
            SyncRepository.syncPending(context)
                .onSuccess {
                    bump()
                    WidgetPrefs.refresh(context)
                }
                .onFailure { showApiSyncFailed("Saved locally; will sync when online") }
        }
    }

    fun syncDeletedCustomHoliday(holiday: AppStore.CustomHoliday) {
        if (!AppStore.isCloudSyncEnabled(context)) {
            widgetScope.launch { WidgetPrefs.refresh(context) }
            return
        }
        val remoteEventId = holiday.remoteHolidayEventId
        if (remoteEventId.isNullOrBlank()) {
            widgetScope.launch { WidgetPrefs.refresh(context) }
            return
        }
        SyncRepository.enqueueCustomHolidayDelete(context, holiday, selectedDate)
        widgetScope.launch {
            SyncRepository.syncPending(context)
                .onSuccess { WidgetPrefs.refresh(context) }
                .onFailure { showApiSyncFailed("Deleted locally; will sync when online") }
        }
    }

    val notes = remember(localVersion, remoteNotes) {
        val localNotes = AppStore.getNotes(context, date.year, date.month, date.day)
        val localRemoteIds = localNotes.mapNotNull { it.remoteId?.takeIf(String::isNotBlank) }.toSet()
        localNotes + remoteNotes
            .filter { it.id !in localRemoteIds }
            .map { remoteNote ->
                AppStore.Note(
                    id = "remote-note-${remoteNote.id}",
                    text = remoteNote.text,
                    ts = 0L,
                    remoteId = remoteNote.id
                )
            }
    }
    val dayReminders = remember(localVersion) {
        AppStore.getReminders(context).filter {
            val c = Calendar.getInstance().apply { timeInMillis = it.triggerMs }
            c.get(Calendar.YEAR) == date.year && c.get(Calendar.MONTH) + 1 == date.month && c.get(Calendar.DAY_OF_MONTH) == date.day
        }.sortedBy { it.triggerMs }
    }
    val dayHolidays = remember(localVersion) {
        AppStore.getCustomHolidays(context).filter { it.month == date.month && it.day == date.day }
    }

    var newNoteText by remember { mutableStateOf("") }
    var editingNoteId by remember { mutableStateOf<String?>(null) }
    var editingNoteText by remember { mutableStateOf("") }

    var alarmTitleText by remember { mutableStateOf("") }
    var showAlarmForm by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var showHolidayForm by remember { mutableStateOf(false) }
    var holidayNameKm by remember { mutableStateOf("") }
    var holidayNameEn by remember { mutableStateOf("") }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showTimePicker = true }

    if (showTimePicker) {
        val defMin = AppStore.getDefaultReminderMinutes(context)
        val dlg = TimePickerDialog(
            context,
            { _, hour, minute ->
                val reminder = scheduleAlarm(context, date.year, date.month, date.day, hour, minute, alarmTitleText, date, lang)
                widgetScope.launch { WidgetPrefs.refresh(context) }
                alarmTitleText = ""
                bump()
                syncSavedReminder(reminder)
                Toast.makeText(context, tr(lang, "បានកំណត់ការរំលឹក", "Reminder set"), Toast.LENGTH_SHORT).show()
                showTimePicker = false
                showAlarmForm = false
            },
            defMin / 60, defMin % 60, true
        )
        dlg.setOnCancelListener { showTimePicker = false }
        dlg.show()
    }

    // Use the full window width (minus a margin) instead of the narrow platform
    // default, so the text fields and rows are not cramped.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = plumCard,
            border = BorderStroke(1.dp, TraditionalGold.copy(0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 18.dp, vertical = 24.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.moonEmoji, fontSize = 44.sp)
                        Text(
                            text = lunarDayLabel(lang, date),
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accentText
                        )
                        Text(
                            text = lunarMonth(lang, date.lunarMonthName),
                            fontSize = 16.sp, color = sandText
                        )
                    }
                }

                item { HorizontalDivider(color = deepBorder, thickness = 1.dp) }

                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        DetailRow(
                            tr("ថ្ងៃគ្រីស្ដ", "Gregorian"),
                            if (lang == AppLanguage.EN) "${date.dayOfWeekEn}, ${date.day} ${gregMonth(lang, date.month - 1)} ${date.year}"
                            else "ថ្ងៃ${date.dayOfWeek} ទី${num(lang, date.day)} ${gregMonth(lang, date.month - 1)} ${num(lang, date.year)}",
                            sandText, goldSubText
                        )
                        DetailRow(tr("ពុទ្ធសករាជ", "Buddhist Era"), num(lang, date.BE), sandText, goldSubText)
                        DetailRow(tr("ឆ្នាំ", "Zodiac"), zodiac(lang, date.zodiac), sandText, goldSubText)

                        if (date.holiday != null) {
                            DetailRow(tr("ថ្ងៃបុណ្យ", "Holiday"), localizeDual(lang, date.holiday!!), LotusPink, goldSubText)
                        }
                        officialHolidays
                            .filterNot { holiday ->
                                date.holiday != null &&
                                    (holiday.nameKh == date.holiday || holiday.nameEn == date.holiday)
                            }
                            .forEach { holiday ->
                                DetailRow(
                                    if (holiday.isBuddhist) tr("ព្រឹត្តិការណ៍ព្រះពុទ្ធ", "Buddhist event") else tr("ថ្ងៃឈប់សម្រាក", "Public holiday"),
                                    if (lang == AppLanguage.EN) holiday.nameEn else holiday.nameKh,
                                    if (holiday.isBuddhist) TraditionalGold else LotusPink,
                                    goldSubText
                                )
                            }
                    }
                }

                item { HorizontalDivider(color = deepBorder, thickness = 1.dp) }

                // ── Notes (multiple) ──────────────────────────────────────────
                item {
                    SectionHeader(
                        title = tr("📝 កំណត់ចំណាំ", "📝 Notes"),
                        actionLabel = null, color = sandText, onAction = null
                    )
                }
                items(notes, key = { "note-${it.id}" }) { note ->
                    if (editingNoteId == note.id) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = editingNoteText,
                                onValueChange = { editingNoteText = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(fontSize = 14.sp, color = sandText),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SkyBlue)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { editingNoteId = null }) {
                                    Text(tr("បោះបង់", "Cancel"), color = goldSubText, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = {
                                        val cleanText = editingNoteText.trim()
                                        AppStore.updateNote(context, date.year, date.month, date.day, note.id, cleanText)
                                        editingNoteId = null
                                        bump()
                                        if (cleanText.isEmpty()) {
                                            syncDeletedNote(note.remoteId)
                                        } else {
                                            syncSavedNote(note.id, note.remoteId, cleanText)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = SkyBlue)
                                ) {
                                    Text(tr("រក្សាទុក", "Save"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(plumSurface, RoundedCornerShape(10.dp))
                                .border(1.dp, SkyBlue.copy(0.25f), RoundedCornerShape(10.dp))
                                .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(note.text, fontSize = 13.sp, lineHeight = 19.sp, color = sandText, modifier = Modifier.weight(1f))
                            RowAction(Icons.Filled.Edit, tr("កែកំណត់ចំណាំ", "Edit note"), SkyBlue) {
                                editingNoteId = note.id; editingNoteText = note.text
                            }
                            RowAction(Icons.Filled.Delete, tr("លុបកំណត់ចំណាំ", "Delete note"), LotusPink) {
                                val remoteId = note.remoteId
                                AppStore.deleteNote(context, date.year, date.month, date.day, note.id)
                                bump()
                                syncDeletedNote(remoteId)
                            }
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newNoteText,
                            onValueChange = { newNoteText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(tr("បន្ថែមកំណត់ចំណាំ…", "Add a note…"), fontSize = 14.sp) },
                            textStyle = TextStyle(fontSize = 14.sp, color = sandText),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SkyBlue)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val savedNote = AppStore.addNote(context, date.year, date.month, date.day, newNoteText)
                                if (savedNote != null) {
                                    newNoteText = ""
                                    bump()
                                    syncSavedNote(savedNote.id, savedNote.remoteId, savedNote.text)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(tr("បន្ថែម", "Add"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }

                item { HorizontalDivider(color = deepBorder, thickness = 1.dp) }

                // ── Reminders (multiple, deletable) ───────────────────────────
                item {
                    SectionHeader(
                        title = tr("🔔 ការរំលឹក", "🔔 Reminders"),
                        actionLabel = if (showAlarmForm) tr("បោះបង់", "Cancel") else tr("+ កំណត់", "+ Set"),
                        color = sandText,
                        actionColor = TraditionalGold,
                        onAction = { showAlarmForm = !showAlarmForm }
                    )
                }
                items(dayReminders, key = { "reminder-${it.requestCode}" }) { r ->
                    val c = Calendar.getInstance().apply { timeInMillis = r.triggerMs }
                    val time = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(plumSurface, RoundedCornerShape(10.dp))
                            .border(1.dp, TraditionalGold.copy(0.25f), RoundedCornerShape(10.dp))
                            .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏰ $time", fontSize = 13.sp, color = accentText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text(r.title, fontSize = 13.sp, color = sandText, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        RowAction(Icons.Filled.Delete, tr("លុបការរំលឹក", "Delete reminder"), LotusPink) {
                            val remoteEventId = r.remoteEventId
                            val triggerMs = r.triggerMs
                            cancelReminder(context, r.requestCode)
                            bump()
                            syncDeletedReminder(remoteEventId, triggerMs)
                        }
                    }
                }
                if (showAlarmForm) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = alarmTitleText,
                                onValueChange = { alarmTitleText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(tr("ចំណងជើង…", "Title…"), fontSize = 14.sp) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TraditionalGold)
                            )
                            Button(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                                            showTimePicker = true
                                        else
                                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        showTimePicker = true
                                    }
                                },
                                modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold)
                            ) {
                                Text(tr("ជ្រើសម៉ោង", "Pick Time"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                item { HorizontalDivider(color = deepBorder, thickness = 1.dp) }

                // ── Custom holiday ────────────────────────────────────────────
                item {
                    SectionHeader(
                        title = tr("🏮 ថ្ងៃបុណ្យផ្ទាល់ខ្លួន", "🏮 My Holiday"),
                        actionLabel = if (showHolidayForm) tr("បោះបង់", "Cancel") else tr("+ បន្ថែម", "+ Add"),
                        color = sandText,
                        actionColor = LotusPink,
                        onAction = { showHolidayForm = !showHolidayForm }
                    )
                }
                items(dayHolidays, key = { "holiday-${it.id}" }) { h ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(plumSurface, RoundedCornerShape(10.dp))
                            .border(1.dp, LotusPink.copy(0.25f), RoundedCornerShape(10.dp))
                            .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (lang == AppLanguage.EN) h.nameEn else h.nameKm, fontSize = 13.sp, color = sandText, modifier = Modifier.weight(1f))
                        RowAction(Icons.Filled.Delete, tr("លុបថ្ងៃបុណ្យ", "Delete holiday"), LotusPink) {
                            val deletedHoliday = AppStore.deleteCustomHoliday(context, h.id) ?: h
                            bump()
                            syncDeletedCustomHoliday(deletedHoliday)
                        }
                    }
                }
                if (showHolidayForm) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = holidayNameKm,
                                onValueChange = { holidayNameKm = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(tr("ឈ្មោះថ្ងៃបុណ្យ (ខ្មែរ)", "Holiday name (Khmer)"), fontSize = 14.sp) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LotusPink)
                            )
                            OutlinedTextField(
                                value = holidayNameEn,
                                onValueChange = { holidayNameEn = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(tr("ឈ្មោះ (English) — ស្រេចចិត្ត", "Name (English) — optional"), fontSize = 14.sp) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LotusPink)
                            )
                            Button(
                                onClick = {
                                    val savedHoliday = AppStore.addCustomHoliday(context, date.month, date.day, holidayNameKm, holidayNameEn)
                                    if (savedHoliday != null) {
                                        holidayNameKm = ""; holidayNameEn = ""
                                        showHolidayForm = false
                                        bump()
                                        syncSavedCustomHoliday(savedHoliday)
                                    }
                                },
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = LotusPink)
                            ) {
                                Text(tr("រក្សាទុក", "Save"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

                item {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = plumSurface),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, deepBorder)
                    ) {
                        Text(tr("បិទ", "Close"), color = sandText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Icon action inside a dialog row, with a full 48dp touch target and a spoken label. */
@Composable
private fun RowAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String?,
    color: Color,
    actionColor: Color = SkyBlue,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = actionColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color, labelColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = labelColor)
        Text(
            value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor,
            textAlign = TextAlign.End, modifier = Modifier.weight(1f).padding(start = 12.dp)
        )
    }
}
