package com.example.ui.tabs

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.alarm.WorkScheduleScheduler
import com.example.core.*
import com.example.data.AppStore
import com.example.data.WorkCycleEngine
import com.example.ui.theme.*
import com.example.widget.WidgetPrefs
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

    val today = remember { Calendar.getInstance() }
    val tY = today.get(Calendar.YEAR); val tM = today.get(Calendar.MONTH) + 1; val tD = today.get(Calendar.DAY_OF_MONTH)

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun fmtDate(cal: Calendar) =
        "${num(lang, cal.get(Calendar.DAY_OF_MONTH))} ${gregMonth(lang, cal.get(Calendar.MONTH))}"

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

    Box(modifier = Modifier.fillMaxSize().background(nightBlack)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(tr("កាលវិភាគការងារ (Work Schedule)", "Work Schedule"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                    Text(tr("ប្រព័ន្ធវេនវិលជុំ · ខួប ២៥ ដល់ ២៥", "Rotating shifts · 25th-to-25th cycle"), fontSize = 10.sp, color = LotusPink)
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
                                cycle = AppStore.ShiftCycle(2, AppStore.presetShifts(2), List(4) { null }, true, 30)
                            }
                        )
                        SystemSetupCard(
                            emoji = "🕗",
                            title = tr("ប្រព័ន្ធ ៣ វេន", "3-shift system"),
                            subtitle = tr("បីវេនស្មើគ្នា ៨ ម៉ោង", "Three equal 8-hour shifts"),
                            onClick = {
                                cycle = AppStore.ShiftCycle(3, AppStore.presetShifts(3), List(4) { null }, true, 30)
                            }
                        )
                    }
                }
            } else {
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
                                        if (!active) cycle = c.copy(systemType = t, shifts = AppStore.presetShifts(t), weekAssignments = List(4) { null })
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 12.sp, color = if (active) nightBlack else sandText, fontWeight = FontWeight.Bold)
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

                // ── Weekly rotation ───────────────────────────────────────────
                item {
                    val start = WorkCycleEngine.cycleStart(tY, tM, tD)
                    val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1); add(Calendar.DAY_OF_YEAR, -1) }
                    SectionLabel(tr("វេនប្រចាំសប្តាហ៍ (WEEKLY ROTATION)", "WEEKLY ROTATION"))
                    Text(
                        tr(lang, "ខួបបច្ចុប្បន្ន៖ ${fmtDate(start)} – ${fmtDate(end)}", "This cycle: ${fmtDate(start)} – ${fmtDate(end)}"),
                        fontSize = 10.sp, color = dimColor, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items((0..3).toList()) { wi ->
                    val (ws, we) = WorkCycleEngine.weekRange(tY, tM, tD, wi)
                    val currentId = c.weekAssignments.getOrNull(wi)
                    val isCurrentWeek = WorkCycleEngine.weekIndex(tY, tM, tD) == wi
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(plumCard, RoundedCornerShape(12.dp))
                            .border(1.dp, if (isCurrentWeek) TraditionalGold.copy(0.6f) else deepBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(tr(lang, "សប្តាហ៍ ${num(lang, wi + 1)}", "Week ${wi + 1}"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = sandText)
                            Spacer(Modifier.width(8.dp))
                            Text("${fmtDate(ws)} – ${fmtDate(we)}", fontSize = 10.sp, color = goldSubText)
                            if (isCurrentWeek) {
                                Spacer(Modifier.width(6.dp))
                                Text(tr(lang, "• ឥឡូវ", "• now"), fontSize = 9.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            ShiftChip(tr(lang, "ឈប់", "Off"), currentId == null, dimColor) {
                                cycle = c.copy(weekAssignments = c.weekAssignments.toMutableList().also { it[wi] = null })
                            }
                            c.shifts.forEach { s ->
                                ShiftChip(s.name, currentId == s.id, TraditionalGold) {
                                    cycle = c.copy(weekAssignments = c.weekAssignments.toMutableList().also { it[wi] = s.id })
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
                    val preview = WorkCycleEngine.buildWorkDays(c, from, to)
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

                // ── Save / clear ──────────────────────────────────────────────
                item {
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            AppStore.saveShiftCycle(context, c)
                            if (c.remind && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            WorkScheduleScheduler.sync(context)
                            scope.launch { WidgetPrefs.refresh(context) }
                            Toast.makeText(context, tr(lang, "បានរក្សាទុកកាលវិភាគ", "Schedule saved"), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(tr("រក្សាទុក & បើកការរំលឹក", "Save & schedule reminders"), color = nightBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            AppStore.clearShiftCycle(context)
                            WorkScheduleScheduler.sync(context)
                            cycle = null
                            scope.launch { WidgetPrefs.refresh(context) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonHoliday),
                        border = BorderStroke(1.dp, CrimsonHoliday.copy(0.5f))
                    ) {
                        Text(tr("លុបកាលវិភាគ", "Delete schedule"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
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
        Text(label, fontSize = 11.sp, color = if (active) nightBlack else sandText, fontWeight = FontWeight.Bold)
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
                    Text(tr("រក្សាទុក", "Save"), color = nightBlack, fontWeight = FontWeight.Bold)
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
