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
import androidx.compose.foundation.selection.selectable
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
import androidx.activity.result.PickVisualMediaRequest
import com.aistudio.khmercalendar.data.AppStore
import com.aistudio.khmercalendar.data.ScheduleImageParser
import com.aistudio.khmercalendar.data.ScheduleImageReader
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
    // Marked days off (annual leave, holiday), keyed like the schedules.
    var marks by remember { mutableStateOf(AppStore.getDayMarks(context)) }
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

    // ── Import a schedule from a roster photo ────────────────────────────────
    // The cycle the picture is being imported into (captured when the button is
    // tapped), whether the photo is being read, and the result awaiting review.
    var importTarget by remember { mutableStateOf<ImportTarget?>(null) }
    var isReadingPicture by remember { mutableStateOf(false) }
    var importPreview by remember { mutableStateOf<ImportPreview?>(null) }
    var rosterSetup by remember { mutableStateOf<RosterSetup?>(null) }
    val pictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val target = importTarget
        val shifts = cycle?.shifts.orEmpty()
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        isReadingPicture = true
        scope.launch {
            val tokens = ScheduleImageReader.readTokens(context, uri)
            isReadingPicture = false
            // A staff roster (one row per person) needs the user to say which row
            // is theirs and what its codes mean before it can be laid on the cycle.
            val roster = tokens.getOrNull()?.let(ScheduleImageParser::parseRoster)
            if (roster != null) {
                rosterSetup = RosterSetup(target, roster)
                return@launch
            }
            val readings = tokens.map { ScheduleImageParser.parse(it, shifts) }
            val preview = readings.getOrNull()?.let { buildImportPreview(target, it) }
            when {
                readings.isFailure -> Toast.makeText(
                    context,
                    tr(lang, "មិនអាចអានរូបភាពបានទេ", "Could not read that picture"),
                    Toast.LENGTH_LONG
                ).show()
                preview == null || preview.detected.isEmpty() -> Toast.makeText(
                    context,
                    tr(lang,
                        "រកមិនឃើញកាលបរិច្ឆេទ និងវេនក្នុងរូបភាពទេ។ សូមប្រើរូបច្បាស់ដែលមានថ្ងៃទី និងកូដវេន (D, N, OFF)។",
                        "No dates with shifts were found. Use a clear photo showing day numbers with shift codes (D, N, OFF)."),
                    Toast.LENGTH_LONG
                ).show()
                else -> importPreview = preview
            }
        }
    }
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

    importPreview?.let { preview ->
        ImportReviewDialog(
            preview = preview,
            shifts = cycle?.shifts.orEmpty(),
            lang = lang,
            onDismiss = { importPreview = null },
            onApply = { assignments, dayMarks ->
                val updated = schedules + (preview.target.key to assignments)
                schedules = updated
                val updatedMarks = marks + (preview.target.key to dayMarks)
                marks = updatedMarks
                AppStore.saveDayMarks(context, updatedMarks)
                importPreview = null
                // Save straight away so the imported shifts show on the Calendar
                // tab and the widgets without a second step.
                cycle?.let { c ->
                    AppStore.saveShiftCycle(context, c)
                    AppStore.saveMonthlySchedules(context, updated)
                    WorkScheduleScheduler.sync(context)
                    syncWorkScheduleToDatabase(c, updated)
                    scope.launch { WidgetPrefs.refresh(context) }
                }
                Toast.makeText(
                    context,
                    tr(lang, "បានបញ្ចូល និងរក្សាទុក · មើលក្នុងប្រតិទិន", "Imported and saved · it now shows in the Calendar"),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
    rosterSetup?.let { setup ->
        RosterSetupDialog(
            setup = setup,
            shifts = cycle?.shifts.orEmpty(),
            lang = lang,
            savedRowKey = remember(setup) { AppStore.getRosterRowKey(context) },
            savedMeanings = remember(setup) { AppStore.getRosterCodeMeanings(context) },
            onDismiss = { rosterSetup = null },
            onContinue = { row, meanings ->
                AppStore.saveRosterImportProfile(context, row.key, meanings)
                // A roster "8" needs an 8-hour day shift: add it the first time it is used.
                val eightHour = AppStore.EIGHT_HOUR_DAY_SHIFT
                if (eightHour.id in meanings.values && cycle?.shifts?.none { it.id == eightHour.id } == true) {
                    cycle = cycle?.let { it.copy(shifts = it.shifts + eightHour) }
                }
                val validIds = cycle?.shifts.orEmpty().map { it.id }.toSet()
                val readings = row.codes.mapValues { (_, code) ->
                    val meaning = meanings[code].orEmpty()
                    when {
                        meaning.startsWith(MARK_PREFIX) -> ScheduleImageParser.Reading.Marked(meaning.removePrefix(MARK_PREFIX))
                        meaning in validIds -> ScheduleImageParser.Reading.Shift(meaning)
                        else -> ScheduleImageParser.Reading.Off
                    }
                }
                rosterSetup = null
                importPreview = buildImportPreview(setup.target, readings)
            }
        )
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
                val viewedMarks = marks[vKey] ?: AppStore.emptyDayAssignments()
                // Set one day: a shift (or plain Off) clears any mark; a mark means no shift.
                fun setDay(offset: Int, shiftId: String?, mark: String?) {
                    setViewed(viewedAssignments.toMutableList().also { if (offset in it.indices) it[offset] = if (mark != null) null else shiftId })
                    marks = marks + (vKey to viewedMarks.toMutableList().also { if (offset in it.indices) it[offset] = mark })
                }
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
                        val leaveDays = viewedMarks.take(cycleDays).count { it == AppStore.MARK_ANNUAL_LEAVE }
                        if (leaveDays > 0) {
                            CycleStat(num(lang, leaveDays), tr(lang, "ច្បាប់ AL (បង់ ៨ម៉)", "Leave (8h paid)"), SkyBlue)
                        }
                        val holidayOffDays = viewedMarks.take(cycleDays).count { it == AppStore.MARK_HOLIDAY }
                        if (holidayOffDays > 0) {
                            CycleStat(num(lang, holidayOffDays), tr(lang, "បុណ្យ H (បង់ ៨ម៉)", "Holiday (8h paid)"), LotusPink)
                        }
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
                    marks = marks + (vKey to viewedMarks.toMutableList().also { list ->
                        for (i in weekDayRange(weekIdx)) if (i in list.indices) list[i] = null
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
                                        marks = marks - vKey   // leave and holidays are not part of a repeating pattern
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
                    // Fill this cycle from a photo of the roster (read on the device).
                    OutlinedButton(
                        onClick = {
                            importTarget = ImportTarget(vKey, cycleStartCal.timeInMillis, cycleLen, viewedAssignments, viewedMarks)
                            pictureLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isReadingPicture,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, TraditionalGold.copy(0.5f))
                    ) {
                        if (isReadingPicture) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = TraditionalGold
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(tr(lang, "កំពុងអានរូបភាព…", "Reading picture…"), fontSize = 13.sp, color = sandText)
                        } else {
                            Text(
                                tr(lang, "📷 បញ្ចូលកាលវិភាគពីរូបភាព", "📷 Import schedule from a picture"),
                                fontSize = 13.sp, color = sandText, fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
                                val mark = viewedMarks.getOrNull(offset)
                                ShiftChip(tr(lang, "ឈប់", "Off"), currentId == null && mark == null, dimColor) {
                                    setDay(offset, null, null)
                                }
                                viewCycle.shifts.forEach { s ->
                                    ShiftChip(s.name, currentId == s.id && mark == null, if (s.isOvernight) LotusPink else TraditionalGold) {
                                        setDay(offset, s.id, null)
                                    }
                                }
                                // Days off that still count: paid annual leave, public holiday.
                                ShiftChip(markChipLabel(lang, AppStore.MARK_ANNUAL_LEAVE), mark == AppStore.MARK_ANNUAL_LEAVE, SkyBlue) {
                                    setDay(offset, null, AppStore.MARK_ANNUAL_LEAVE)
                                }
                                ShiftChip(markChipLabel(lang, AppStore.MARK_HOLIDAY), mark == AppStore.MARK_HOLIDAY, LotusPink) {
                                    setDay(offset, null, AppStore.MARK_HOLIDAY)
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
                            AppStore.saveDayMarks(context, marks)
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
                            marks = emptyMap()
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


// ── Import from picture ─────────────────────────────────────────────────────

/** The cycle a roster photo is imported into. */
internal data class ImportTarget(
    val key: String,
    val cycleStartMs: Long,
    val cycleLen: Int,
    val currentAssignments: List<String?>,
    val currentMarks: List<String?> = emptyList()
)

/** The schedule read from a photo, laid over the cycle's current one, awaiting review. */
internal data class ImportPreview(
    val target: ImportTarget,
    val assignments: List<String?>,
    /** Day offsets within the cycle that were found in the picture. */
    val detected: Set<Int>,
    /** Marked days off (annual leave, holiday) per day offset; null = none. */
    val marks: List<String?> = List(AppStore.CYCLE_SLOTS) { null }
)

/** Prefix that tells a day mark apart from a shift id where both share one value. */
internal const val MARK_PREFIX = "@"

internal fun markChipLabel(lang: AppLanguage, mark: String): String = when (mark) {
    AppStore.MARK_ANNUAL_LEAVE -> tr(lang, "ច្បាប់ AL", "Leave AL")
    else -> tr(lang, "បុណ្យ H", "Holiday H")
}

/** A staff roster read from a photo, waiting for the user to pick their row. */
internal data class RosterSetup(val target: ImportTarget, val roster: ScheduleImageParser.Roster)

/**
 * First step for a staff roster: choose your own row, then confirm what each
 * code in it means. Both answers are remembered, so the next picture opens
 * with them already filled in.
 */
@Composable
internal fun RosterSetupDialog(
    setup: RosterSetup,
    shifts: List<AppStore.ShiftDef>,
    lang: AppLanguage,
    savedRowKey: String?,
    savedMeanings: Map<String, String>,
    onDismiss: () -> Unit,
    onContinue: (ScheduleImageParser.RosterRow, Map<String, String>) -> Unit
) {
    val colors = LocalAppColors.current
    val rows = setup.roster.rows
    var selected by remember(setup) {
        mutableIntStateOf(rows.indexOfFirst { it.key == savedRowKey }.coerceAtLeast(0))
    }
    val row = rows[selected]
    val codes = remember(row) { row.codes.values.distinct().sorted() }
    // The shifts on offer: the user's own, plus the 8-hour day shift a roster "8"
    // needs when it is not set up yet (it is added once the import is confirmed).
    val options = remember(shifts) {
        if (shifts.any { it.id == AppStore.EIGHT_HOUR_DAY_SHIFT.id || (!it.isOvernight && it.durationHours == 8) }) shifts
        else shifts + AppStore.EIGHT_HOUR_DAY_SHIFT
    }
    // code → shift id, "" for a plain day off, or "@AL"/"@H" for a marked day off.
    // Saved answers first, then a best guess.
    var meanings by remember(row) {
        val validIds = options.map { it.id }.toSet()
        mutableStateOf(codes.associateWith { code ->
            savedMeanings[code]?.takeIf { it.isEmpty() || it.startsWith(MARK_PREFIX) || it in validIds }
                ?: when (val guess = ScheduleImageParser.guessMeaning(code, codes.toSet(), shifts)) {
                    is ScheduleImageParser.Reading.Shift -> guess.shiftId
                    is ScheduleImageParser.Reading.Marked -> MARK_PREFIX + guess.mark
                    ScheduleImageParser.Reading.Off -> ""
                }
        })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                tr(lang, "បញ្ចូលពីតារាងវេន", "Import from roster"),
                color = colors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    tr(lang, "១. ជួរណាជារបស់អ្នក?", "1. Which row is yours?"),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.accentText
                )
                rows.forEachIndexed { index, r ->
                    val isSelected = index == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) TraditionalGold.copy(alpha = 0.18f) else colors.surface)
                            .border(1.dp, if (isSelected) TraditionalGold else colors.border, RoundedCornerShape(10.dp))
                            .selectable(selected = isSelected, onClick = { selected = index })
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected, onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = TraditionalGold, unselectedColor = colors.dim)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                r.label, fontSize = 13.sp, lineHeight = 17.sp, color = colors.text,
                                fontWeight = FontWeight.SemiBold, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                setup.roster.days.joinToString("  ") { r.codes[it] ?: "·" },
                                fontSize = 11.sp, color = colors.subText, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    tr(lang, "២. កូដនីមួយៗមានន័យអ្វី?", "2. What does each code mean?"),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.accentText
                )
                codes.forEach { code ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            code, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.text,
                            modifier = Modifier.width(46.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())
                        ) {
                            ShiftChip(tr(lang, "ឈប់", "Off"), meanings[code].isNullOrEmpty(), colors.dim) {
                                meanings = meanings + (code to "")
                            }
                            options.forEach { s ->
                                ShiftChip(s.name, meanings[code] == s.id, if (s.isOvernight) LotusPink else TraditionalGold) {
                                    meanings = meanings + (code to s.id)
                                }
                            }
                            listOf(AppStore.MARK_ANNUAL_LEAVE to SkyBlue, AppStore.MARK_HOLIDAY to LotusPink).forEach { (mark, tint) ->
                                ShiftChip(markChipLabel(lang, mark), meanings[code] == MARK_PREFIX + mark, tint) {
                                    meanings = meanings + (code to MARK_PREFIX + mark)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onContinue(row, meanings) },
                colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold)
            ) { Text(tr(lang, "បន្ត", "Continue"), color = OnAccent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(lang, "បោះបង់", "Cancel"), color = colors.subText) }
        }
    )
}

/** Maps what was read for each day of the month onto the cycle's day offsets. */
internal fun buildImportPreview(
    target: ImportTarget,
    readings: Map<Int, ScheduleImageParser.Reading>
): ImportPreview {
    val assignments = MutableList(AppStore.CYCLE_SLOTS) { target.currentAssignments.getOrNull(it) }
    val marks = MutableList(AppStore.CYCLE_SLOTS) { target.currentMarks.getOrNull(it) }
    val detected = HashSet<Int>()
    val cal = Calendar.getInstance()
    for (offset in 0 until target.cycleLen) {
        cal.timeInMillis = target.cycleStartMs
        cal.add(Calendar.DAY_OF_YEAR, offset)
        val reading = readings[cal.get(Calendar.DAY_OF_MONTH)] ?: continue
        assignments[offset] = (reading as? ScheduleImageParser.Reading.Shift)?.shiftId
        marks[offset] = (reading as? ScheduleImageParser.Reading.Marked)?.mark
        detected += offset
    }
    return ImportPreview(target, assignments, detected, marks)
}

/**
 * Lets the user check, and correct, what was read from the photo before it
 * replaces the cycle's schedule. Days found in the picture are outlined; tapping
 * any day steps it through Off, each shift, annual leave and holiday.
 */
@Composable
internal fun ImportReviewDialog(
    preview: ImportPreview,
    shifts: List<AppStore.ShiftDef>,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onApply: (assignments: List<String?>, marks: List<String?>) -> Unit
) {
    val colors = LocalAppColors.current
    // One value per day: null = off, a shift id, or "@AL"/"@H" for a marked day off.
    var assignments by remember(preview) {
        mutableStateOf(preview.assignments.mapIndexed { i, shiftId ->
            preview.marks.getOrNull(i)?.let { MARK_PREFIX + it } ?: shiftId
        })
    }
    val dayNumbers = remember(preview) {
        val cal = Calendar.getInstance()
        (0 until preview.target.cycleLen).map { offset ->
            cal.timeInMillis = preview.target.cycleStartMs
            cal.add(Calendar.DAY_OF_YEAR, offset)
            cal.get(Calendar.DAY_OF_MONTH)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.card,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                tr(lang, "ពិនិត្យកាលវិភាគដែលបានអាន", "Check the schedule that was read"),
                color = colors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    tr(lang,
                        "រកឃើញ ${num(lang, preview.detected.size)} ក្នុងចំណោម ${num(lang, preview.target.cycleLen)} ថ្ងៃ (មានស៊ុមមាស)។ ចុចលើថ្ងៃដើម្បីកែ។",
                        "Found ${preview.detected.size} of ${preview.target.cycleLen} days (gold outline). Tap a day to change it."),
                    fontSize = 12.sp, lineHeight = 17.sp, color = colors.subText
                )
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    dayNumbers.indices.chunked(7).forEach { week ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            week.forEach { offset ->
                                val value = assignments.getOrNull(offset)
                                val mark = value?.takeIf { it.startsWith(MARK_PREFIX) }?.removePrefix(MARK_PREFIX)
                                val shift = shifts.firstOrNull { it.id == value }
                                val tint = when {
                                    mark == AppStore.MARK_ANNUAL_LEAVE -> SkyBlue
                                    mark != null -> LotusPink
                                    shift == null -> colors.dim
                                    shift.isOvernight -> LotusPink
                                    else -> TraditionalGold
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (shift == null && mark == null) colors.surface else tint.copy(alpha = 0.18f))
                                        .border(
                                            1.dp,
                                            if (offset in preview.detected) TraditionalGold else colors.border,
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            // Off → each shift → annual leave → holiday → Off
                                            val order = listOf<String?>(null) + shifts.map { it.id } +
                                                listOf(MARK_PREFIX + AppStore.MARK_ANNUAL_LEAVE, MARK_PREFIX + AppStore.MARK_HOLIDAY)
                                            val next = order[(order.indexOf(assignments.getOrNull(offset)) + 1) % order.size]
                                            assignments = assignments.toMutableList().also { it[offset] = next }
                                        }
                                        .padding(vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        num(lang, dayNumbers[offset]),
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = colors.text
                                    )
                                    Text(
                                        mark ?: shift?.name ?: tr(lang, "ឈប់", "Off"),
                                        fontSize = 10.sp, lineHeight = 13.sp, maxLines = 1, softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        color = if (shift == null && mark == null) colors.dim else colors.text
                                    )
                                }
                            }
                            // Keep the last, shorter row aligned to the 7-column grid.
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(
                        assignments.map { v -> v?.takeIf { !it.startsWith(MARK_PREFIX) } },
                        assignments.map { v -> v?.takeIf { it.startsWith(MARK_PREFIX) }?.removePrefix(MARK_PREFIX) }
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold)
            ) { Text(tr(lang, "ប្រើកាលវិភាគនេះ", "Use this schedule"), color = OnAccent, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(lang, "បោះបង់", "Cancel"), color = colors.subText) }
        }
    )
}
