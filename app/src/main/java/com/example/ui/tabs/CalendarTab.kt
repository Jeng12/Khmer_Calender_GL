package com.example.ui.tabs

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import com.example.alarm.scheduleAlarm
import com.example.calendar.KhmerCalendarHelper
import com.example.calendar.KhmerDate
import com.example.core.*
import com.example.ui.theme.*
import com.example.widget.WidgetPrefs
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

// Data model for monthly agenda items
data class AgendaItem(
    val day: Int,
    val type: AgendaType,
    val title: String,
    val subtitle: String,
    val isPast: Boolean
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
    val widgetScope = rememberCoroutineScope()
    var swipeOffset by remember { mutableStateOf(0f) }
    
    // Refresh triggers
    var agendaVersion by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(Unit) { WidgetPrefs.refresh(context) }

    val todayCal = remember { java.util.Calendar.getInstance() }
    val todayYear = todayCal.get(java.util.Calendar.YEAR)
    val todayMonth = todayCal.get(java.util.Calendar.MONTH) + 1
    val todayDay = todayCal.get(java.util.Calendar.DAY_OF_MONTH)
    
    val daysList = remember(year, month) { KhmerCalendarHelper.getGregorianMonthDays(year, month) }
    val startDayOfWeekSerial = KhmerCalendarHelper.getSerialDay(year, month, 1)
    val startOffset = ((startDayOfWeekSerial + 2) % 7 + 7) % 7

    val selectedKhmerDate = daysList.getOrNull(selectedDay - 1) ?: daysList.firstOrNull() ?: KhmerCalendarHelper.getKhmerDate(year, month, selectedDay)

    val notesPrefs = remember { context.getSharedPreferences("khmer_calendar_notes", android.content.Context.MODE_PRIVATE) }
    val daysWithNotes = remember(year, month, agendaVersion) {
        (1..31).filter { d -> !notesPrefs.getString("${year}_${month}_$d", "").isNullOrEmpty() }.toSet()
    }
    
    var showDayDetailDialog by remember { mutableStateOf(false) }
    var detailDialogDate by remember { mutableStateOf<KhmerDate?>(null) }

    // Build the Monthly Agenda
    val holidayLabel = tr("ថ្ងៃឈប់សម្រាកសាធារណៈ", "Public Holiday")
    val noteLabel = tr("កំណត់ចំណាំ", "Note")
    val reminderFallback = tr("ការរំលឹក", "Reminder")

    val monthlyAgenda = remember(year, month, agendaVersion, lang) {
        val list = mutableListOf<AgendaItem>()
        
        // 1. Add Holidays
        daysList.forEachIndexed { index, khDate ->
            val d = index + 1
            if (khDate.holiday != null) {
                list.add(AgendaItem(
                    day = d,
                    type = AgendaType.HOLIDAY,
                    title = localizeDual(lang, khDate.holiday!!),
                    subtitle = holidayLabel,
                    isPast = year < todayYear || (year == todayYear && month < todayMonth) || (year == todayYear && month == todayMonth && d < todayDay)
                ))
            }
        }
        
        // 2. Add Notes
        for (d in 1..31) {
            val note = notesPrefs.getString("${year}_${month}_$d", "")
            if (!note.isNullOrBlank()) {
                list.add(AgendaItem(
                    day = d,
                    type = AgendaType.NOTE,
                    title = note,
                    subtitle = noteLabel,
                    isPast = year < todayYear || (year == todayYear && month < todayMonth) || (year == todayYear && month == todayMonth && d < todayDay)
                ))
            }
        }
        
        // 3. Add Reminders
        val alarmsPrefs = context.getSharedPreferences("khmer_calendar_alarms", android.content.Context.MODE_PRIVATE)
        val arr = try { JSONArray(alarmsPrefs.getString("alarms", "[]") ?: "[]") } catch (e: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o != null) {
                val requestCode = o.optInt("requestCode") // format: YYYYMMDD
                val rYear = requestCode / 10000
                val rMonth = (requestCode % 10000) / 100
                val rDay = requestCode % 100
                
                if (rYear == year && rMonth == month) {
                    list.add(AgendaItem(
                        day = rDay,
                        type = AgendaType.REMINDER,
                        title = o.optString("title").ifBlank { reminderFallback },
                        subtitle = o.optString("message"),
                        isPast = year < todayYear || (year == todayYear && month < todayMonth) || (year == todayYear && month == todayMonth && rDay < todayDay)
                    ))
                }
            }
        }
        
        list.sortedWith(compareBy({ it.day }, { it.type }))
    }

    if (showDayDetailDialog && detailDialogDate != null) {
        DayDetailDialog(
            date = detailDialogDate!!,
            lang = lang,
            onDismiss = { showDayDetailDialog = false },
            onDataChange = { agendaVersion++ }
        )
    }

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
                        color = if (idx == 0 || idx == 6) CrimsonHoliday else goldSubText
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
                                                    else       -> SandText
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

        // Monthly Agenda Section
        item {
            Text(
                text = tr("📅 កម្មវិធីប្រចាំខែ", "📅 Monthly Agenda"),
                style = TextStyle(color = goldSubText, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (monthlyAgenda.isEmpty()) {
            item {
                Text(
                    tr("គ្មានកម្មវិធីសម្រាប់ខែនេះទេ", "No events for this month"),
                    fontSize = 12.sp, color = dimColor, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                )
            }
        } else {
            items(monthlyAgenda) { item ->
                val accentColor = when (item.type) {
                    AgendaType.HOLIDAY -> LotusPink
                    AgendaType.NOTE -> SkyBlue
                    AgendaType.REMINDER -> TraditionalGold
                }
                val icon = when (item.type) {
                    AgendaType.HOLIDAY -> "🏮"
                    AgendaType.NOTE -> "📝"
                    AgendaType.REMINDER -> "⏰"
                }
                AgendaItemRow(
                    icon = icon,
                    title = item.title,
                    subtitle = "${num(lang, item.day)} ${gregMonth(lang, month - 1)} · ${item.subtitle}",
                    accentColor = if (item.isPast) Color.Gray else accentColor,
                    isPast = item.isPast
                )
            }
        }

        // Legend Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendIndicator(JadeGreen, tr("ថ្ងៃមង្គល", "Auspicious"))
                LegendIndicator(LotusPink, tr("ថ្ងៃបុណ្យ", "Holiday"))
                LegendIndicator(SkyBlue, tr("ចំណាំ", "Note"))
                LegendIndicator(TraditionalGold, tr("សកម្ម", "Active"))
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
    onDismiss: () -> Unit,
    onDataChange: () -> Unit
) {
    val (nightBlack, _, plumSurface, plumCard, deepBorder, _, sandText, goldSubText, dimColor) = LocalAppColors.current
    val context = LocalContext.current
    val widgetScope = rememberCoroutineScope()
    
    // Day-specific states inside dialog
    val noteKey = "${date.year}_${date.month}_${date.day}"
    val notesPrefs = remember { context.getSharedPreferences("khmer_calendar_notes", android.content.Context.MODE_PRIVATE) }
    var currentNote by remember(noteKey) { mutableStateOf(notesPrefs.getString(noteKey, "") ?: "") }
    var editNoteText by remember { mutableStateOf(currentNote) }
    var isEditingNote by remember { mutableStateOf(false) }
    
    var alarmTitleText by remember { mutableStateOf("") }
    var showAlarmForm by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) showTimePicker = true }

    if (showTimePicker) {
        val cal = remember { java.util.Calendar.getInstance() }
        val dlg = TimePickerDialog(
            context,
            { _, hour, minute ->
                scheduleAlarm(context, date.year, date.month, date.day, hour, minute, alarmTitleText, date, lang)
                widgetScope.launch { WidgetPrefs.refresh(context) }
                onDataChange()
                Toast.makeText(context, tr(lang, "បានកំណត់ការរំលឹក", "Reminder set"), Toast.LENGTH_SHORT).show()
                showTimePicker = false
                showAlarmForm = false
            },
            cal.get(java.util.Calendar.HOUR_OF_DAY),
            cal.get(java.util.Calendar.MINUTE),
            true
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
                        
                        if (date.isAuspicious) {
                            DetailRow(
                                tr("ថ្ងៃមង្គល", "Auspicious"),
                                localizeDual(lang, date.auspiciousType ?: "General"),
                                JadeGreen,
                                goldSubText
                            )
                        }
                        if (date.holiday != null) {
                            DetailRow(
                                tr("ថ្ងៃបុណ្យ", "Holiday"),
                                localizeDual(lang, date.holiday!!),
                                LotusPink,
                                goldSubText
                            )
                        }
                    }
                }

                item { HorizontalDivider(color = deepBorder, thickness = 1.dp) }

                // Note Editor Section
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tr("📝 កំណត់ចំណាំ", "📝 Note"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = sandText)
                            TextButton(onClick = { if(!isEditingNote) editNoteText = currentNote; isEditingNote = !isEditingNote }) {
                                Text(if(isEditingNote) tr("បោះបង់", "Cancel") else if(currentNote.isEmpty()) tr("+ បន្ថែម", "+ Add") else tr("✏️ កែ", "✏️ Edit"), color = SkyBlue, fontSize = 11.sp)
                            }
                        }
                        if (isEditingNote) {
                            OutlinedTextField(
                                value = editNoteText,
                                onValueChange = { editNoteText = it },
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = TextStyle(fontSize = 12.sp, color = sandText),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SkyBlue)
                            )
                            Button(
                                onClick = {
                                    notesPrefs.edit().putString(noteKey, editNoteText.trim()).apply()
                                    currentNote = editNoteText.trim()
                                    isEditingNote = false
                                    onDataChange()
                                    widgetScope.launch { WidgetPrefs.refresh(context) }
                                },
                                modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SkyBlue)
                            ) {
                                Text(tr("រក្សាទុក", "Save"), color = nightBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        } else if (currentNote.isNotEmpty()) {
                            Text(currentNote, fontSize = 12.sp, color = sandText, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }

                item { HorizontalDivider(color = deepBorder, thickness = 1.dp) }

                // Reminder Section
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(tr("🔔 ការរំលឹក", "🔔 Reminder"), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = sandText)
                            TextButton(onClick = { showAlarmForm = !showAlarmForm }) {
                                Text(if(showAlarmForm) tr("បោះបង់", "Cancel") else tr("+ កំណត់", "+ Set"), color = TraditionalGold, fontSize = 11.sp)
                            }
                        }
                        if (showAlarmForm) {
                            OutlinedTextField(
                                value = alarmTitleText,
                                onValueChange = { alarmTitleText = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(tr("ចំណងជើង...", "Title..."), fontSize = 12.sp) },
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
                                Text(tr("ជ្រើសម៉ោង", "Pick Time"), color = nightBlack, fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
private fun DetailRow(label: String, value: String, valueColor: Color, labelColor: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = labelColor)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}
