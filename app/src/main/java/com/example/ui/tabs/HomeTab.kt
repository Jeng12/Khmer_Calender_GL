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

/* ─────────────────────────────────────────────────────────────
   TAB CONTENTS
───────────────────────────────────────────────────────────── */

/** One upcoming public holiday shown on the Home screen's "Upcoming events" list. */
private data class UpcomingHoliday(val year: Int, val month: Int, val day: Int, val name: String)

// 1. HOME TAB CONTAINER
@Composable
fun HomeTabContent(onTabSelect: (AppTab) -> Unit) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current
    val calendar = remember { java.util.Calendar.getInstance() }
    val currentYear = calendar.get(java.util.Calendar.YEAR)
    val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
    val currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
    val currentKhmerInfo = remember(currentYear, currentMonth, currentDay) {
        KhmerCalendarHelper.getKhmerDate(currentYear, currentMonth, currentDay)
    }
    // Real upcoming public holidays (next 3), scanned from today via the calendar engine.
    val todaySerial = remember(currentYear, currentMonth, currentDay) {
        KhmerCalendarHelper.getSerialDay(currentYear, currentMonth, currentDay)
    }
    val localUpcomingHolidays = remember(currentYear, currentMonth, currentDay) {
        val out = ArrayList<UpcomingHoliday>()
        var y = currentYear; var m = currentMonth; var scanned = 0
        while (out.size < 3 && scanned < 6) {
            val days = KhmerCalendarHelper.getGregorianMonthDays(y, m)
            for (idx in days.indices) {
                val name = days[idx].holiday ?: continue
                val d = idx + 1
                val future = y > currentYear || (y == currentYear && m > currentMonth) ||
                    (y == currentYear && m == currentMonth && d >= currentDay)
                if (future && out.size < 3) out.add(UpcomingHoliday(y, m, d, name))
            }
            m++; if (m > 12) { m = 1; y++ }; scanned++
        }
        out
    }
    var apiUpcomingHolidays by remember { mutableStateOf<List<UpcomingHoliday>?>(null) }
    LaunchedEffect(currentYear, currentMonth, currentDay) {
        HolidayRepository.fetchHolidays(currentYear).onSuccess { holidays ->
            apiUpcomingHolidays = holidays
                .filter { holiday ->
                    KhmerCalendarHelper.getSerialDay(
                        holiday.date.year,
                        holiday.date.monthValue,
                        holiday.date.dayOfMonth
                    ) >= todaySerial
                }
                .take(3)
                .map { holiday ->
                    UpcomingHoliday(
                        year = holiday.date.year,
                        month = holiday.date.monthValue,
                        day = holiday.date.dayOfMonth,
                        name = if (lang == AppLanguage.EN) holiday.nameEn else holiday.nameKh
                    )
                }
        }
    }
    val upcomingHolidays = apiUpcomingHolidays?.takeIf { it.isNotEmpty() } ?: localUpcomingHolidays

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App header with Khmer lunar elements
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(
                            Brush.linearGradient(listOf(TraditionalGold, CrimsonHoliday)),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌙", fontSize = 24.sp)
                }
                Column {
                    Text(tr("ប្រតិទិនចន្ទគតិខ្មែរ", "Khmer Lunar Calendar"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = SandText)
                    Text("KHMER LUNAR CALENDAR · OFFICIAL v2", fontSize = 9.sp, color = TraditionalGold, letterSpacing = 1.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Today Hero card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PlumSurface),
                border = BorderStroke(1.dp, TraditionalGold.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Floating giant background moon info
                    Text(
                        text = currentKhmerInfo.moonEmoji,
                        fontSize = 80.sp,
                        modifier = Modifier.align(Alignment.TopEnd).alpha(0.08f)
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(tr("TODAY · ថ្ងៃនេះ", "TODAY"), fontSize = 9.sp, color = GoldSubText, letterSpacing = 1.sp)
                                Text(
                                    text = if (lang == AppLanguage.EN)
                                        "${currentKhmerInfo.dayOfWeekEn}, ${currentKhmerInfo.day} ${gregMonth(lang, currentMonth - 1)} $currentYear"
                                    else
                                        "ថ្ងៃ${currentKhmerInfo.dayOfWeek} ទី${num(lang, currentKhmerInfo.day)} ${gregMonth(lang, currentMonth - 1)} ${num(lang, currentYear)}",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SandText
                                )
                            }
                            Text(text = currentKhmerInfo.moonEmoji, fontSize = 32.sp)
                        }

                        // Divider line
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(TraditionalGold.copy(0.25f))
                        )

                        Text(
                            text = "${lunarDayLabel(lang, currentKhmerInfo)} ${lunarMonth(lang, currentKhmerInfo.lunarMonthName)} ${zodiac(lang, currentKhmerInfo.zodiac)}",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = TraditionalGold
                        )

                        // Tags row
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .background(TraditionalGold.copy(0.18f), RoundedCornerShape(20.dp))
                                    .border(1.dp, TraditionalGold.copy(0.4f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(tr("ព.ស. ${num(lang, currentKhmerInfo.BE)}", "BE ${currentKhmerInfo.BE}"), fontSize = 9.sp, color = TraditionalGold, fontWeight = FontWeight.SemiBold)
                            }
                            Box(
                                modifier = Modifier
                                    .background(LotusPink.copy(0.12f), RoundedCornerShape(20.dp))
                                    .border(1.dp, LotusPink.copy(0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (lang == AppLanguage.EN)
                                        "${lunarMonth(lang, currentKhmerInfo.lunarMonthName)} ${lunarDayLabel(lang, currentKhmerInfo)}"
                                    else
                                        "ខែ${currentKhmerInfo.lunarMonthName} ${lunarDayLabel(lang, currentKhmerInfo)}",
                                    fontSize = 9.sp, color = LotusPink, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick action grids
        item {
            Text(tr("សេវាកម្មរហ័ស (QUICK SERVICES)", "QUICK SERVICES"), fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "📅",
                    title = tr("ប្រតិទិន", "Calendar"),
                    subtitle = "Calendar",
                    accentColor = TraditionalGold,
                    onClick = { onTabSelect(AppTab.CALENDAR) }
                )
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🌿",
                    title = tr("ថ្ងៃមង្គល", "Auspicious"),
                    subtitle = "Auspicious",
                    accentColor = JadeGreen,
                    onClick = { onTabSelect(AppTab.AUSPICIOUS) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🎊",
                    title = tr("ថ្ងៃបុណ្យ", "Holidays"),
                    subtitle = "Holidays",
                    accentColor = LotusPink,
                    onClick = { onTabSelect(AppTab.HOLIDAYS) }
                )
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🔄",
                    title = tr("បំលែង", "Convert"),
                    subtitle = "Convert",
                    accentColor = SkyBlue,
                    onClick = { onTabSelect(AppTab.CONVERT) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🏭",
                    title = tr("កាលវិភាគ", "Schedule"),
                    subtitle = "Work Schedule",
                    accentColor = CrimsonHoliday,
                    onClick = { onTabSelect(AppTab.SCHEDULE) }
                )
                QuickGridCard(
                    modifier = Modifier.weight(1f),
                    emoji = "👤",
                    title = tr("ប្រវត្តិរូប", "Profile"),
                    subtitle = "Profile & Settings",
                    accentColor = SkyBlue,
                    onClick = { onTabSelect(AppTab.PROFILE) }
                )
            }
        }

        // Upcoming public holidays — real data from the calendar engine; tap to open the calendar.
        item {
            Text(tr("ព្រឹត្តិការណ៍ខាងមុខ (UPCOMING EVENTS)", "UPCOMING EVENTS"), fontSize = 10.sp, color = DimColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(10.dp))

            if (upcomingHolidays.isEmpty()) {
                Text(tr("គ្មានព្រឹត្តិការណ៍ខាងមុខ", "No upcoming events"), fontSize = 11.sp, color = DimColor)
            } else {
                upcomingHolidays.forEach { ev ->
                    val daysLeft = (KhmerCalendarHelper.getSerialDay(ev.year, ev.month, ev.day) - todaySerial).coerceAtLeast(0)
                    val whenText = when (daysLeft) {
                        0 -> tr("ថ្ងៃនេះ", "Today")
                        1 -> tr("ស្អែក", "Tomorrow")
                        else -> tr("នៅ ${num(lang, daysLeft)} ថ្ងៃទៀត", "in $daysLeft days")
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(PlumCard, RoundedCornerShape(12.dp))
                            .border(1.dp, DeepBorder, RoundedCornerShape(12.dp))
                            .clickable { onTabSelect(AppTab.CALENDAR) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(TraditionalGold.copy(0.12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎉", fontSize = 16.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(localizeDual(lang, ev.name), fontSize = 12.sp, color = SandText, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text("${num(lang, ev.day)} ${gregMonth(lang, ev.month - 1)} · $whenText", fontSize = 9.sp, color = TraditionalGold)
                        }
                        Text("›", fontSize = 18.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickGridCard(
    modifier: Modifier = Modifier,
    emoji: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    Box(
        modifier = modifier
            .background(PlumCard, RoundedCornerShape(12.dp))
            .border(BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)), RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(emoji, fontSize = 24.sp)
            Column {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = SandText)
                Text(subtitle, fontSize = 9.sp, color = DimColor)
            }
        }
    }
}
