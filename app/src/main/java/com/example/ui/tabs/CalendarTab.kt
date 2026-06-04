package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendar.KhmerCalendarHelper
import com.example.ui.theme.*

// 2. CALENDAR TAB CONTAINER
@Composable
fun CalendarTabContent(
    year: Int,
    month: Int,
    selectedDay: Int,
    onMonthChange: (Int, Int) -> Unit,
    onDayChange: (Int) -> Unit
) {
    // Generate dates inside month
    val daysList = KhmerCalendarHelper.getGregorianMonthDays(year, month)
    // Starting day of week for index 1
    val startDayOfWeekSerial = KhmerCalendarHelper.getSerialDay(year, month, 1)
    val startOffset = ((startDayOfWeekSerial + 4) % 7 + 7) % 7 // index representing start day of week (Sunday=0, etc.)

    val khmerMonthNames = listOf(
        "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
        "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
    )

    val selectedKhmerDate = daysList.getOrNull(selectedDay - 1) ?: daysList.firstOrNull() ?: KhmerCalendarHelper.getKhmerDate(year, month, selectedDay)

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
                        text = "ខែ${khmerMonthNames[month - 1]} $year",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MoonWheat
                    )
                    Text(
                        text = "ព.ស. ${selectedKhmerDate.BE} · ${selectedKhmerDate.zodiac}",
                        fontSize = 9.sp,
                        color = TraditionalGold,
                        fontWeight = FontWeight.SemiBold
                    )
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
                val weekLabels = listOf("អា", "ច", "អ", "ព", "ព្រ", "សុ", "ស")
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

        // Days Grid Calendar
        item {
            // We use simple nested layouts instead of complex lazy grid inside lazy column to prevent crashes
            val totalCells = startOffset + daysList.size
            val rowsCount = (totalCells + 6) / 7

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until rowsCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..6) {
                            val cellIdx = row * 7 + col
                            val dayNumber = cellIdx - startOffset + 1

                            if (cellIdx < startOffset || dayNumber > daysList.size) {
                                Box(modifier = Modifier.weight(1f))
                            } else {
                                val dateInfo = daysList[dayNumber - 1]
                                val isSelected = dayNumber == selectedDay
                                val isHoliday = dateInfo.holiday != null
                                val isWeekend = col == 0 || col == 6

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.8f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) TraditionalGold.copy(0.2f) else Color.Transparent)
                                        .border(
                                            1.5.dp,
                                            if (isSelected) TraditionalGold else Color.Transparent,
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
                                        // Render small indicator for major moon phase
                                        val hasMoonIndicator =
                                            (dateInfo.isWaxing && dateInfo.lunarDayVal in listOf(1, 8, 15)) ||
                                            (!dateInfo.isWaxing && dateInfo.lunarDayVal == 8)
                                        if (hasMoonIndicator) {
                                            Text(dateInfo.moonEmoji, fontSize = 10.sp, lineHeight = 12.sp)
                                        }

                                        Text(
                                            text = dayNumber.toString(),
                                            fontSize = 20.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = when {
                                                isSelected -> TraditionalGold
                                                isHoliday -> LotusPink
                                                isWeekend -> CrimsonHoliday
                                                else -> SandText
                                            }
                                        )
                                        Text(
                                            text = KhmerCalendarHelper.toKhmerNumeral(dateInfo.lunarDayVal),
                                            fontSize = 11.sp,
                                            color = if (isSelected) TraditionalGold.copy(0.8f) else DimColor
                                        )
                                        // Small dot if auspicious or has custom holiday
                                        if (dateInfo.isAuspicious) {
                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(JadeGreen))
                                        } else if (isHoliday) {
                                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(LotusPink))
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
                            text = "ព័ត៌មានលម្អិតថ្ងៃទី $selectedDay",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TraditionalGold
                        )
                        Text(selectedKhmerDate.moonEmoji, fontSize = 18.sp)
                    }

                    Text(
                        text = "Gregorian Date: ${selectedKhmerDate.dayOfWeekEn}, ${selectedKhmerDate.day} ${khmerMonthNames[selectedKhmerDate.month - 1]} ${selectedKhmerDate.year}",
                        fontSize = 10.sp,
                        color = GoldSubText
                    )

                    Text(
                        text = "ថ្ងៃចន្ទគតិ: ${selectedKhmerDate.lunarDayName} ${selectedKhmerDate.lunarMonthName}",
                        fontSize = 12.sp,
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
                                text = "🎉 ថ្ងៃបុណ្យជាតិ: ${selectedKhmerDate.holiday}",
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
                                text = "🌿 ថ្ងៃមង្គល: ល្អសម្រាប់ ${selectedKhmerDate.auspiciousType ?: "ការងារទូទៅ"}",
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
                            Pair(JadeGreen, "ថ្ងៃមង្គល"),
                            Pair(LotusPink, "ថ្ងៃបុណ្យ"),
                            Pair(TraditionalGold, "ថ្ងៃសកម្ម"),
                            Pair(CrimsonHoliday, "ថ្ងៃឈប់")
                        ).forEach { legend ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(legend.first))
                                Text(legend.second, fontSize = 8.sp, color = DimColor)
                            }
                        }
                    }
                }
            }
        }
    }
}
