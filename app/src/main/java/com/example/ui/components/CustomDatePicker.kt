package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.calendar.KhmerCalendarHelper
import com.example.calendar.KhmerDate
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NightBlack
import com.example.ui.theme.PlumSurface
import com.example.ui.theme.PlumCard
import com.example.ui.theme.DeepBorder
import com.example.ui.theme.TraditionalGold
import com.example.ui.theme.MoonWheat
import com.example.ui.theme.SandText
import com.example.ui.theme.GoldSubText
import com.example.ui.theme.DimColor
import com.example.ui.theme.LotusPink
import com.example.ui.theme.JadeGreen
import com.example.ui.theme.CrimsonHoliday
import java.time.LocalDate

private val KHMER_MONTH_NAMES_EN = listOf(
    "January", "February", "March", "April",
    "May", "June", "July", "August",
    "September", "October", "November", "December"
)

private val KHMER_MONTH_NAMES_KH = listOf(
    "មករា", "កុម្ភៈ", "មីនា", "មេសា",
    "ឧសភា", "មិថុនា", "កក្កដា", "សីហា",
    "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
)

private val DAY_HEADERS_SHORT = listOf("អា", "ច", "អ", "ព", "ព្រ", "សុ", "ស")

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
    else -> 30
}

private fun firstDayOfWeek(year: Int, month: Int): Int {
    val sd = KhmerCalendarHelper.getSerialDay(year, month, 1)
    return ((sd + 2) % 7 + 7) % 7  // 0=Sunday (offset matches getKhmerDate's weekday math)
}

/**
 * A customizable Gregorian date picker composable that displays the corresponding
 * Khmer lunar date beneath each selected day.
 *
 * @param initialDate     The initially selected date (defaults to today)
 * @param minYear         Minimum selectable year (default 2019, matching milestone range)
 * @param maxYear         Maximum selectable year (default 2036, matching milestone range)
 * @param onDateSelected  Callback invoked with the selected LocalDate and its KhmerDate
 * @param onDismiss       Callback invoked when the picker is dismissed without selection
 */
@Composable
fun KhmerDatePickerDialog(
    initialDate: LocalDate = LocalDate.now(),
    minYear: Int = 2019,
    maxYear: Int = 2036,
    onDateSelected: (LocalDate, KhmerDate) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        KhmerDatePickerContent(
            initialDate = initialDate,
            minYear = minYear,
            maxYear = maxYear,
            onDateSelected = onDateSelected,
            onDismiss = onDismiss
        )
    }
}

/**
 * The inner composable for the date picker. Can be embedded inline or shown in a Dialog.
 */
@Composable
fun KhmerDatePickerContent(
    initialDate: LocalDate = LocalDate.now(),
    minYear: Int = 2019,
    maxYear: Int = 2036,
    onDateSelected: (LocalDate, KhmerDate) -> Unit,
    onDismiss: () -> Unit
) {
    var displayYear by remember { mutableIntStateOf(initialDate.year.coerceIn(minYear, maxYear)) }
    var displayMonth by remember { mutableIntStateOf(initialDate.monthValue) }
    var selectedDay by remember { mutableIntStateOf(initialDate.dayOfMonth) }

    val goldGradient = remember {
        Brush.horizontalGradient(listOf(TraditionalGold, LightGold, TraditionalGold))
    }
    val selectedDayKhmerDate = remember(displayYear, displayMonth, selectedDay) {
        KhmerCalendarHelper.getKhmerDate(displayYear, displayMonth, selectedDay)
    }
    val monthDays = remember(displayYear, displayMonth) {
        KhmerCalendarHelper.getGregorianMonthDays(displayYear, displayMonth)
    }
    val firstDow = remember(displayYear, displayMonth) {
        firstDayOfWeek(displayYear, displayMonth)
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF1D1726),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, goldGradient, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = "ជ្រើសរើសកាលបរិច្ឆេទ",
                color = LightGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Select Date",
                color = GoldSubText,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Month/Year navigation row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (displayMonth == 1) {
                            if (displayYear > minYear) { displayYear--; displayMonth = 12 }
                        } else {
                            displayMonth--
                        }
                        selectedDay = selectedDay.coerceAtMost(daysInMonth(displayYear, displayMonth))
                    }
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "ខែមុន",
                        tint = TraditionalGold)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${KHMER_MONTH_NAMES_KH[displayMonth - 1]} ${KhmerCalendarHelper.toKhmerNumeral(displayYear)}",
                        color = SandText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${KHMER_MONTH_NAMES_EN[displayMonth - 1]} $displayYear",
                        color = GoldSubText,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = {
                        if (displayMonth == 12) {
                            if (displayYear < maxYear) { displayYear++; displayMonth = 1 }
                        } else {
                            displayMonth++
                        }
                        selectedDay = selectedDay.coerceAtMost(daysInMonth(displayYear, displayMonth))
                    }
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "ខែក្រោយ",
                        tint = TraditionalGold)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Day-of-week header row
            Row(modifier = Modifier.fillMaxWidth()) {
                DAY_HEADERS_SHORT.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        color = GoldSubText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Calendar grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                userScrollEnabled = false,
                contentPadding = PaddingValues(0.dp)
            ) {
                // Leading empty cells for alignment
                items(firstDow) {
                    Box(Modifier.aspectRatio(1f))
                }

                // Day cells
                items(daysInMonth(displayYear, displayMonth)) { idx ->
                    val dayNum = idx + 1
                    val khDate = monthDays.getOrNull(idx)
                    val isSelected = dayNum == selectedDay
                    val isHoliday = khDate?.holiday != null
                    val isAuspicious = khDate?.isAuspicious == true

                    val bgColor = when {
                        isSelected -> TraditionalGold
                        isHoliday  -> CrimsonHoliday.copy(alpha = 0.25f)
                        isAuspicious -> JadeGreen.copy(alpha = 0.15f)
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        isSelected -> NightBlack
                        isHoliday  -> CrimsonHoliday
                        isAuspicious -> JadeGreen
                        else -> SandText
                    }

                    Column(
                        modifier = Modifier
                            .aspectRatio(0.85f)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor)
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { selectedDay = dayNum },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = KhmerCalendarHelper.toKhmerNumeral(dayNum),
                            color = textColor,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                        if (khDate != null) {
                            Text(
                                text = KhmerCalendarHelper.toKhmerNumeral(khDate.lunarDayVal),
                                color = if (isSelected) NightBlack.copy(alpha = 0.7f)
                                        else DimColor,
                                fontSize = 8.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Selected date summary card
            AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF261E30))
                        .border(1.dp, TraditionalGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${KHMER_MONTH_NAMES_KH[displayMonth - 1]} ${KhmerCalendarHelper.toKhmerNumeral(selectedDay)}, ${KhmerCalendarHelper.toKhmerNumeral(displayYear)}",
                        color = SandText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = selectedDayKhmerDate.moonEmoji,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${selectedDayKhmerDate.lunarDayName} ${selectedDayKhmerDate.lunarMonthName}",
                            color = LightGold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "ព.ស. ${KhmerCalendarHelper.toKhmerNumeral(selectedDayKhmerDate.BE)}",
                            color = GoldSubText,
                            fontSize = 11.sp
                        )
                    }
                    if (selectedDayKhmerDate.holiday != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "🎉 ${selectedDayKhmerDate.holiday}",
                            color = LotusPink,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (selectedDayKhmerDate.isAuspicious && selectedDayKhmerDate.auspiciousType != null) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(JadeGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "✨ ${selectedDayKhmerDate.auspiciousType}",
                                color = JadeGreen,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            // Color legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem(color = TraditionalGold, label = "ជ្រើស")
                LegendItem(color = CrimsonHoliday, label = "បុណ្យ")
                LegendItem(color = JadeGreen, label = "ថ្ងៃល្អ")
            }

            Spacer(Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onDismiss) {
                    Text("បោះបង់", color = GoldSubText)
                }
                TextButton(
                    onClick = {
                        val selected = LocalDate.of(displayYear, displayMonth, selectedDay)
                        onDateSelected(selected, selectedDayKhmerDate)
                    }
                ) {
                    Text(
                        "យល់ព្រម",
                        color = TraditionalGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, color = GoldSubText, fontSize = 10.sp)
    }
}

/**
 * A compact inline date picker field that opens the full picker dialog on tap.
 *
 * @param selectedDate    Current selected date (or null if unset)
 * @param label           Label shown above the field
 * @param onDateSelected  Callback with selected LocalDate and its KhmerDate
 */
@Composable
fun KhmerDatePickerField(
    selectedDate: LocalDate?,
    label: String = "ជ្រើសរើសថ្ងៃខែ",
    onDateSelected: (LocalDate, KhmerDate) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = GoldSubText,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF261E30))
                .border(
                    1.dp,
                    if (selectedDate != null) TraditionalGold.copy(alpha = 0.6f)
                    else DimColor,
                    RoundedCornerShape(10.dp)
                )
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { showPicker = true }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedDate != null) {
                val khDate = remember(selectedDate) {
                    KhmerCalendarHelper.getKhmerDate(
                        selectedDate.year,
                        selectedDate.monthValue,
                        selectedDate.dayOfMonth
                    )
                }
                Column {
                    Text(
                        text = "${KHMER_MONTH_NAMES_KH[selectedDate.monthValue - 1]} " +
                               "${KhmerCalendarHelper.toKhmerNumeral(selectedDate.dayOfMonth)}, " +
                               KhmerCalendarHelper.toKhmerNumeral(selectedDate.year),
                        color = SandText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${khDate.moonEmoji} ${khDate.lunarDayName} ${khDate.lunarMonthName}",
                        color = LightGold,
                        fontSize = 11.sp
                    )
                }
            } else {
                Text(
                    text = "ថ្ងៃ / ខែ / ឆ្នាំ",
                    color = DimColor,
                    fontSize = 14.sp
                )
            }

            Text(text = "📅", fontSize = 18.sp)
        }
    }

    if (showPicker) {
        KhmerDatePickerDialog(
            initialDate = selectedDate ?: LocalDate.now(),
            onDateSelected = { date, khDate ->
                onDateSelected(date, khDate)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0A0F)
@Composable
private fun KhmerDatePickerContentPreview() {
    MyApplicationTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            KhmerDatePickerContent(
                initialDate = LocalDate.of(2026, 5, 15),
                onDateSelected = { _, _ -> },
                onDismiss = {}
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0A0F)
@Composable
private fun KhmerDatePickerFieldPreview() {
    MyApplicationTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            KhmerDatePickerField(
                selectedDate = LocalDate.of(2026, 5, 15),
                label = "ថ្ងៃចូលផ្ទះថ្មី",
                onDateSelected = { _, _ -> }
            )
        }
    }
}
