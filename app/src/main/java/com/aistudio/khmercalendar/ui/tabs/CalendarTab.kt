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
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.aistudio.khmercalendar.alarm.scheduleAlarm
import com.aistudio.khmercalendar.alarm.cancelReminder
import com.aistudio.khmercalendar.calendar.KhmerCalendarHelper
import com.aistudio.khmercalendar.calendar.KhmerDate
import com.aistudio.khmercalendar.core.*
import com.aistudio.khmercalendar.data.AppStore
import com.aistudio.khmercalendar.data.CalendarApiMonthOverlays
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
data class AgendaItem(
    val day: Int,
    val type: AgendaType,
    val title: String,
    val subtitle: String,
    val isPast: Boolean,
    val icon: String? = null
)

enum class AgendaType { HOLIDAY, NOTE, REMINDER }

@Composable
fun CalendarTabContent(
    year: Int,
    month: Int,
    selectedDay: Int,
    onMonthChange: (Int, Int) -> Unit,
    onDayChange: (Int) -> Unit,
    onGoToToday: () -> Unit = {}
) {
    val (nightBlack, _, plumSurface, plumCard, deepBorder, _, sandText, goldSubText, dimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    var swipeOffset by remember { mutableFloatStateOf(0f) }

    // Refresh trigger — bumping this re-reads notes/reminders/holidays from store.
    var agendaVersion by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { WidgetPrefs.refresh(context) }

    val todayCal = remember { Calendar.getInstance() }
    val todayYear = todayCal.get(Calendar.YEAR)
    val todayMonth = todayCal.get(Calendar.MONTH) + 1
    val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

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
            val cached = SyncRepository.loadCachedMonthOverlays(context, year, month, maxAgeMs = null)
            if (cached != null && apiOverlaysResult == null) {
                apiOverlaysResult = Result.success(cached)
            } else if (cached == null) {
                apiOverlaysResult = null
            }
            apiOverlaysResult = SyncRepository.refreshMonth(
                context = context,
                year = year,
                month = month,
                forceRefresh = agendaVersion > 0
            )
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
    val apiWorkShiftsByDay = remember(apiOverlays) {
        apiOverlays?.workShifts.orEmpty().associateBy { it.date.dayOfMonth }
    }
    var holidaysResult by remember { mutableStateOf<Result<List<Holiday>>?>(null) }
    LaunchedEffect(year, month, agendaVersion, cloudSyncEnabled) {
        holidaysResult = null
        holidaysResult = HolidayRepository.fetchHolidays(
            context = context,
            year = year,
            forceRefresh = agendaVersion > 0,
            includeDatabaseEvents = cloudSyncEnabled
        )
    }
    val holidaysByDay = remember(holidaysResult, year, month) {
        holidaysResult
            ?.getOrNull()
            .orEmpty()
            .filter { it.date.year == year && it.date.monthValue == month }
            .groupBy { it.date.dayOfMonth }
    }

    val daysList = remember(year, month) { KhmerCalendarHelper.getGregorianMonthDays(year, month) }
    val startDayOfWeekSerial = KhmerCalendarHelper.getSerialDay(year, month, 1)
    val startOffset = ((startDayOfWeekSerial + 2) % 7 + 7) % 7

    val selectedKhmerDate = daysList.getOrNull(selectedDay - 1) ?: daysList.firstOrNull() ?: KhmerCalendarHelper.getKhmerDate(year, month, selectedDay)

    val customHolidays = remember(month, agendaVersion) { AppStore.customHolidaysForMonth(context, month) }
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

    // Per-month schedule data — used to highlight working days on the grid. Each
    // month has its own schedule; a month with none shows no work highlights. The
    // highlight maps are computed inside AnimatedContent so they stay correct for
    // whichever month is on screen (incl. mid-swipe animations).
    val scheduleCycle = remember(agendaVersion) { AppStore.getShiftCycle(context) }
    val scheduleSnaps = remember(agendaVersion) { AppStore.getCycleSnapshots(context) }

    var showDayDetailDialog by remember { mutableStateOf(false) }
    var detailDialogDate by remember { mutableStateOf<KhmerDate?>(null) }

    // Build the Monthly Agenda
    val holidayLabel = tr("ថ្ងៃឈប់សម្រាកសាធារណៈ", "Public Holiday")
    val customHolidayLabel = tr("ថ្ងៃបុណ្យផ្ទាល់ខ្លួន", "My Holiday")
    val noteLabel = tr("កំណត់ចំណាំ", "Note")
    val reminderFallback = tr("ការរំលឹក", "Reminder")

    fun isPastDay(d: Int) = year < todayYear ||
        (year == todayYear && month < todayMonth) ||
        (year == todayYear && month == todayMonth && d < todayDay)

    val monthlyAgenda = remember(
        year,
        month,
        agendaVersion,
        lang,
        daysList,
        holidaysByDay,
        apiNotesByDay,
        apiEventsByDay,
        apiHolidayEventsByDay
    ) {
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

        val localReminders = AppStore.getReminders(context)
        val localRemoteNoteIds = mutableSetOf<String>()
        val localRemoteEventIds = localReminders.mapNotNull { it.remoteEventId?.takeIf(String::isNotBlank) }.toSet()
        val localRemoteHolidayEventIds = customHolidays.mapNotNull {
            it.remoteHolidayEventId?.takeIf(String::isNotBlank)
        }.toSet()

        // 3. Notes (multiple per day)
        for (d in 1..daysList.size) {
            AppStore.getNotes(context, year, month, d).forEach { note ->
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
        localReminders.forEach { r ->
            val c = Calendar.getInstance().apply { timeInMillis = r.triggerMs }
            if (c.get(Calendar.YEAR) == year && c.get(Calendar.MONTH) + 1 == month) {
                val d = c.get(Calendar.DAY_OF_MONTH)
                val time = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
                val icon = if (r.kind == "shift") "🏭" else null
                list.add(AgendaItem(d, AgendaType.REMINDER, r.title.ifBlank { reminderFallback }, "$time · ${r.message}", isPastDay(d), icon))
            }
        }

        list.sortedWith(compareBy({ it.day }, { it.type }))
    }

    if (showDayDetailDialog && detailDialogDate != null) {
        val dialogDate = detailDialogDate!!
        DayDetailDialog(
            date = dialogDate,
            lang = lang,
            officialHolidays = holidaysByDay[dialogDate.day].orEmpty(),
            remoteNotes = apiNotesByDay[dialogDate.day].orEmpty(),
            onDismiss = { showDayDetailDialog = false },
            onDataChange = { agendaVersion++ }
        )
    }
    val isLoadingAgenda = cloudSyncEnabled && apiOverlaysResult == null

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(nightBlack)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Switcher Banner
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(plumSurface, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    var prevMonth = month - 1
                    var prevYear = year
                    if (prevMonth < 1) {
                        prevMonth = 12
                        prevYear -= 1
                    }
                    onMonthChange(prevYear, prevMonth)
                }) {
                    Text("‹", fontSize = 24.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (lang == AppLanguage.EN)
                            "${selectedKhmerDate.dayOfWeekEn}, ${gregMonth(lang, month - 1)} $selectedDay, $year"
                        else
                            "ថ្ងៃទី ${num(lang, selectedDay)} ខែ${gregMonth(lang, month - 1)} ឆ្នាំ${num(lang, year)}",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = sandText
                    )
                    Text(
                        text = tr(
                            "ព.ស. ${num(lang, selectedKhmerDate.BE)} · ${zodiac(lang, selectedKhmerDate.zodiac)}",
                            "BE ${selectedKhmerDate.BE} · ${zodiac(lang, selectedKhmerDate.zodiac)}"
                        ),
                        fontSize = 11.sp,
                        color = if (LocalAppColors.current == DarkAppColors) TraditionalGold else goldSubText,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val isTodaySelected = year == todayYear && month == todayMonth && selectedDay == todayDay
                    Box(
                        modifier = Modifier
                            .background(TraditionalGold.copy(0.15f), RoundedCornerShape(20.dp))
                            .border(1.dp, TraditionalGold.copy(0.4f), RoundedCornerShape(20.dp))
                            .clickable { onGoToToday() }
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        val todayLabel = if (lang == AppLanguage.EN)
                            if (isTodaySelected) "Today · ${selectedKhmerDate.dayOfWeekEn}"
                            else selectedKhmerDate.dayOfWeekEn
                        else
                            if (isTodaySelected) "ថ្ងៃនេះ ${selectedKhmerDate.dayOfWeek}"
                            else "ថ្ងៃ${selectedKhmerDate.dayOfWeek}"
                        Text(text = todayLabel, fontSize = 11.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                    }
                }

                IconButton(onClick = {
                    var nextMonth = month + 1
                    var nextYear = year
                    if (nextMonth > 12) {
                        nextMonth = 1
                        nextYear += 1
                    }
                    onMonthChange(nextYear, nextMonth)
                }) {
                    Text("›", fontSize = 24.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Days labels
        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                val weekLabels = weekdayLabels(lang)
                weekLabels.forEachIndexed { idx, label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (idx == 0 || idx == 6) CrimsonHoliday else dimColor
                    )
                }
            }
        }

        // Days Grid Calendar
        item {
            AnimatedContent(
                targetState = year * 12 + (month - 1),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(
                        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)
                    ) { if (forward) it / 2 else -it / 2 } +
                     fadeIn(tween(durationMillis = 240, easing = FastOutSlowInEasing))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                    ) { if (forward) -it / 4 else it / 4 } +
                     fadeOut(tween(durationMillis = 220, easing = FastOutSlowInEasing))) using
                    SizeTransform(clip = false)
                },
                label = "MonthGrid"
            ) { ym ->
                val animYear   = ym / 12
                val animMonth  = ym % 12 + 1
                val animDays = remember(ym) { KhmerCalendarHelper.getGregorianMonthDays(animYear, animMonth) }
                val animSerial = remember(ym) { KhmerCalendarHelper.getSerialDay(animYear, animMonth, 1) }
                val animOffset = ((animSerial + 2) % 7 + 7) % 7
                val animRows   = ((animOffset + animDays.size + 6) / 7)

                // Per-displayed-month highlight maps, so notes/holidays/work shifts
                // render for the month actually on screen (including mid-swipe).
                val animApiNoteDays = remember(ym, apiNotesByDay) {
                    if (animYear == year && animMonth == month) apiNotesByDay.keys else emptySet()
                }
                val animNotes = remember(ym, agendaVersion, animApiNoteDays) {
                    AppStore.daysWithNotes(context, animYear, animMonth) + animApiNoteDays
                }
                val animApiHolidayDays = remember(ym, apiHolidayEventsByDay) {
                    if (animYear == year && animMonth == month) apiHolidayEventsByDay.keys else emptySet()
                }
                val animCustomHolidayDays = remember(ym, agendaVersion, animApiHolidayDays) {
                    AppStore.customHolidaysForMonth(context, animMonth).map { it.day }.toSet() + animApiHolidayDays
                }
                val animOfficialHolidayDays = remember(ym, holidaysByDay) {
                    if (animYear == year && animMonth == month) holidaysByDay.keys else emptySet()
                }
                val animApiWorkingDays: Map<Int, AppStore.ShiftDef> = remember(ym, apiWorkShiftsByDay) {
                    if (animYear != year || animMonth != month) emptyMap()
                    else apiWorkShiftsByDay.mapNotNull { (day, workShift) ->
                        workShift.shiftTemplate
                            ?.toShiftDef()
                            ?.let { day to it }
                    }.toMap()
                }
                val animWorkingDays: Map<Int, AppStore.ShiftDef> = remember(ym, scheduleCycle, scheduleSnaps, animApiWorkingDays) {
                    val base = scheduleCycle
                    val localWorkingDays = if (base == null || !base.isConfigured) emptyMap()
                    else (1..animDays.size).mapNotNull { d ->
                        val cyc = AppStore.cycleForDate(base, scheduleSnaps, animYear, animMonth, d)
                        WorkCycleEngine.shiftForDate(cyc, animYear, animMonth, d)?.let { d to it }
                    }.toMap()
                    animApiWorkingDays + localWorkingDays
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.pointerInput(animYear, animMonth) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (swipeOffset > 80f) {
                                    var pm = animMonth - 1; var py = animYear
                                    if (pm < 1) { pm = 12; py -= 1 }
                                    onMonthChange(py, pm)
                                } else if (swipeOffset < -80f) {
                                    var nm = animMonth + 1; var ny = animYear
                                    if (nm > 12) { nm = 1; ny += 1 }
                                    onMonthChange(ny, nm)
                                }
                                swipeOffset = 0f
                            },
                            onHorizontalDrag = { _, d -> swipeOffset += d }
                        )
                    }
                ) {
                    for (row in 0 until animRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (col in 0..6) {
                                val cellIdx   = row * 7 + col
                                val dayNumber = cellIdx - animOffset + 1

                                if (cellIdx < animOffset || dayNumber > animDays.size) {
                                    Box(modifier = Modifier.weight(1f))
                                } else {
                                    val dateInfo  = animDays[dayNumber - 1]
                                    val isSelected = dayNumber == selectedDay && animYear == year && animMonth == month
                                    val isToday    = animYear == todayYear && animMonth == todayMonth && dayNumber == todayDay
                                    val isHoliday  = dateInfo.holiday != null ||
                                        dayNumber in animOfficialHolidayDays ||
                                        dayNumber in animCustomHolidayDays
                                    val isWeekend  = col == 0 || col == 6
                                    val hasNote    = dayNumber in animNotes
                                    val workShift  = animWorkingDays[dayNumber]
                                    val workColor  = if (workShift?.isOvernight == true) LotusPink else LightGold

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.8f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> TraditionalGold.copy(0.2f)
                                                    isToday    -> LotusPink.copy(0.12f)
                                                    workShift != null -> workColor.copy(0.13f)
                                                    else       -> Color.Transparent
                                                }
                                            )
                                            .border(
                                                1.5.dp,
                                                when {
                                                    isSelected -> TraditionalGold
                                                    isToday    -> LotusPink.copy(0.7f)
                                                    else       -> Color.Transparent
                                                },
                                                RoundedCornerShape(10.dp)
                                            )
                                            .combinedClickable(
                                                onClick = { onDayChange(dayNumber) },
                                                onLongClick = {
                                                    detailDialogDate = dateInfo
                                                    showDayDetailDialog = true
                                                }
                                            )
                                            .padding(4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            val hasMoon = (dateInfo.isWaxing && dateInfo.lunarDayVal in listOf(1, 8, 15)) ||
                                                (!dateInfo.isWaxing && dateInfo.lunarDayVal == 8)
                                            if (hasMoon) Text(dateInfo.moonEmoji, fontSize = 10.sp, lineHeight = 12.sp)

                                            Text(
                                                text = dayNumber.toString(),
                                                fontSize = 20.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = when {
                                                    isSelected -> TraditionalGold
                                                    isHoliday  -> LotusPink
                                                    isWeekend  -> CrimsonHoliday
                                                    else       -> sandText
                                                }
                                            )
                                            Text(
                                                text = lunarDayLabel(lang, dateInfo),
                                                fontSize = 8.sp, lineHeight = 9.sp,
                                                maxLines = 1, softWrap = false,
                                                color = if (isSelected) TraditionalGold.copy(0.8f) else dimColor
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.height(5.dp)
                                            ) {
                                                if (isHoliday) Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(LotusPink))
                                                if (hasNote) Box(
                                                    modifier = Modifier
                                                        .width(10.dp).height(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(SkyBlue)
                                                )
                                                if (workShift != null) Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .clip(CircleShape)
                                                        .background(workColor)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Hint
        item {
            Text(
                text = tr("បន្ថែម · សង្កត់​ឲ្យ​យូរ​លើ​ថ្ងៃ​ណាមួយ​ដើម្បី​បន្ថែម​កំណត់ចំណាំ ការរំលឹក ឬ​ថ្ងៃបុណ្យ",
                          "Tip · long-press any day to add notes, reminders or a holiday"),
                fontSize = 9.sp, color = dimColor, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Monthly Agenda Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                Text(
                    text = tr("\uD83D\uDCC5 \u1780\u1798\u17D2\u1798\u179C\u17B7\u1792\u17B8\u1794\u17D2\u179A\u1785\u17B6\u17C6\u1781\u17C2", "\uD83D\uDCC5 Monthly Agenda"),
                    style = TextStyle(color = sandText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                )
                // Animated pulsing dot while cloud data is in flight
                AnimatedVisibility(
                    visible = isLoadingAgenda,
                    enter = fadeIn(tween(300)),
                    exit  = fadeOut(tween(300))
                ) {
                    val pulse = rememberInfiniteTransition(label = "dot")
                    val dotAlpha by pulse.animateFloat(
                        initialValue = 0.3f, targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                        label = "dotAlpha"
                    )
                    Box(
                        modifier = Modifier.size(7.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(TraditionalGold.copy(alpha = dotAlpha))
                    )
                }
            }
        }

        item {
            AnimatedContent(
                targetState = isLoadingAgenda,
                transitionSpec = {
                    fadeIn(tween(250)) togetherWith fadeOut(tween(180))
                },
                label = "MonthlyAgendaLoading"
            ) { loading ->
                if (loading) {
                    CalendarAgendaShimmer()
                } else if (monthlyAgenda.isEmpty()) {
                    Text(
                        tr("\u1782\u17D2\u1798\u17B6\u1793\u1780\u1798\u17D2\u1798\u179C\u17B7\u1792\u17B8\u179F\u1798\u17D2\u179A\u17B6\u1794\u17CB\u1781\u17C2\u1793\u17C1\u17C7\u1791\u17C1", "No events for this month"),
                        fontSize = 12.sp, color = dimColor, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        monthlyAgenda.forEach { item ->
                            val accentColor = when (item.type) {
                                AgendaType.HOLIDAY -> LotusPink
                                AgendaType.NOTE -> SkyBlue
                                AgendaType.REMINDER -> TraditionalGold
                            }
                            val icon = item.icon ?: when (item.type) {
                                AgendaType.HOLIDAY -> "\uD83C\uDFEE"
                                AgendaType.NOTE -> "\uD83D\uDCDD"
                                AgendaType.REMINDER -> "\u23F0"
                            }
                            AgendaItemRow(
                                icon = icon,
                                title = item.title,
                                subtitle = "${num(lang, item.day)} ${gregMonth(lang, month - 1)} \u00B7 ${item.subtitle}",
                                accentColor = if (item.isPast) Color.Gray else accentColor,
                                isPast = item.isPast
                            )
                        }
                    }
                }
            }
        }
        // Legend Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendIndicator(LotusPink, tr("ថ្ងៃបុណ្យ", "Holiday"))
                LegendIndicator(SkyBlue, tr("ចំណាំ", "Note"))
                LegendIndicator(LightGold, tr("ការងារ", "Work"))
            }
        }
    }
}

@Composable
private fun AgendaItemRow(icon: String, title: String, subtitle: String, accentColor: Color, isPast: Boolean) {
    val (_, _, _, plumCard, _, _, sandText, _, _) = LocalAppColors.current
    Card(
        colors = CardDefaults.cardColors(containerColor = plumCard),
        border = BorderStroke(1.dp, accentColor.copy(0.3f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().alpha(if (isPast) 0.5f else 1f)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(accentColor.copy(0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, fontSize = 20.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (isPast) Color.Gray else sandText)
                Text(subtitle, fontSize = 10.sp, color = if (isPast) Color.Gray else accentColor.copy(0.8f))
            }
        }
    }
}

@Composable
private fun LegendIndicator(color: Color, label: String) {
    val (_, _, _, _, _, _, _, _, dimColor) = LocalAppColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
        Text(label, fontSize = 8.sp, color = dimColor)
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
    val (nightBlack, _, plumSurface, plumCard, deepBorder, _, sandText, goldSubText, dimColor) = LocalAppColors.current
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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = plumCard,
            border = BorderStroke(1.dp, TraditionalGold.copy(0.4f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.moonEmoji, fontSize = 48.sp)
                        Text(
                            text = lunarDayLabel(lang, date),
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TraditionalGold
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
                        DetailRow(tr("ថ្ងៃគ្រីស្ដ", "Gregorian"), "${date.dayOfWeekEn}, ${date.day} ${gregMonth(AppLanguage.EN, date.month-1)}", sandText, goldSubText)
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
                items(notes) { note ->
                    if (editingNoteId == note.id) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = editingNoteText,
                                onValueChange = { editingNoteText = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(fontSize = 12.sp, color = sandText),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SkyBlue)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { editingNoteId = null }) {
                                    Text(tr("បោះបង់", "Cancel"), color = goldSubText, fontSize = 11.sp)
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
                                    Text(tr("រក្សាទុក", "Save"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(plumSurface, RoundedCornerShape(10.dp))
                                .border(1.dp, SkyBlue.copy(0.25f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(note.text, fontSize = 12.sp, color = sandText, modifier = Modifier.weight(1f))
                            Text(
                                "✏️", fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable { editingNoteId = note.id; editingNoteText = note.text }
                                    .padding(horizontal = 4.dp)
                            )
                            Text(
                                "🗑️", fontSize = 14.sp,
                                modifier = Modifier
                                    .clickable {
                                        val remoteId = note.remoteId
                                        AppStore.deleteNote(context, date.year, date.month, date.day, note.id)
                                        bump()
                                        syncDeletedNote(remoteId)
                                    }
                                    .padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newNoteText,
                            onValueChange = { newNoteText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(tr("បន្ថែមកំណត់ចំណាំ…", "Add a note…"), fontSize = 12.sp) },
                            textStyle = TextStyle(fontSize = 12.sp, color = sandText),
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
                            Text(tr("បន្ថែម", "Add"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                items(dayReminders) { r ->
                    val c = Calendar.getInstance().apply { timeInMillis = r.triggerMs }
                    val time = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(plumSurface, RoundedCornerShape(10.dp))
                            .border(1.dp, TraditionalGold.copy(0.25f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏰ $time", fontSize = 12.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text(r.title, fontSize = 12.sp, color = sandText, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(
                            "🗑️", fontSize = 14.sp,
                            modifier = Modifier
                                .clickable {
                                    val remoteEventId = r.remoteEventId
                                    val triggerMs = r.triggerMs
                                    cancelReminder(context, r.requestCode)
                                    bump()
                                    syncDeletedReminder(remoteEventId, triggerMs)
                                }
                                .padding(start = 6.dp)
                        )
                    }
                }
                if (showAlarmForm) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = alarmTitleText,
                                onValueChange = { alarmTitleText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(tr("ចំណងជើង…", "Title…"), fontSize = 12.sp) },
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
                                Text(tr("ជ្រើសម៉ោង", "Pick Time"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                items(dayHolidays) { h ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(plumSurface, RoundedCornerShape(10.dp))
                            .border(1.dp, LotusPink.copy(0.25f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (lang == AppLanguage.EN) h.nameEn else h.nameKm, fontSize = 12.sp, color = sandText, modifier = Modifier.weight(1f))
                        Text(
                            "🗑️", fontSize = 14.sp,
                            modifier = Modifier
                                .clickable {
                                    val deletedHoliday = AppStore.deleteCustomHoliday(context, h.id) ?: h
                                    bump()
                                    syncDeletedCustomHoliday(deletedHoliday)
                                }
                                .padding(start = 6.dp)
                        )
                    }
                }
                if (showHolidayForm) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = holidayNameKm,
                                onValueChange = { holidayNameKm = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(tr("ឈ្មោះថ្ងៃបុណ្យ (ខ្មែរ)", "Holiday name (Khmer)"), fontSize = 12.sp) },
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LotusPink)
                            )
                            OutlinedTextField(
                                value = holidayNameEn,
                                onValueChange = { holidayNameEn = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(tr("ឈ្មោះ (English) — ស្រេចចិត្ត", "Name (English) — optional"), fontSize = 12.sp) },
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
                                Text(tr("រក្សាទុក", "Save"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = actionColor, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, valueColor: Color, labelColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = labelColor)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
