package com.aistudio.khmercalendar.ui.tabs

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.aistudio.khmercalendar.alarm.WorkScheduleScheduler
import com.aistudio.khmercalendar.core.*
import com.aistudio.khmercalendar.data.AppStore
import com.aistudio.khmercalendar.data.SyncRepository
import com.aistudio.khmercalendar.data.WorkCycleEngine
import com.aistudio.khmercalendar.ui.components.ScheduleShimmer
import com.aistudio.khmercalendar.ui.theme.*
import com.aistudio.khmercalendar.widget.WidgetPrefs
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun ScheduleTabContent() {
    val (nightBlack, _, plumSurface, plumCard, deepBorder, _, sandText, goldSubText, dimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var cycle by remember { mutableStateOf(AppStore.getShiftCycle(context)) }
    var editingShift by remember { mutableStateOf<AppStore.ShiftDef?>(null) }
    // Which cycle is being viewed: 0 = current, -1 = previous month, +1 = next, …
    var viewedOffset by remember { mutableIntStateOf(0) }
    // Which week (0..3) currently has its "set whole week" quick-picker open.
    var weekQuickPick by remember { mutableStateOf<Int?>(null) }
    // Target system type awaiting confirmation before it wipes the daily schedule.
    var pendingSystemType by remember { mutableStateOf<Int?>(null) }
    // Per-month schedules (cycleKey → day assignments). Each month is independent;
    // a month with no entry has no work. Migrate the old single template once.
    var schedules by remember {
        AppStore.migrateLegacyTemplate(context)
        mutableStateOf(AppStore.getCycleSnapshots(context))
    }
    val today = remember { Calendar.getInstance() }
    val tY = today.get(Calendar.YEAR); val tM = today.get(Calendar.MONTH) + 1; val tD = today.get(Calendar.DAY_OF_MONTH)

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun fmtDate(cal: Calendar) =
        "${num(lang, cal.get(Calendar.DAY_OF_MONTH))} ${gregMonth(lang, cal.get(Calendar.MONTH))}"

    fun syncWorkScheduleToDatabase(cycleToSave: AppStore.ShiftCycle, schedulesToSave: Map<String, List<String?>>) {
        if (!AppStore.isCloudSyncEnabled(context)) {
            scope.launch { WidgetPrefs.refresh(context) }
            return
        }
        SyncRepository.enqueueWorkSchedule(context, cycleToSave, schedulesToSave)
        scope.launch {
            SyncRepository.syncPending(context)
                .onSuccess { WidgetPrefs.refresh(context) }
                .onFailure {
                    Toast.makeText(context, "Schedule saved locally; will sync when online", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun syncClearWorkSchedulesFromDatabase(keysToClear: Set<String>) {
        if (!AppStore.isCloudSyncEnabled(context)) {
            scope.launch { WidgetPrefs.refresh(context) }
            return
        }
        SyncRepository.enqueueClearWorkSchedules(context, keysToClear)
        scope.launch {
            SyncRepository.syncPending(context)
                .onSuccess { WidgetPrefs.refresh(context) }
                .onFailure {
                    Toast.makeText(context, "Schedule deleted locally; will sync when online", Toast.LENGTH_SHORT).show()
                }
        }
    }

    var isLoadingSchedule by remember {
        mutableStateOf(
            AppStore.isCloudSyncEnabled(context) &&
                cycle?.isConfigured != true &&
                schedules.isEmpty()
        )
    }

    // Pull the latest work schedule from the remote DB when the tab opens,
    // then refresh local Compose state so the UI reflects the synced data.
    LaunchedEffect(Unit) {
        if (AppStore.isCloudSyncEnabled(context)) {
            if (cycle?.isConfigured != true && schedules.isEmpty()) {
                isLoadingSchedule = true
            }
            SyncRepository.pullWorkScheduleFromRemote(context)
        }
        cycle = AppStore.getShiftCycle(context)
        schedules = AppStore.getCycleSnapshots(context)
        isLoadingSchedule = false
        WidgetPrefs.refresh(context)
    }

    // Edit-shift dialog
    editingShift?.let { shift ->
        ShiftTimeEditor(
            shift = shift,
            lang = lang,
            onDismiss = { editingShift = null },
            onSave = { updated ->
                cycle = cycle?.let { c -> c.copy(shifts = c.shifts.map { if (it.id == updated.id) updated else it }) }
                editingShift = null
            }
        )
    }

    // Confirm before switching shift systems — it resets the whole daily schedule.
    pendingSystemType?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingSystemType = null },
            confirmButton = {
                TextButton(onClick = {
                    cycle = cycle?.copy(
                        systemType = target,
                        shifts = AppStore.presetShifts(target),
                        dayAssignments = AppStore.emptyDayAssignments()
                    )
                    schedules = emptyMap()   // shift ids change → every month is cleared
                    weekQuickPick = null
                    pendingSystemType = null
                }) { Text(tr("កំណត់ឡើងវិញ", "Reset"), color = CrimsonHoliday, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { pendingSystemType = null }) {
                    Text(tr("បោះបង់", "Cancel"), color = goldSubText)
                }
            },
            title = { Text(tr("ប្ដូរប្រព័ន្ធវេន?", "Switch shift system?"), color = sandText, fontWeight = FontWeight.Bold) },
            text = { Text(tr("ការប្ដូរនេះនឹងលុបកាលវិភាគគ្រប់ខែទាំងអស់។ បន្តឬ?", "This will reset the schedule for every month. Continue?"), color = sandText) },
            containerColor = plumCard,
            shape = RoundedCornerShape(16.dp)
        )
    }

    AnimatedContent(
        targetState = isLoadingSchedule,
        transitionSpec = {
            fadeIn(tween(250)) togetherWith fadeOut(tween(180))
        },
        label = "ScheduleLoading"
    ) { loading ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().background(nightBlack).padding(16.dp)) {
                ScheduleShimmer()
            }
        } else {
            Box(modifier = Modifier.fillMaxSize().background(nightBlack)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(tr("កាលវិភាគការងារ (Work Schedule)", "Work Schedule"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = sandText)
                    Text(tr("ប្រព័ន្ធវេនវិលជុំ · ខួប ២៦ ដល់ ២៥", "Rotating shifts · 26th-to-25th cycle"), fontSize = 10.sp, color = LotusPink)
                }
            }

            val c = cycle
            if (c == null || !c.isConfigured) {
                // ── First-time setup ──────────────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(tr("ជ្រើសរើសប្រព័ន្ធវេន", "Choose a shift system"), fontSize = 12.sp, color = goldSubText, fontWeight = FontWeight.Bold)
                        SystemSetupCard(
                            emoji = "🌗",
                            title = tr("ប្រព័ន្ធ ២ វេន", "2-shift system"),
                            subtitle = tr("ថ្ងៃ ០៧:៣០–១៩:៣០ · យប់ ១៩:៣០–០៧:៣០", "Day 07:30–19:30 · Night 19:30–07:30"),
                            onClick = {
                                cycle = AppStore.ShiftCycle(2, AppStore.presetShifts(2), AppStore.emptyDayAssignments(), true, 30)
                            }
                        )
                        SystemSetupCard(
                            emoji = "🕗",
                            title = tr("ប្រព័ន្ធ ៣ វេន", "3-shift system"),
                            subtitle = tr("បីវេនស្មើគ្នា ៨ ម៉ោង", "Three equal 8-hour shifts"),
                            onClick = {
                                cycle = AppStore.ShiftCycle(3, AppStore.presetShifts(3), AppStore.emptyDayAssignments(), true, 30)
                            }
                        )
                    }
                }
            } else {
                // Each month has its own schedule and every month is editable.
                val viewedStartCal = (WorkCycleEngine.cycleStart(tY, tM, tD).clone() as Calendar).apply { add(Calendar.MONTH, viewedOffset) }
                val vY = viewedStartCal.get(Calendar.YEAR)
                val vM = viewedStartCal.get(Calendar.MONTH) + 1
                val vD = viewedStartCal.get(Calendar.DAY_OF_MONTH)
                val vKey = AppStore.cycleKey(vY, vM, vD)
                val viewedAssignments = schedules[vKey] ?: AppStore.emptyDayAssignments()
                val viewCycle = c.copy(dayAssignments = viewedAssignments)
                // Save an edited assignment list back to the viewed month.
                fun setViewed(updated: List<String?>) { schedules = schedules + (vKey to updated) }
                // Per-date cycle resolver for the today banner + upcoming preview.
                val cycleFor: (Int, Int, Int) -> AppStore.ShiftCycle = { yy, mm, dd ->
                    AppStore.cycleForDate(c, schedules, yy, mm, dd)
                }

                // ── Cycle navigation (review previous / next months) ──────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(plumSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, deepBorder, RoundedCornerShape(12.dp))
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("‹", fontSize = 22.sp, color = TraditionalGold, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewedOffset -= 1; weekQuickPick = null }.padding(horizontal = 14.dp, vertical = 4.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val vEnd = (viewedStartCal.clone() as Calendar).apply { add(Calendar.MONTH, 1); add(Calendar.DAY_OF_YEAR, -1) }
                            Text("${fmtDate(viewedStartCal)} – ${fmtDate(vEnd)}", fontSize = 13.sp, color = sandText, fontWeight = FontWeight.Bold)
                            Text(
                                when {
                                    viewedOffset == 0 -> tr(lang, "ខួបបច្ចុប្បន្ន · កែបាន", "Current cycle · editable")
                                    viewedOffset < 0 -> tr(lang, "ខួបមុន · កែបាន", "Past cycle · editable")
                                    else -> tr(lang, "ខួបខាងមុខ · កែបាន", "Upcoming cycle · editable")
                                },
                                fontSize = 9.sp, color = TraditionalGold, fontWeight = FontWeight.Bold
                            )
                        }
                        Text("›", fontSize = 22.sp, color = TraditionalGold, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { viewedOffset += 1; weekQuickPick = null }.padding(horizontal = 14.dp, vertical = 4.dp))
                    }
                }

                // ── Cycle stats (worked days · hours · days off) ──────────────
                item {
                    val statsEnd = (viewedStartCal.clone() as Calendar).apply { add(Calendar.MONTH, 1); add(Calendar.DAY_OF_YEAR, -1) }
                    val statsCtxStart = (viewedStartCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }
                    val startInt = vY * 10000 + vM * 100 + vD
                    val statDays = WorkCycleEngine.buildWorkDays(cycleFor, statsCtxStart, statsEnd)
                        .filter { it.year * 10000 + it.month * 100 + it.day >= startInt }
                    val worked = statDays.count { !it.blocked }
                    val totalHours = statDays.filterNot { it.blocked }.sumOf { (it.endMs - it.startMs) / 3_600_000.0 }
                    val blockedCount = statDays.count { it.blocked }
                    val cycleDays = (((statsEnd.timeInMillis - viewedStartCal.timeInMillis) / 86_400_000L) + 1).toInt()
                    val hoursLabel = if (totalHours % 1.0 == 0.0) "${totalHours.toInt()}" else "%.1f".format(totalHours)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(plumCard, RoundedCornerShape(12.dp))
                            .border(1.dp, TraditionalGold.copy(0.25f), RoundedCornerShape(12.dp))
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        CycleStat(num(lang, worked), tr(lang, "ថ្ងៃធ្វើការ", "Work days"))
                        CycleStat(numStr(lang, hoursLabel), tr(lang, "ម៉ោងសរុប", "Total hours"))
                        CycleStat(num(lang, (cycleDays - worked - blockedCount).coerceAtLeast(0)), tr(lang, "ថ្ងៃឈប់", "Days off"))
                        if (blockedCount > 0) {
                            CycleStat(num(lang, blockedCount), tr(lang, "⛔ គ្មានសម្រាក", "⛔ No rest"), CrimsonHoliday)
                        }
                    }
                }

                // ── Today's shift banner (current cycle only) ─────────────────
                if (viewedOffset == 0) item {
                    val ctxStart = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }
                    val wdToday = WorkCycleEngine.buildWorkDays(cycleFor, ctxStart, today)
                        .lastOrNull { it.year == tY && it.month == tM && it.day == tD }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                when {
                                    wdToday == null -> plumSurface
                                    wdToday.blocked -> CrimsonHoliday.copy(0.12f)
                                    else -> TraditionalGold.copy(0.12f)
                                },
                                RoundedCornerShape(14.dp)
                            )
                            .border(
                                1.dp,
                                when {
                                    wdToday == null -> deepBorder
                                    wdToday.blocked -> CrimsonHoliday.copy(0.5f)
                                    else -> TraditionalGold.copy(0.5f)
                                },
                                RoundedCornerShape(14.dp)
                            )
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            when {
                                wdToday == null -> "🛌"
                                wdToday.shift.isOvernight -> "🌙"
                                else -> "☀️"
                            },
                            fontSize = 26.sp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("វេនថ្ងៃនេះ (Today)", "Today's shift"), fontSize = 10.sp, color = goldSubText, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Text(
                                when {
                                    wdToday == null -> tr(lang, "ថ្ងៃឈប់សម្រាក", "Day off")
                                    wdToday.blocked -> tr(lang, "${wdToday.shift.name} · ត្រូវបានទប់ (គ្មានសម្រាក)", "${wdToday.shift.name} · blocked (no rest)")
                                    else -> "${wdToday.shift.name} · %02d:%02d → %02d:%02d".format(
                                        wdToday.shift.startHour, wdToday.shift.startMin, wdToday.shift.endHour, wdToday.shift.endMin
                                    )
                                },
                                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                color = if (wdToday?.blocked == true) CrimsonHoliday else sandText
                            )
                        }
                    }
                }

                // ── System type switch ────────────────────────────────────────
                item {
                    SectionLabel(tr("ប្រព័ន្ធវេន (SHIFT SYSTEM)", "SHIFT SYSTEM"))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(2 to tr("២ វេន", "2 shifts"), 3 to tr("៣ វេន", "3 shifts")).forEach { (t, label) ->
                            val active = c.systemType == t
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) TraditionalGold else plumSurface)
                                    .border(1.dp, if (active) TraditionalGold else deepBorder, RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (!active) {
                                            // Switching systems changes the shift ids, so it clears every
                                            // month. Confirm only when some month actually has a schedule.
                                            val hasAny = schedules.values.any { row -> row.any { it != null } }
                                            if (hasAny) {
                                                pendingSystemType = t
                                            } else {
                                                cycle = c.copy(systemType = t, shifts = AppStore.presetShifts(t), dayAssignments = AppStore.emptyDayAssignments())
                                                schedules = emptyMap()
                                            }
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 12.sp, color = if (active) OnAccent else sandText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // ── Editable shift templates ──────────────────────────────────
                item { SectionLabel(tr("វេន (SHIFTS · ចុចដើម្បីកែម៉ោង)", "SHIFTS · tap to edit time")) }
                items(c.shifts) { shift ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(plumSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, TraditionalGold.copy(0.25f), RoundedCornerShape(12.dp))
                            .clickable { editingShift = shift }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(if (shift.isOvernight) "🌙" else "☀️", fontSize = 20.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(shift.name.ifBlank { tr(lang, "វេន", "Shift") }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = sandText)
                            Text(
                                "%02d:%02d → %02d:%02d".format(shift.startHour, shift.startMin, shift.endHour, shift.endMin) +
                                    if (shift.isOvernight) tr(lang, " (ឆ្លងយប់)", " (overnight)") else "",
                                fontSize = 12.sp, color = TraditionalGold, fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text("✏️", fontSize = 16.sp)
                    }
                }

                // ── Per-day schedule (fully customisable) ─────────────────────
                val cycleStartCal = viewedStartCal
                val cycleEndCal = (cycleStartCal.clone() as Calendar).apply { add(Calendar.MONTH, 1); add(Calendar.DAY_OF_YEAR, -1) }
                val cycleLen = (((cycleEndCal.timeInMillis - cycleStartCal.timeInMillis) / 86_400_000L) + 1).toInt().coerceIn(1, AppStore.CYCLE_SLOTS)
                // No-rest markers: resolve per date (2-day lookback) so a shift on the
                // 26th still sees the previous month's last shift.
                val blockCtxStart = (cycleStartCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }
                val blockedDays = WorkCycleEngine.buildWorkDays(cycleFor, blockCtxStart, cycleEndCal)
                    .filter { it.blocked }
                    .map { it.year * 10000 + it.month * 100 + it.day }
                    .toSet()
                // Day offsets covered by a 0-based week. Weeks 0–2 are 7 days; week 3
                // (the 4th) absorbs everything to the 25th, so it can hold >7 days.
                fun weekDayRange(weekIdx: Int): IntRange {
                    val start = weekIdx * 7
                    val end = if (weekIdx >= 3) cycleLen - 1 else (start + 6).coerceAtMost(cycleLen - 1)
                    return start..end
                }
                // Assign one shift (or Off) to every day of a whole week of the viewed month.
                val applyWeek: (Int, String?) -> Unit = { weekIdx, shiftId ->
                    setViewed(viewedAssignments.toMutableList().also { list ->
                        for (i in weekDayRange(weekIdx)) if (i in list.indices) list[i] = shiftId
                    })
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionLabel(tr("កាលវិភាគប្រចាំថ្ងៃ (DAILY SCHEDULE)", "DAILY SCHEDULE"))
                        // One-tap reuse of last cycle's pattern when it has any shifts.
                        val prevStartCal = (viewedStartCal.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                        val prevKey = AppStore.cycleKey(
                            prevStartCal.get(Calendar.YEAR),
                            prevStartCal.get(Calendar.MONTH) + 1,
                            prevStartCal.get(Calendar.DAY_OF_MONTH)
                        )
                        val prevAssignments = schedules[prevKey]
                        if (prevAssignments?.any { it != null } == true) {
                            Text(
                                tr(lang, "⧉ ចម្លងខួបមុន", "⧉ Copy last cycle"),
                                fontSize = 9.sp, color = TraditionalGold, fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        setViewed(prevAssignments)
                                        Toast.makeText(context, tr(lang, "បានចម្លងកាលវិភាគខួបមុន", "Copied last cycle's schedule"), Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        tr(lang, "ប្ដូរវេនបានគ្រប់ថ្ងៃ · ខែនេះមានកាលវិភាគផ្ទាល់ខ្លួន", "Tap any day to set its shift · this month has its own schedule"),
                        fontSize = 10.sp, color = dimColor, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items((0 until cycleLen).toList()) { offset ->
                    val cal = (cycleStartCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
                    val y = cal.get(Calendar.YEAR); val m = cal.get(Calendar.MONTH) + 1; val d = cal.get(Calendar.DAY_OF_MONTH)
                    val dow = cal.get(Calendar.DAY_OF_WEEK)
                    val isToday = y == tY && m == tM && d == tD
                    val isWeekend = dow == Calendar.SUNDAY || dow == Calendar.SATURDAY
                    val currentId = viewCycle.shiftIdForDay(offset)
                    val blocked = (y * 10000 + m * 100 + d) in blockedDays

                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        // Week header above the first day of each of the four weeks.
                        // Only weeks 1–4 get a header (offsets 0/7/14/21); the 4th week
                        // absorbs every day to the 25th, so no spurious "Week 5" appears.
                        if (offset % 7 == 0 && offset <= 21) {
                            val weekIdx = offset / 7          // 0..3
                            val wk = weekIdx + 1
                            val isLongWeek = weekIdx == 3
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = if (offset == 0) 0.dp else 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    tr(lang,
                                        if (isLongWeek) "— សប្តាហ៍ ${num(lang, wk)} (ដល់ ២៥) —" else "— សប្តាហ៍ ${num(lang, wk)} —",
                                        if (isLongWeek) "— Week $wk (to 25th) —" else "— Week $wk —"),
                                    fontSize = 9.sp, color = goldSubText, fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (weekQuickPick == weekIdx) tr(lang, "បិទ", "Close") else tr(lang, "កំណត់ទាំងសប្តាហ៍", "Set week"),
                                    fontSize = 9.sp, color = TraditionalGold, fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { weekQuickPick = if (weekQuickPick == weekIdx) null else weekIdx }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            // Quick-assign one shift to the whole week (incl. the long 4th week).
                            if (weekQuickPick == weekIdx) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp)
                                ) {
                                    ShiftChip(tr(lang, "ឈប់", "Off"), false, dimColor) { applyWeek(weekIdx, null); weekQuickPick = null }
                                    viewCycle.shifts.forEach { s ->
                                        ShiftChip(s.name, false, if (s.isOvernight) LotusPink else TraditionalGold) {
                                            applyWeek(weekIdx, s.id); weekQuickPick = null
                                        }
                                    }
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isToday) TraditionalGold.copy(0.10f) else plumCard, RoundedCornerShape(12.dp))
                                .border(1.dp, if (isToday) TraditionalGold.copy(0.6f) else deepBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.width(54.dp)) {
                                Text(
                                    weekdayLabels(lang).getOrElse(dow - 1) { "" },
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    color = if (isWeekend) CrimsonHoliday else sandText
                                )
                                Text("${num(lang, d)} ${gregMonth(lang, m - 1).take(3)}", fontSize = 9.sp, color = goldSubText)
                                if (blocked) Text("⛔", fontSize = 9.sp, color = CrimsonHoliday)
                            }
                            Spacer(Modifier.width(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
                            ) {
                                ShiftChip(tr(lang, "ឈប់", "Off"), currentId == null, dimColor) {
                                    setViewed(viewedAssignments.toMutableList().also { if (offset in it.indices) it[offset] = null })
                                }
                                viewCycle.shifts.forEach { s ->
                                    ShiftChip(s.name, currentId == s.id, if (s.isOvernight) LotusPink else TraditionalGold) {
                                        setViewed(viewedAssignments.toMutableList().also { if (offset in it.indices) it[offset] = s.id })
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Reminder settings ─────────────────────────────────────────
                item {
                    SectionLabel(tr("ការរំលឹក (REMINDERS)", "REMINDERS"))
                    Spacer(Modifier.height(6.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(plumSurface, RoundedCornerShape(12.dp))
                            .border(1.dp, deepBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tr("រំលឹកមុនចូលវេន", "Remind before each shift"), fontSize = 12.sp, color = sandText)
                            Switch(
                                checked = c.remind,
                                onCheckedChange = { cycle = c.copy(remind = it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = TraditionalGold, checkedTrackColor = TraditionalGold.copy(0.4f))
                            )
                        }
                        if (c.remind) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(15, 30, 60, 90).forEach { mins ->
                                    ShiftChip(tr(lang, "$mins នាទី", "$mins min"), c.reminderMinutesBefore == mins, TraditionalGold) {
                                        cycle = c.copy(reminderMinutesBefore = mins)
                                    }
                                }
                            }
                        }
                    }
                }

                // ── Upcoming worked days preview (with no-rest blocks) ────────
                item { SectionLabel(tr("វេនខាងមុខ (UPCOMING SHIFTS)", "UPCOMING SHIFTS")) }
                run {
                    val from = (today.clone() as Calendar)
                    val to = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 13) }
                    val preview = WorkCycleEngine.buildWorkDays(cycleFor, from, to)
                    if (preview.isEmpty()) {
                        item {
                            Text(
                                tr("មិនមានវេនកំណត់ក្នុង ១៤ ថ្ងៃខាងមុខ", "No shifts assigned in the next 14 days"),
                                fontSize = 11.sp, color = dimColor, modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        items(preview) { wd ->
                            val dow = Calendar.getInstance().apply { set(wd.year, wd.month - 1, wd.day) }.get(Calendar.DAY_OF_WEEK)
                            val dowLabel = weekdayLabels(lang).getOrElse(dow - 1) { "" }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(plumSurface, RoundedCornerShape(10.dp))
                                    .border(1.dp, (if (wd.blocked) CrimsonHoliday else TraditionalGold).copy(0.3f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(if (wd.shift.isOvernight) "🌙" else "☀️", fontSize = 16.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "$dowLabel ${num(lang, wd.day)} ${gregMonth(lang, wd.month - 1)} · ${wd.shift.name}",
                                        fontSize = 12.sp, color = sandText, fontWeight = FontWeight.Bold,
                                        textDecoration = if (wd.blocked) TextDecoration.LineThrough else null
                                    )
                                    Text(
                                        "%02d:%02d → %02d:%02d".format(wd.shift.startHour, wd.shift.startMin, wd.shift.endHour, wd.shift.endMin),
                                        fontSize = 10.sp, color = if (wd.blocked) CrimsonHoliday else TraditionalGold
                                    )
                                }
                                if (wd.blocked) {
                                    Text(tr(lang, "⛔ គ្មានសម្រាក", "⛔ no rest"), fontSize = 9.sp, color = CrimsonHoliday, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // ── Save / delete ─────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            AppStore.saveShiftCycle(context, c)
                            AppStore.saveMonthlySchedules(context, schedules)
                            if (c.remind && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            WorkScheduleScheduler.sync(context)
                            syncWorkScheduleToDatabase(c, schedules)
                            scope.launch { WidgetPrefs.refresh(context) }
                            Toast.makeText(context, tr(lang, "បានរក្សាទុកកាលវិភាគ", "Schedule saved"), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(tr("រក្សាទុក & បើកការរំលឹក", "Save & schedule reminders"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            val keysToClear = schedules.keys.toSet()
                            AppStore.clearShiftCycle(context)
                            AppStore.clearAllSchedules(context)
                            schedules = emptyMap()
                            cycle = null
                            WorkScheduleScheduler.sync(context)
                            syncClearWorkSchedulesFromDatabase(keysToClear)
                            scope.launch { WidgetPrefs.refresh(context) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonHoliday),
                        border = BorderStroke(1.dp, CrimsonHoliday.copy(0.5f))
                    ) {
                        Text(tr("លុបកាលវិភាគទាំងអស់", "Delete all schedules"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
}
}

@Composable
private fun SectionLabel(text: String) {
    val (_, _, _, _, _, _, _, _, dimColor) = LocalAppColors.current
    Text(text, fontSize = 10.sp, color = dimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
}

@Composable
private fun CycleStat(value: String, label: String, valueColor: Color? = null) {
    val (_, _, _, _, _, _, sandText, goldSubText, _) = LocalAppColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = valueColor ?: TraditionalGold)
        Text(label, fontSize = 9.sp, color = goldSubText)
    }
}

@Composable
private fun SystemSetupCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    val (_, _, plumSurface, _, deepBorder, _, sandText, goldSubText, _) = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(plumSurface, RoundedCornerShape(14.dp))
            .border(1.dp, TraditionalGold.copy(0.3f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(emoji, fontSize = 28.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = sandText)
            Text(subtitle, fontSize = 10.sp, color = goldSubText)
        }
        Text("›", fontSize = 22.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ShiftChip(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    val (nightBlack, _, plumSurface, _, deepBorder, _, sandText, _, _) = LocalAppColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) activeColor else plumSurface)
            .border(1.dp, if (active) activeColor else deepBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(label, fontSize = 11.sp, color = if (active) OnAccent else sandText, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ShiftTimeEditor(
    shift: AppStore.ShiftDef,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (AppStore.ShiftDef) -> Unit
) {
    val (nightBlack, _, plumSurface, plumCard, deepBorder, _, sandText, goldSubText, _) = LocalAppColors.current
    val context = LocalContext.current

    var name by remember { mutableStateOf(shift.name) }
    var startHour by remember { mutableIntStateOf(shift.startHour) }
    var startMin by remember { mutableIntStateOf(shift.startMin) }
    var endHour by remember { mutableIntStateOf(shift.endHour) }
    var endMin by remember { mutableIntStateOf(shift.endMin) }

    fun pick(h: Int, m: Int, onPicked: (Int, Int) -> Unit) {
        TimePickerDialog(context, { _, hh, mm -> onPicked(hh, mm) }, h, m, true).show()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = plumCard,
            border = BorderStroke(1.dp, TraditionalGold.copy(0.4f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(tr("កែវេន", "Edit Shift"), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TraditionalGold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(tr("ឈ្មោះវេន", "Shift name"), fontSize = 11.sp) },
                    textStyle = TextStyle(fontSize = 13.sp, color = sandText),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TraditionalGold)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    EditorTimeField(tr("ចាប់ផ្តើម", "Start"), "%02d:%02d".format(startHour, startMin), Modifier.weight(1f)) {
                        pick(startHour, startMin) { h, m -> startHour = h; startMin = m }
                    }
                    EditorTimeField(tr("បញ្ចប់", "End"), "%02d:%02d".format(endHour, endMin), Modifier.weight(1f)) {
                        pick(endHour, endMin) { h, m -> endHour = h; endMin = m }
                    }
                }
                Button(
                    onClick = { onSave(shift.copy(name = name.trim().ifBlank { shift.name }, startHour = startHour, startMin = startMin, endHour = endHour, endMin = endMin)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold)
                ) {
                    Text(tr("រក្សាទុក", "Save"), color = OnAccent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EditorTimeField(label: String, time: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (_, _, plumSurface, _, deepBorder, _, sandText, goldSubText, _) = LocalAppColors.current
    Column(modifier = modifier) {
        Text(label, fontSize = 10.sp, color = goldSubText, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(plumSurface)
                .border(1.dp, deepBorder, RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("🕐 $time", fontSize = 14.sp, color = sandText, fontWeight = FontWeight.Bold)
        }
    }
}
