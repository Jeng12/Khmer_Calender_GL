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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.alarm.WorkScheduleScheduler
import com.example.core.*
import com.example.data.AppStore
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

    var version by remember { mutableIntStateOf(0) }
    val shifts = remember(version) { AppStore.getShifts(context) }

    var showEditor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AppStore.WorkShift?>(null) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled implicitly — alarm still schedules either way */ }

    fun resync() {
        WorkScheduleScheduler.sync(context)
        version++
        scope.launch { WidgetPrefs.refresh(context) }
    }

    if (showEditor) {
        ShiftEditorDialog(
            initial = editing,
            lang = lang,
            onDismiss = { showEditor = false },
            onSave = { shift ->
                AppStore.upsertShift(context, shift)
                if (shift.remind && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                ) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                showEditor = false
                resync()
                Toast.makeText(context, tr(lang, "បានរក្សាទុកវេនការងារ", "Shift saved"), Toast.LENGTH_SHORT).show()
            },
            onDelete = { id ->
                AppStore.deleteShift(context, id)
                showEditor = false
                resync()
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
                    Text(tr("កំណត់វេន និងទទួលការរំលឹកមុនពេលចូលធ្វើការ", "Set weekly shifts and get reminders before work"), fontSize = 10.sp, color = LotusPink)
                }
            }

            if (shifts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🗓️", fontSize = 40.sp)
                        Text(tr("មិនទាន់មានវេនការងារ", "No shifts yet"), fontSize = 13.sp, color = sandText, fontWeight = FontWeight.Bold)
                        Text(tr("ចុចប៊ូតុង + ដើម្បីបន្ថែមវេនដំបូង", "Tap + to add your first shift"), fontSize = 11.sp, color = dimColor)
                    }
                }
            } else {
                items(shifts) { shift ->
                    ShiftCard(
                        shift = shift,
                        lang = lang,
                        onClick = { editing = shift; showEditor = true },
                        onToggleRemind = { enabled ->
                            AppStore.upsertShift(context, shift.copy(remind = enabled))
                            resync()
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }
        }

        // Add FAB
        FloatingActionButton(
            onClick = { editing = null; showEditor = true },
            containerColor = TraditionalGold,
            contentColor = nightBlack,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Text("+", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ShiftCard(
    shift: AppStore.WorkShift,
    lang: AppLanguage,
    onClick: () -> Unit,
    onToggleRemind: (Boolean) -> Unit
) {
    val (_, _, plumSurface, _, deepBorder, _, sandText, goldSubText, dimColor) = LocalAppColors.current
    val dayLabels = weekdayLabels(lang) // Sunday-first
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(plumSurface, RoundedCornerShape(14.dp))
            .border(1.dp, TraditionalGold.copy(0.25f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(TraditionalGold.copy(0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Text("💼", fontSize = 20.sp) }

        Column(modifier = Modifier.weight(1f)) {
            Text(shift.label.ifBlank { tr(lang, "វេនការងារ", "Work shift") }, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = sandText)
            Text(
                "%02d:%02d – %02d:%02d".format(shift.startHour, shift.startMin, shift.endHour, shift.endMin),
                fontSize = 12.sp, color = TraditionalGold, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0..6) {
                    val cal = i + 1 // Calendar.SUNDAY = 1
                    val active = cal in shift.daysOfWeek
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (active) TraditionalGold.copy(0.85f) else Color.Transparent)
                            .border(1.dp, if (active) TraditionalGold else deepBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(dayLabels[i].take(2), fontSize = 8.sp, color = if (active) Color.Black else dimColor, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (shift.remind) {
                Text(
                    tr(lang, "🔔 រំលឹក ${shift.reminderMinutesBefore} នាទីមុន", "🔔 Remind ${shift.reminderMinutesBefore} min before"),
                    fontSize = 9.sp, color = goldSubText, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Switch(
            checked = shift.remind,
            onCheckedChange = onToggleRemind,
            colors = SwitchDefaults.colors(checkedThumbColor = TraditionalGold, checkedTrackColor = TraditionalGold.copy(0.4f))
        )
    }
}

@Composable
private fun ShiftEditorDialog(
    initial: AppStore.WorkShift?,
    lang: AppLanguage,
    onDismiss: () -> Unit,
    onSave: (AppStore.WorkShift) -> Unit,
    onDelete: (String) -> Unit
) {
    val (nightBlack, _, plumSurface, plumCard, deepBorder, _, sandText, goldSubText, dimColor) = LocalAppColors.current
    val context = LocalContext.current

    var label by remember { mutableStateOf(initial?.label ?: "") }
    var days by remember { mutableStateOf(initial?.daysOfWeek?.toSet() ?: setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)) }
    var startHour by remember { mutableIntStateOf(initial?.startHour ?: 8) }
    var startMin by remember { mutableIntStateOf(initial?.startMin ?: 0) }
    var endHour by remember { mutableIntStateOf(initial?.endHour ?: 17) }
    var endMin by remember { mutableIntStateOf(initial?.endMin ?: 0) }
    var remind by remember { mutableStateOf(initial?.remind ?: true) }
    var minutesBefore by remember { mutableIntStateOf(initial?.reminderMinutesBefore ?: 30) }

    val dayLabels = weekdayLabels(lang)

    fun pickTime(initialHour: Int, initialMin: Int, onPicked: (Int, Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onPicked(h, m) }, initialHour, initialMin, true).show()
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
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        if (initial == null) tr("បន្ថែមវេនការងារ", "Add Shift") else tr("កែវេនការងារ", "Edit Shift"),
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TraditionalGold
                    )
                }

                item {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr("ឈ្មោះវេន (ឧ. វេនព្រឹក)", "Shift name (e.g. Morning)"), fontSize = 11.sp) },
                        textStyle = TextStyle(fontSize = 13.sp, color = sandText),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TraditionalGold)
                    )
                }

                // Day selector
                item {
                    Text(tr("ថ្ងៃ", "Days"), fontSize = 11.sp, color = goldSubText, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        for (i in 0..6) {
                            val cal = i + 1
                            val active = cal in days
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (active) TraditionalGold else plumSurface)
                                    .border(1.dp, if (active) TraditionalGold else deepBorder, RoundedCornerShape(8.dp))
                                    .clickable { days = if (active) days - cal else days + cal },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(dayLabels[i], fontSize = 10.sp, color = if (active) nightBlack else sandText, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Times
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TimeField(
                            label = tr("ចាប់ផ្តើម", "Start"),
                            time = "%02d:%02d".format(startHour, startMin),
                            modifier = Modifier.weight(1f),
                            onClick = { pickTime(startHour, startMin) { h, m -> startHour = h; startMin = m } }
                        )
                        TimeField(
                            label = tr("បញ្ចប់", "End"),
                            time = "%02d:%02d".format(endHour, endMin),
                            modifier = Modifier.weight(1f),
                            onClick = { pickTime(endHour, endMin) { h, m -> endHour = h; endMin = m } }
                        )
                    }
                }

                // Reminder toggle
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr("ការរំលឹក", "Reminder"), fontSize = 12.sp, color = sandText)
                        Switch(
                            checked = remind,
                            onCheckedChange = { remind = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = TraditionalGold, checkedTrackColor = TraditionalGold.copy(0.4f))
                        )
                    }
                }
                if (remind) {
                    item {
                        Text(tr("រំលឹកមុនពេល", "Remind before"), fontSize = 11.sp, color = goldSubText, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(10, 15, 30, 60).forEach { mins ->
                                val active = minutesBefore == mins
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (active) TraditionalGold else plumSurface)
                                        .border(1.dp, if (active) TraditionalGold else deepBorder, RoundedCornerShape(20.dp))
                                        .clickable { minutesBefore = mins }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        tr(lang, "$mins នាទី", "$mins min"),
                                        fontSize = 10.sp, color = if (active) nightBlack else sandText, fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (initial != null) {
                            OutlinedButton(
                                onClick = { onDelete(initial.id) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonHoliday),
                                border = BorderStroke(1.dp, CrimsonHoliday.copy(0.6f))
                            ) {
                                Text(tr("លុប", "Delete"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Button(
                            onClick = {
                                if (days.isEmpty()) {
                                    Toast.makeText(context, tr(lang, "សូមជ្រើសរើសយ៉ាងហោចណាស់មួយថ្ងៃ", "Pick at least one day"), Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                onSave(
                                    AppStore.WorkShift(
                                        id = initial?.id ?: "shift_${System.currentTimeMillis()}",
                                        label = label.trim(),
                                        daysOfWeek = days.toList().sorted(),
                                        startHour = startHour, startMin = startMin,
                                        endHour = endHour, endMin = endMin,
                                        remind = remind,
                                        reminderMinutesBefore = minutesBefore
                                    )
                                )
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold)
                        ) {
                            Text(tr("រក្សាទុក", "Save"), color = nightBlack, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeField(label: String, time: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
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
