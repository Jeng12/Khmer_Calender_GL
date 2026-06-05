package com.example.ui.tabs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.example.calendar.*
import com.example.core.*
import com.example.alarm.*
import com.example.data.*
import com.example.ui.theme.*
import com.example.ui.components.*
import com.example.ui.navigation.*
import com.example.ui.auth.*
import com.example.ui.tabs.*
import com.example.widget.WidgetPrefs

// 2. CALENDAR TAB CONTAINER
@Composable
fun CalendarTabContent(
    year: Int,
    month: Int,
    selectedDay: Int,
    onMonthChange: (Int, Int) -> Unit,
    onDayChange: (Int) -> Unit,
    onGoToToday: () -> Unit = {}
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val context = LocalContext.current
    val widgetScope = rememberCoroutineScope()
    var swipeOffset by remember { mutableStateOf(0f) }
    // Focus on the current day each time the Calendar tab is opened
    LaunchedEffect(Unit) { onGoToToday() }
    // Keep the home-screen widgets in sync whenever this tab is shown
    LaunchedEffect(Unit) { WidgetPrefs.refresh(context) }

    // The actual current Gregorian date, used to highlight "today" in the grid
    val todayCal = remember { java.util.Calendar.getInstance() }
    val todayYear = todayCal.get(java.util.Calendar.YEAR)
    val todayMonth = todayCal.get(java.util.Calendar.MONTH) + 1
    val todayDay = todayCal.get(java.util.Calendar.DAY_OF_MONTH)
    // Memoize: recompute only when year/month changes
    val daysList = remember(year, month) { KhmerCalendarHelper.getGregorianMonthDays(year, month) }
    val startDayOfWeekSerial = KhmerCalendarHelper.getSerialDay(year, month, 1)
    val startOffset = ((startDayOfWeekSerial + 2) % 7 + 7) % 7

    val selectedKhmerDate = daysList.getOrNull(selectedDay - 1) ?: daysList.firstOrNull() ?: KhmerCalendarHelper.getKhmerDate(year, month, selectedDay)

    // Notes state
    val notesPrefs = remember { context.getSharedPreferences("khmer_calendar_notes", android.content.Context.MODE_PRIVATE) }
    var notesVersion by remember { mutableStateOf(0) }
    val daysWithNotes = remember(year, month, notesVersion) {
        (1..31).filter { d -> !notesPrefs.getString("${year}_${month}_$d", "").isNullOrEmpty() }.toSet()
    }
    val noteKey = "${year}_${month}_${selectedDay}"
    var currentNote by remember(year, month, selectedDay) {
        mutableStateOf(notesPrefs.getString(noteKey, "") ?: "")
    }
    var isEditingNote by remember { mutableStateOf(false) }
    var editNoteText by remember { mutableStateOf("") }

    // Reminder state
    var showAlarmForm by remember(year, month, selectedDay) { mutableStateOf(false) }
    var alarmTitleText by remember(year, month, selectedDay) { mutableStateOf("") }
    var pendingAlarmTitle by remember { mutableStateOf("") }
    var showTimePicker by remember { mutableStateOf(false) }
    var reminderMessage by remember { mutableStateOf<String?>(null) }
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showTimePicker = true }

    // Time picker dialog — placed outside LazyColumn to always trigger
    if (showTimePicker) {
        val cal = remember { java.util.Calendar.getInstance() }
        DisposableEffect(selectedDay) {
            val dlg = TimePickerDialog(
                context,
                { _, hour, minute ->
                    scheduleAlarm(context, year, month, selectedDay, hour, minute, pendingAlarmTitle, selectedKhmerDate, lang)
                    widgetScope.launch { WidgetPrefs.refresh(context) }
                    val timeStr = "$hour:${String.format("%02d", minute)}"
                    reminderMessage = if (lang == AppLanguage.EN) "✓ Reminder set for $timeStr"
                    else "✓ ការរំលឹកត្រូវបានកំណត់ម៉ោង $timeStr"
                    Toast.makeText(context, reminderMessage, Toast.LENGTH_SHORT).show()
                    showTimePicker = false
                },
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE),
                true
            )
            dlg.setOnCancelListener { showTimePicker = false }
            dlg.show()
            onDispose { if (dlg.isShowing) dlg.dismiss() }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Switcher Banner
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PlumSurface, RoundedCornerShape(12.dp))
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
                        color = MoonWheat
                    )
                    Text(
                        text = tr(
                            "ព.ស. ${num(lang, selectedKhmerDate.BE)} · ${zodiac(lang, selectedKhmerDate.zodiac)}",
                            "BE ${selectedKhmerDate.BE} · ${zodiac(lang, selectedKhmerDate.zodiac)}"
                        ),
                        fontSize = 11.sp,
                        color = TraditionalGold,
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
                        Text(
                            text = if (lang == AppLanguage.EN)
                                if (isTodaySelected) "Today · ${selectedKhmerDate.dayOfWeekEn}"
                                else selectedKhmerDate.dayOfWeekEn
                            else
                                if (isTodaySelected) "ថ្ងៃនេះ ${selectedKhmerDate.dayOfWeek}"
                                else "ថ្ងៃ${selectedKhmerDate.dayOfWeek}",
                            fontSize = 11.sp, color = TraditionalGold, fontWeight = FontWeight.Bold
                        )
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
                        color = if (idx == 0 || idx == 6) CrimsonHoliday else GoldSubText
                    )
                }
            }
        }

        // Days Grid Calendar — wrapped in AnimatedContent for smooth month slide
        item {
            AnimatedContent(
                targetState = year * 12 + (month - 1),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(300)) { if (forward) it else -it } +
                     fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(300)) { if (forward) -it else it } +
                     fadeOut(tween(180)))
                },
                label = "MonthGrid"
            ) { ym ->
                val animYear   = ym / 12
                val animMonth  = ym % 12 + 1
                val animDays   = remember(ym) { KhmerCalendarHelper.getGregorianMonthDays(animYear, animMonth) }
                val animSerial = remember(ym) { KhmerCalendarHelper.getSerialDay(animYear, animMonth, 1) }
                val animOffset = ((animSerial + 2) % 7 + 7) % 7
                val animRows   = ((animOffset + animDays.size + 6) / 7)

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
                                    val isHoliday  = dateInfo.holiday != null
                                    val isWeekend  = col == 0 || col == 6
                                    val hasNote    = animYear == year && animMonth == month && dayNumber in daysWithNotes

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(0.8f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                when {
                                                    isSelected -> TraditionalGold.copy(0.2f)
                                                    isToday    -> LotusPink.copy(0.12f)
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
                                            .clickable { onDayChange(dayNumber) }
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
                                                    else       -> SandText
                                                }
                                            )
                                            Text(
                                                text = lunarDayLabel(lang, dateInfo),
                                                fontSize = 8.sp, lineHeight = 9.sp,
                                                maxLines = 1, softWrap = false,
                                                color = if (isSelected) TraditionalGold.copy(0.8f) else DimColor
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.height(5.dp)
                                            ) {
                                                if (dateInfo.isAuspicious) Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(JadeGreen))
                                                else if (isHoliday) Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(LotusPink))
                                                if (hasNote) Box(
                                                    modifier = Modifier
                                                        .width(10.dp).height(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(SkyBlue)
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

        // Selected date detail box
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PlumCard),
                border = BorderStroke(1.dp, TraditionalGold.copy(0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr("ព័ត៌មានលម្អិតថ្ងៃទី ${num(lang, selectedDay)}", "Details for Day $selectedDay"),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TraditionalGold
                        )
                        Text(selectedKhmerDate.moonEmoji, fontSize = 18.sp)
                    }

                    Text(
                        text = "${tr("ថ្ងៃគ្រីស្ដ", "Gregorian Date")}: ${selectedKhmerDate.dayOfWeekEn}, ${selectedKhmerDate.day} ${gregMonth(AppLanguage.EN, selectedKhmerDate.month - 1)} ${selectedKhmerDate.year}",
                        fontSize = 10.sp,
                        color = GoldSubText
                    )

                    Text(
                        text = "${tr("ថ្ងៃចន្ទគតិ", "Lunar Date")}: ${lunarDayLabel(lang, selectedKhmerDate)} ${lunarMonth(lang, selectedKhmerDate.lunarMonthName)}",
                        fontSize = 14.sp,
                        color = SandText,
                        fontWeight = FontWeight.Bold
                    )

                    // Holiday / Auspicious Tag alerts
                    if (selectedKhmerDate.holiday != null) {
                        Box(
                            modifier = Modifier
                                .background(LotusPink.copy(0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, LotusPink, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "🎉 ${tr("ថ្ងៃបុណ្យជាតិ", "Public Holiday")}: ${localizeDual(lang, selectedKhmerDate.holiday!!)}",
                                fontSize = 10.sp,
                                color = LotusPink,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (selectedKhmerDate.isAuspicious) {
                        Box(
                            modifier = Modifier
                                .background(JadeGreen.copy(0.12f), RoundedCornerShape(8.dp))
                                .border(1.dp, JadeGreen, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = tr(
                                    "🌿 ថ្ងៃមង្គល: ល្អសម្រាប់ ${selectedKhmerDate.auspiciousType ?: "ការងារទូទៅ"}",
                                    "🌿 Auspicious: good for ${localizeDual(lang, selectedKhmerDate.auspiciousType ?: "General work")}"
                                ),
                                fontSize = 10.sp,
                                color = JadeGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Legend of color representations
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            Triple(JadeGreen, tr("ថ្ងៃមង្គល", "Auspicious"), ""),
                            Triple(LotusPink, tr("ថ្ងៃបុណ្យ", "Holiday"), ""),
                            Triple(TraditionalGold, tr("ថ្ងៃសកម្ម", "Selected"), ""),
                            Triple(CrimsonHoliday, tr("ថ្ងៃឈប់", "Weekend"), ""),
                            Triple(SkyBlue, tr("កំណត់ចំណាំ", "Note"), "")
                        ).forEach { (color, label, _) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
                                Text(label, fontSize = 8.sp, color = DimColor)
                            }
                        }
                    }

                    // ── Notes section ─────────────────────────────────────────
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DeepBorder))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(tr("📝 កំណត់ចំណាំ", "📝 Notes"), fontSize = 11.sp, color = GoldSubText, fontWeight = FontWeight.SemiBold)
                        if (!isEditingNote) {
                            TextButton(
                                onClick = { editNoteText = currentNote; isEditingNote = true },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    if (currentNote.isEmpty()) tr("+ បន្ថែម", "+ Add") else tr("✏️ កែ", "✏️ Edit"),
                                    fontSize = 10.sp, color = SkyBlue
                                )
                            }
                        }
                    }
                    if (isEditingNote) {
                        OutlinedTextField(
                            value = editNoteText,
                            onValueChange = { editNoteText = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = SandText, fontSize = 12.sp),
                            placeholder = { Text(tr("សរសេរកំណត់ចំណាំ...", "Write a note..."), color = DimColor, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = PlumSurface,
                                focusedContainerColor = PlumSurface,
                                unfocusedBorderColor = DeepBorder,
                                focusedBorderColor = SkyBlue
                            ),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 4
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { isEditingNote = false }) {
                                Text(tr("បោះបង់", "Cancel"), fontSize = 10.sp, color = DimColor)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val trimmed = editNoteText.trim()
                                    notesPrefs.edit().putString(noteKey, trimmed).apply()
                                    currentNote = trimmed
                                    widgetScope.launch { WidgetPrefs.refresh(context) }
                                    isEditingNote = false
                                    notesVersion++
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(tr("រក្សាទុក", "Save"), fontSize = 10.sp, color = NightBlack, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else if (currentNote.isNotEmpty()) {
                        Text(
                            text = currentNote,
                            fontSize = 12.sp, color = SandText, lineHeight = 17.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PlumSurface, RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }

                    // ── Reminder section ──────────────────────────────────────
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(DeepBorder))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔔", fontSize = 14.sp)
                            Text(tr("ការរំលឹក", "Reminder"), fontSize = 11.sp, color = GoldSubText, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(
                            onClick = {
                                alarmTitleText = ""
                                reminderMessage = null
                                showAlarmForm = !showAlarmForm
                            },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) {
                            Text(
                                if (showAlarmForm) tr("បោះបង់", "Cancel")
                                else tr("+ កំណត់", "+ Set"),
                                fontSize = 10.sp,
                                color = if (showAlarmForm) DimColor else TraditionalGold
                            )
                        }
                    }
                    if (showAlarmForm) {
                        OutlinedTextField(
                            value = alarmTitleText,
                            onValueChange = { alarmTitleText = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = SandText, fontSize = 12.sp),
                            placeholder = { Text(tr("ចំណងជើងរំលឹក...", "Alarm title..."), color = DimColor, fontSize = 12.sp) },
                            label = { Text(tr("ចំណងជើង (ស្រេចចិត្ត)", "Title (optional)"), color = GoldSubText, fontSize = 10.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = PlumSurface,
                                focusedContainerColor = PlumSurface,
                                unfocusedBorderColor = DeepBorder,
                                focusedBorderColor = TraditionalGold
                            ),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                pendingAlarmTitle = alarmTitleText
                                showAlarmForm = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                                        showTimePicker = true
                                    else
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    showTimePicker = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TraditionalGold),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "⏰ ${tr("ជ្រើសម៉ោង", "Pick Time")}",
                                fontSize = 12.sp, color = NightBlack, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (reminderMessage != null) {
                        Text(reminderMessage!!, fontSize = 10.sp, color = JadeGreen)
                    }
                }
            }
        }

    }
}
