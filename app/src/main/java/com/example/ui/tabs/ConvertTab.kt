package com.example.ui.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calendar.KhmerDate
import com.example.ui.theme.*

// 5. DATE CONVERTER TAB CONTAINER
@Composable
fun DateConvertContent(
    year: String,
    month: String,
    day: String,
    convertedDate: KhmerDate?,
    onConvert: (String, String, String) -> Unit
) {
    var inYear by remember { mutableStateOf(year) }
    var inMonth by remember { mutableStateOf(month) }
    var inDay by remember { mutableStateOf(day) }
    var inputError by remember { mutableStateOf<String?>(null) }

    val khMonths = listOf(
        "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
        "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
    )

    fun validate(): Boolean {
        val y = inYear.toIntOrNull()
        val m = inMonth.toIntOrNull()
        val d = inDay.toIntOrNull()
        return when {
            y == null || m == null || d == null -> {
                inputError = "សូមបញ្ចូលតម្លៃជាលេខ (numbers only)"
                false
            }
            m < 1 || m > 12 -> {
                inputError = "ខែត្រូវស្ថិតក្នុងចន្លោះ ១–១២ (Month 1–12)"
                false
            }
            d < 1 || d > 31 -> {
                inputError = "ថ្ងៃត្រូវស្ថិតក្នុងចន្លោះ ១–៣១ (Day 1–31)"
                false
            }
            else -> {
                inputError = null
                true
            }
        }
    }

    LaunchedEffect(year, month, day) {
        inYear = year
        inMonth = month
        inDay = day
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NightBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(SkyBlue.copy(0.18f), TraditionalGold.copy(0.1f))),
                        RoundedCornerShape(14.dp)
                    )
                    .border(1.dp, SkyBlue.copy(0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🔄", fontSize = 28.sp)
                    Column {
                        Text("បំលែងថ្ងៃខែ", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MoonWheat)
                        Text("ពីគ្រីស្ដសករាជ → ចន្ទគតិខ្មែរ · គ្រប់ឆ្នាំ", fontSize = 10.sp, color = SkyBlue)
                    }
                }
            }
        }

        // Input card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = PlumSurface),
                border = BorderStroke(1.dp, if (inputError != null) CrimsonHoliday.copy(0.6f) else DeepBorder),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Label row + Today button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ថ្ងៃខែគ្រីស្ដសករាជ",
                            fontSize = 10.sp,
                            color = SkyBlue,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(SkyBlue.copy(0.15f), RoundedCornerShape(20.dp))
                                .border(1.dp, SkyBlue.copy(0.4f), RoundedCornerShape(20.dp))
                                .clickable {
                                    val now = java.util.Calendar.getInstance()
                                    inDay = now.get(java.util.Calendar.DAY_OF_MONTH).toString()
                                    inMonth = (now.get(java.util.Calendar.MONTH) + 1).toString()
                                    inYear = now.get(java.util.Calendar.YEAR).toString()
                                    inputError = null
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("📅 ថ្ងៃនេះ", fontSize = 10.sp, color = SkyBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Day / Month / Year fields
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ថ្ងៃ (Day)", fontSize = 9.sp, color = DimColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = inDay,
                                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) inDay = v },
                                textStyle = TextStyle(
                                    color = SandText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                placeholder = {
                                    Text("DD", color = DimColor, fontSize = 14.sp,
                                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = PlumCard,
                                    focusedContainerColor = PlumCard,
                                    unfocusedBorderColor = DeepBorder,
                                    focusedBorderColor = SkyBlue
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ខែ (Month)", fontSize = 9.sp, color = DimColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = inMonth,
                                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) inMonth = v },
                                textStyle = TextStyle(
                                    color = SandText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                placeholder = {
                                    Text("MM", color = DimColor, fontSize = 14.sp,
                                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = PlumCard,
                                    focusedContainerColor = PlumCard,
                                    unfocusedBorderColor = DeepBorder,
                                    focusedBorderColor = SkyBlue
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(modifier = Modifier.weight(1.6f)) {
                            Text("ឆ្នាំ (Year)", fontSize = 9.sp, color = DimColor)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = inYear,
                                onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 6) inYear = v },
                                textStyle = TextStyle(
                                    color = SandText,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                placeholder = {
                                    Text("YYYY", color = DimColor, fontSize = 14.sp,
                                        modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = PlumCard,
                                    focusedContainerColor = PlumCard,
                                    unfocusedBorderColor = DeepBorder,
                                    focusedBorderColor = SkyBlue
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Error banner
                    if (inputError != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CrimsonHoliday.copy(0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, CrimsonHoliday.copy(0.4f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("⚠", fontSize = 13.sp, color = CrimsonHoliday)
                            Text(inputError ?: "", fontSize = 10.sp, color = CrimsonHoliday)
                        }
                    }

                    // Convert button
                    Button(
                        onClick = { if (validate()) onConvert(inYear, inMonth, inDay) },
                        colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("convert_date_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⇅", fontSize = 18.sp, color = NightBlack)
                            Text("បំលែងជាចន្ទគតិ", color = NightBlack, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // Result card
        if (convertedDate != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PlumCard),
                    border = BorderStroke(1.dp, TraditionalGold.copy(0.5f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Result header row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "លទ្ធផលថ្ងៃចន្ទគតិ",
                                fontSize = 10.sp,
                                color = TraditionalGold,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Text(convertedDate.moonEmoji, fontSize = 24.sp)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TraditionalGold.copy(0.25f)))
                        Spacer(modifier = Modifier.height(12.dp))

                        // Gregorian date echo
                        val mIdx = (convertedDate.month - 1).coerceIn(0, 11)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SkyBlue))
                            Text(
                                "ថ្ងៃ${convertedDate.dayOfWeek} ទី${convertedDate.day} ${khMonths[mIdx]} ${convertedDate.year}",
                                fontSize = 12.sp,
                                color = GoldSubText
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Main lunar result — large
                        Text(
                            text = "${convertedDate.lunarDayName} ខែ${convertedDate.lunarMonthName}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = MoonWheat
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Tags
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(TraditionalGold.copy(0.18f), RoundedCornerShape(20.dp))
                                    .border(1.dp, TraditionalGold.copy(0.4f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("ព.ស. ${convertedDate.BE}", fontSize = 10.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .background(LotusPink.copy(0.12f), RoundedCornerShape(20.dp))
                                    .border(1.dp, LotusPink.copy(0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(convertedDate.zodiac, fontSize = 10.sp, color = LotusPink, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .background(SkyBlue.copy(0.12f), RoundedCornerShape(20.dp))
                                    .border(1.dp, SkyBlue.copy(0.3f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "${convertedDate.moonEmoji} ${if (convertedDate.isWaxing) "កើត" else "រោច"}",
                                    fontSize = 10.sp,
                                    color = SkyBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Holiday alert
                        if (convertedDate.holiday != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(LotusPink.copy(0.1f), RoundedCornerShape(10.dp))
                                    .border(1.dp, LotusPink.copy(0.45f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🎉", fontSize = 16.sp)
                                Column {
                                    Text("ថ្ងៃបុណ្យ", fontSize = 9.sp, color = LotusPink.copy(0.7f))
                                    Text(convertedDate.holiday!!, fontSize = 12.sp, color = LotusPink, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Auspicious alert
                        if (convertedDate.isAuspicious) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(JadeGreen.copy(0.1f), RoundedCornerShape(10.dp))
                                    .border(1.dp, JadeGreen.copy(0.45f), RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🌿", fontSize = 16.sp)
                                Column {
                                    Text("ថ្ងៃមង្គល", fontSize = 9.sp, color = JadeGreen.copy(0.7f))
                                    Text(
                                        convertedDate.auspiciousType ?: "ថ្ងៃល្អ",
                                        fontSize = 12.sp,
                                        color = JadeGreen,
                                        fontWeight = FontWeight.Bold
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
