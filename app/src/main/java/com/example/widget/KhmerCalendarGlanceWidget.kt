package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.KhmerCalendarHelper
import com.example.ui.theme.DeepAmethyst
import com.example.ui.theme.GoldSubText
import com.example.ui.theme.JadeGreen
import com.example.ui.theme.LotusPink
import com.example.ui.theme.NightBlack
import com.example.ui.theme.PlumCard
import com.example.ui.theme.SandText
import com.example.ui.theme.TraditionalGold
import java.util.Calendar

class KhmerCalendarGlanceWidget : GlanceAppWidget() {

    companion object {
        val MINI_SIZE   = DpSize(100.dp, 50.dp)
        val MEDIUM_SIZE = DpSize(200.dp, 100.dp)
        val LARGE_SIZE  = DpSize(200.dp, 200.dp)
    }

    override val sizeMode = SizeMode.Responsive(setOf(MINI_SIZE, MEDIUM_SIZE, LARGE_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                when {
                    size.width >= LARGE_SIZE.width && size.height >= LARGE_SIZE.height ->
                        LargeWidgetContent()
                    size.width >= MEDIUM_SIZE.width ->
                        MediumWidgetContent()
                    else ->
                        MiniWidgetContent()
                }
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun todayKhmerInfo(): Triple<String, String, String> {
    val cal = Calendar.getInstance()
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return try {
        val kd = KhmerCalendarHelper.getKhmerDate(y, m, d)
        Triple(kd.moonEmoji, kd.dayOfWeek, kd.lunarMonthName)
    } catch (_: Exception) {
        Triple("🌕", "អាទិត្យ", "ចេត្រ")
    }
}

private fun todayGregorianString(): Pair<String, String> {
    val cal = Calendar.getInstance()
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val months = listOf("មករា","កុម្ភៈ","មីនា","មេសា","ឧសភា","មិថុនា",
        "កក្កដា","សីហា","កញ្ញា","តុលា","វិច្ឆិកា","ធ្នូ")
    val monthKm = months.getOrElse(cal.get(Calendar.MONTH)) { "" }
    val dayStr = KhmerCalendarHelper.toKhmerNumeral(day)
    val yearStr = KhmerCalendarHelper.toKhmerNumeral(cal.get(Calendar.YEAR))
    return Pair("ថ្ងៃទី $dayStr ខែ$monthKm ឆ្នាំ$yearStr", "${cal.get(Calendar.YEAR)}")
}

private fun upcomingHolidays(count: Int): List<Pair<String, String>> {
    val cal = Calendar.getInstance()
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val results = mutableListOf<Pair<String, String>>()
    for (day in 1..31) {
        if (results.size >= count) break
        try {
            val kd = KhmerCalendarHelper.getKhmerDate(y, m, day)
            if (kd.holiday != null) {
                val months = listOf("","មករា","កុម្ភៈ","មីនា","មេសា","ឧសភា","មិថុនា",
                    "កក្កដា","សីហា","កញ្ញា","តុលា","វិច្ឆិកា","ធ្នូ")
                results.add(Pair(kd.holiday!!, "${months.getOrElse(m){""}}-${KhmerCalendarHelper.toKhmerNumeral(day)}"))
            }
        } catch (_: Exception) {}
    }
    return results
}

// ── Mini Widget (2×1) ────────────────────────────────────────────────────────

@Composable
private fun MiniWidgetContent() {
    val (moonEmoji, dayOfWeek, _) = todayKhmerInfo()
    val (dateKm, _) = todayGregorianString()

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(NightBlack.copy(alpha = 0.92f)))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$moonEmoji ថ្ងៃ$dayOfWeek",
                style = TextStyle(
                    color = ColorProvider(TraditionalGold),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = dateKm,
                style = TextStyle(
                    color = ColorProvider(SandText),
                    fontSize = 10.sp
                ),
                maxLines = 1
            )
        }
    }
}

// ── Medium Widget (4×2) ──────────────────────────────────────────────────────

@Composable
private fun MediumWidgetContent() {
    val (moonEmoji, dayOfWeek, lunarMonth) = todayKhmerInfo()
    val (dateKm, _) = todayGregorianString()
    val holidays = upcomingHolidays(2)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(PlumCard.copy(alpha = 0.95f)))
            .padding(12.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = moonEmoji,
                    style = TextStyle(fontSize = 22.sp)
                )
                Spacer(GlanceModifier.width(6.dp))
                Column {
                    Text(
                        text = "ថ្ងៃ$dayOfWeek",
                        style = TextStyle(
                            color = ColorProvider(TraditionalGold),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "ខែ$lunarMonth",
                        style = TextStyle(color = ColorProvider(GoldSubText), fontSize = 10.sp)
                    )
                }
            }
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = dateKm,
                style = TextStyle(color = ColorProvider(SandText), fontSize = 10.sp),
                maxLines = 1
            )
            Spacer(GlanceModifier.height(8.dp))
            // Upcoming holidays
            if (holidays.isEmpty()) {
                Text(
                    text = "🎉 គ្មានថ្ងៃបុណ្យខែនេះ",
                    style = TextStyle(color = ColorProvider(GoldSubText), fontSize = 9.sp)
                )
            } else {
                holidays.forEach { (name, date) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎉 ", style = TextStyle(fontSize = 9.sp))
                        Text(
                            text = "$name ($date)",
                            style = TextStyle(color = ColorProvider(LotusPink), fontSize = 9.sp),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

// ── Large Widget (4×4) ───────────────────────────────────────────────────────

@Composable
private fun LargeWidgetContent() {
    val cal = Calendar.getInstance()
    val year = cal.get(Calendar.YEAR)
    val month = cal.get(Calendar.MONTH) + 1
    val todayDay = cal.get(Calendar.DAY_OF_MONTH)
    val (moonEmoji, dayOfWeek, lunarMonth) = todayKhmerInfo()
    val (dateKm, _) = todayGregorianString()
    val holidays = upcomingHolidays(3)

    val monthNames = listOf("","មករា","កុម្ភៈ","មីនា","មេសា","ឧសភា","មិថុនា",
        "កក្កដា","សីហា","កញ្ញា","តុលា","វិច្ឆិកា","ធ្នូ")

    // Build a mini 7-col calendar for the current month
    val daysList = try { KhmerCalendarHelper.getGregorianMonthDays(year, month) } catch (_: Exception) { emptyList() }
    val serialDay = try { KhmerCalendarHelper.getSerialDay(year, month, 1) } catch (_: Exception) { 1 }
    val startOffset = ((serialDay + 2) % 7 + 7) % 7
    val totalCells = startOffset + daysList.size
    val rows = (totalCells + 6) / 7

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(DeepAmethyst.copy(alpha = 0.97f)))
            .padding(10.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Title row
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(moonEmoji, style = TextStyle(fontSize = 18.sp))
                Spacer(GlanceModifier.width(6.dp))
                Column {
                    Text(
                        "ថ្ងៃ$dayOfWeek",
                        style = TextStyle(color = ColorProvider(TraditionalGold), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "${monthNames.getOrElse(month){""}} $year",
                        style = TextStyle(color = ColorProvider(GoldSubText), fontSize = 10.sp)
                    )
                }
            }
            Spacer(GlanceModifier.height(6.dp))

            // Day header row
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                listOf("អា","ច","អ","ព","ព្រ","សុ","ស").forEach { label ->
                    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                        Text(label, style = TextStyle(color = ColorProvider(GoldSubText), fontSize = 7.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
            Spacer(GlanceModifier.height(2.dp))

            // Calendar grid (limited to 4 rows to fit)
            val maxRows = minOf(rows, 5)
            for (row in 0 until maxRows) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIdx = row * 7 + col
                        val dayNum = cellIdx - startOffset + 1
                        val isValid = cellIdx >= startOffset && dayNum <= daysList.size
                        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                            if (isValid) {
                                val isToday = dayNum == todayDay
                                val isHoliday = daysList.getOrNull(dayNum - 1)?.holiday != null
                                Text(
                                    text = dayNum.toString(),
                                    style = TextStyle(
                                        color = ColorProvider(when {
                                            isToday   -> TraditionalGold
                                            isHoliday -> LotusPink
                                            col == 0 || col == 6 -> LotusPink.copy(alpha = 0.7f)
                                            else -> SandText
                                        }),
                                        fontSize = 9.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(GlanceModifier.height(8.dp))
            // Upcoming holidays
            holidays.take(2).forEach { (name, date) ->
                Text(
                    text = "🎉 $name ($date)",
                    style = TextStyle(color = ColorProvider(LotusPink), fontSize = 8.sp),
                    maxLines = 1
                )
            }
        }
    }
}
