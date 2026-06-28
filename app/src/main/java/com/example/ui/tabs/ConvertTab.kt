package com.example.ui.tabs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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

// 5. DATE CONVERTER TAB CONTAINER
@Composable
fun DateConvertContent(
    year: String,
    month: String,
    day: String,
    convertedDate: KhmerDate?,
    onConvert: (String, String, String) -> Unit
) {
    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
    val lang = LocalAppLanguage.current

    var inYear by remember { mutableStateOf(year) }
    var inMonth by remember { mutableStateOf(month) }
    var inDay by remember { mutableStateOf(day) }
    var inputError by remember { mutableStateOf<String?>(null) }

    // Drives the "converting…" → "result" animation. Bumped on every successful
    // convert so the styled transition replays even for the same date.
    var convertSeq by remember { mutableIntStateOf(0) }
    var isConverting by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(convertedDate != null) }

    // Continuous spin for the converting indicator.
    val spinTransition = rememberInfiniteTransition(label = "convert-spin")
    val spin by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)),
        label = "spin"
    )

    fun validate(): Boolean {
        val y = inYear.toIntOrNull()
        val m = inMonth.toIntOrNull()
        val d = inDay.toIntOrNull()
        // Real number of days in a Gregorian month (leap-year aware) so invalid
        // dates like 30 February or 31 April are rejected before conversion.
        fun maxDayOf(yy: Int, mm: Int) = when (mm) {
            2 -> if ((yy % 4 == 0 && yy % 100 != 0) || yy % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        return when {
            y == null || m == null || d == null -> {
                inputError = tr(lang, "សូមបញ្ចូលតម្លៃជាលេខ (numbers only)", "Please enter numbers only")
                false
            }
            y < 1900 || y > 2200 -> {
                inputError = tr(lang, "ឆ្នាំ ១៩០០–២២០០ ប៉ុណ្ណោះ (Year 1900–2200 only)", "Year 1900–2200 only")
                false
            }
            m < 1 || m > 12 -> {
                inputError = tr(lang, "ខែត្រូវស្ថិតក្នុងចន្លោះ ១–១២ (Month 1–12)", "Month must be 1–12")
                false
            }
            d < 1 || d > maxDayOf(y, m) -> {
                inputError = tr(lang, "ថ្ងៃមិនត្រឹមត្រូវសម្រាប់ខែនេះ (Invalid day for this month)", "Invalid day for this month")
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

    // Play the styled conversion sequence whenever a convert is triggered.
    LaunchedEffect(convertSeq) {
        if (convertSeq == 0) {
            showResult = convertedDate != null
            return@LaunchedEffect
        }
        isConverting = true
        showResult = false
        delay(520)
        isConverting = false
        showResult = true
    }

    fun triggerConvert() {
        if (validate()) {
            onConvert(inYear, inMonth, inDay)
            convertSeq++
        }
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
                    // Gently rotates while a conversion is in flight.
                    Text("🔄", fontSize = 28.sp, modifier = Modifier.rotate(if (isConverting) spin else 0f))
                    Column {
                        Text(tr("បំលែងថ្ងៃខែ", "Date Converter"), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = SandText)
                        Text(tr("ពីគ្រីស្ដសករាជ → ចន្ទគតិខ្មែរ", "Gregorian → Khmer Lunar"), fontSize = 10.sp, color = SkyBlue)
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
                            tr("ថ្ងៃខែគ្រីស្ដសករាជ", "Gregorian Date"),
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
                            Text(tr("📅 ថ្ងៃនេះ", "📅 Today"), fontSize = 10.sp, color = SkyBlue, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Day / Month / Year fields
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateNumberField(
                            label = tr("ថ្ងៃ (Day)", "Day"),
                            value = inDay,
                            onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) inDay = v },
                            placeholder = "DD",
                            maxLen = 2,
                            weight = 1f
                        )
                        DateNumberField(
                            label = tr("ខែ (Month)", "Month"),
                            value = inMonth,
                            onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 2) inMonth = v },
                            placeholder = "MM",
                            maxLen = 2,
                            weight = 1f
                        )
                        DateNumberField(
                            label = tr("ឆ្នាំ (Year)", "Year"),
                            value = inYear,
                            onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 4) inYear = v },
                            placeholder = "YYYY",
                            maxLen = 4,
                            weight = 1.6f
                        )
                    }

                    // Error banner
                    AnimatedVisibility(
                        visible = inputError != null,
                        enter = fadeIn(tween(durationMillis = 180, easing = FastOutSlowInEasing)) +
                                expandVertically(tween(durationMillis = 220, easing = FastOutSlowInEasing)),
                        exit = fadeOut(tween(durationMillis = 140, easing = FastOutSlowInEasing)) +
                                shrinkVertically(tween(durationMillis = 180, easing = FastOutSlowInEasing))
                    ) {
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

                    // Year range hint
                    Text(
                        tr("ឆ្នាំដែលគ្រប: ១៩០០ – ២២០០", "Supported years: 1900 – 2200"),
                        fontSize = 9.sp,
                        color = DimColor,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )

                    // Convert button — shows a spinner + label while converting
                    Button(
                        onClick = { triggerConvert() },
                        enabled = !isConverting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SkyBlue,
                            disabledContainerColor = SkyBlue.copy(0.6f)
                        ),
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
                            if (isConverting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = OnAccent
                                )
                                Text(tr("កំពុងបំលែង…", "Converting…"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            } else {
                                Text("⇅", fontSize = 18.sp, color = OnAccent)
                                Text(tr("បំលែងជាចន្ទគតិ", "Convert to Lunar"), color = OnAccent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // Animated result — fades, slides up and scales in once conversion finishes
        item {
            AnimatedVisibility(
                visible = showResult && !isConverting && convertedDate != null,
                enter = fadeIn(tween(durationMillis = 380, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(durationMillis = 380, easing = FastOutSlowInEasing)) { it / 4 } +
                        scaleIn(tween(durationMillis = 380, easing = FastOutSlowInEasing), initialScale = 0.96f),
                exit = fadeOut(tween(durationMillis = 160, easing = FastOutSlowInEasing))
            ) {
                convertedDate?.let { ConversionResultCard(it) }
            }
        }
    }
}

/**
 * One Gregorian date-part input (Day / Month / Year). Extracted so the three
 * fields share styling. Must be called inside a [RowScope] so [weight] applies.
 */
@Composable
private fun RowScope.DateNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLen: Int,
    weight: Float
) {
    val colors = LocalAppColors.current
    Column(modifier = Modifier.weight(weight)) {
        Text(label, fontSize = 9.sp, color = colors.dim)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = colors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            placeholder = {
                Text(
                    placeholder, color = colors.dim, fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                )
            },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = colors.card,
                focusedContainerColor = colors.card,
                unfocusedBorderColor = colors.border,
                focusedBorderColor = SkyBlue
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** The styled lunar-date result card shown after a successful conversion. */
@Composable
private fun ConversionResultCard(date: KhmerDate) {
    val colors = LocalAppColors.current
    val lang = LocalAppLanguage.current

    Card(
        colors = CardDefaults.cardColors(containerColor = colors.card),
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
                    tr("លទ្ធផលថ្ងៃចន្ទគតិ", "Lunar Date Result"),
                    fontSize = 10.sp,
                    color = TraditionalGold,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(date.moonEmoji, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(TraditionalGold.copy(0.25f)))
            Spacer(modifier = Modifier.height(12.dp))

            // Gregorian date echo
            val mIdx = (date.month - 1).coerceIn(0, 11)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SkyBlue))
                Text(
                    text = if (lang == AppLanguage.EN)
                        "${date.dayOfWeekEn}, ${date.day} ${gregMonth(lang, mIdx)} ${date.year}"
                    else
                        "ថ្ងៃ${date.dayOfWeek} ទី${num(lang, date.day)} ${gregMonth(lang, mIdx)} ${num(lang, date.year)}",
                    fontSize = 12.sp,
                    color = colors.subText
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main lunar result — large
            Text(
                text = if (lang == AppLanguage.EN)
                    "${lunarDayLabel(lang, date)} ${lunarMonth(lang, date.lunarMonthName)}"
                else
                    "${lunarDayLabel(lang, date)} ខែ${date.lunarMonthName}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colors.text
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
                    Text(tr("ព.ស. ${num(lang, date.BE)}", "BE ${date.BE}"), fontSize = 10.sp, color = TraditionalGold, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .background(LotusPink.copy(0.12f), RoundedCornerShape(20.dp))
                        .border(1.dp, LotusPink.copy(0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(zodiac(lang, date.zodiac), fontSize = 10.sp, color = LotusPink, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .background(SkyBlue.copy(0.12f), RoundedCornerShape(20.dp))
                        .border(1.dp, SkyBlue.copy(0.3f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${date.moonEmoji} " + if (lang == AppLanguage.EN) {
                            if (date.isWaxing) "Waxing" else "Waning"
                        } else {
                            if (date.isWaxing) "កើត" else "រោច"
                        },
                        fontSize = 10.sp,
                        color = SkyBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Holiday alert
            if (date.holiday != null) {
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
                        Text(tr("ថ្ងៃបុណ្យ", "Holiday"), fontSize = 9.sp, color = LotusPink.copy(0.7f))
                        Text(localizeDual(lang, date.holiday!!), fontSize = 12.sp, color = LotusPink, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Auspicious alert
            if (date.isAuspicious) {
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
                        Text(tr("ថ្ងៃមង្គល", "Auspicious"), fontSize = 9.sp, color = JadeGreen.copy(0.7f))
                        Text(
                            localizeDual(lang, date.auspiciousType ?: tr(lang, "ថ្ងៃល្អ", "Good day")),
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
