package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.layout.defaultWeight
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.KhmerCalendarHelper
import java.util.Calendar

// ── Bright warm palette for widget (renders on any home screen) ──────────────
private val WBg     = Color(0xFFFFF8EE)  // warm ivory
private val WCard   = Color(0xFFF5EDE0)  // warm card
private val WText   = Color(0xFF2C1F0E)  // dark brown
private val WSub    = Color(0xFF6B5028)  // gold-brown
private val WDim    = Color(0xFF9E7A4A)  // muted gold
private val WGold   = Color(0xFFC8973A)  // traditional gold
private val WRed    = Color(0xFFC0392B)  // crimson
private val WBlue   = Color(0xFF5588CC)  // Saturday blue
private val WBorder = Color(0xFFE2C98A)  // gold border
private val WDivider= Color(0xFFE8D5B0)  // divider line

// ── Data helpers ──────────────────────────────────────────────────────────────
private data class TodayInfo(
    val day: Int,
    val month: Int,
    val year: Int,
    val dayKm: String,       // Khmer numeral
    val monthKm: String,     // Khmer month name
    val yearKm: String,      // Khmer year numeral
    val dayOfWeek: String,   // Khmer day name
    val moonEmoji: String,
    val lunarLabel: String,  // e.g. "ថ្ងៃ ០៥ កើត"
    val zodiac: String,
    val lunarMonthKm: String
)

private val KM_MONTHS = listOf(
    "មករា","កុម្ភៈ","មីនា","មេសា","ឧសភា","មិថុនា",
    "កក្កដា","សីហា","កញ្ញា","តុលា","វិច្ឆិកា","ធ្នូ"
)

private fun buildTodayInfo(): TodayInfo {
    val cal = Calendar.getInstance()
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    return try {
        val kd = KhmerCalendarHelper.getKhmerDate(y, m, d)
        val lunarLabel = "ថ្ងៃ ${KhmerCalendarHelper.toKhmerNumeral(kd.lunarDayVal)} " +
            if (kd.isWaxing) "កើត" else "រោច"
        TodayInfo(
            day = d, month = m, year = y,
            dayKm = KhmerCalendarHelper.toKhmerNumeral(d),
            monthKm = KM_MONTHS.getOrElse(m - 1) { "" },
            yearKm = KhmerCalendarHelper.toKhmerNumeral(y),
            dayOfWeek = kd.dayOfWeek,
            moonEmoji = kd.moonEmoji,
            lunarLabel = lunarLabel,
            zodiac = kd.zodiac,
            lunarMonthKm = kd.lunarMonthName
        )
    } catch (_: Exception) {
        TodayInfo(d, m, y,
            KhmerCalendarHelper.toKhmerNumeral(d),
            KM_MONTHS.getOrElse(m - 1) { "" },
            KhmerCalendarHelper.toKhmerNumeral(y),
            "អាទិត្យ", "🌕", "ថ្ងៃ ០១ កើត", "ជូត", "ចេត្រ"
        )
    }
}

private data class GridCell(
    val dayNum: Int,         // 0 = empty cell
    val isToday: Boolean,
    val isHoliday: Boolean,
    val isWeekend: Boolean,
    val lunarNumKm: String
)

private fun buildMonthGrid(year: Int, month: Int, todayDay: Int): List<GridCell> {
    val days = try { KhmerCalendarHelper.getGregorianMonthDays(year, month) } catch (_: Exception) { return emptyList() }
    val serial = try { KhmerCalendarHelper.getSerialDay(year, month, 1) } catch (_: Exception) { 1 }
    val startOffset = ((serial + 2) % 7 + 7) % 7
    val cells = mutableListOf<GridCell>()
    repeat(startOffset) { cells.add(GridCell(0, false, false, false, "")) }
    days.forEachIndexed { idx, kd ->
        val dayNum = idx + 1
        val col = (startOffset + idx) % 7
        cells.add(GridCell(
            dayNum = dayNum,
            isToday = dayNum == todayDay,
            isHoliday = kd.holiday != null,
            isWeekend = col == 0 || col == 6,
            lunarNumKm = KhmerCalendarHelper.toKhmerNumeral(kd.lunarDayVal)
        ))
    }
    return cells
}

private fun upcomingHolidays(count: Int): List<Pair<String, String>> {
    val cal = Calendar.getInstance()
    val y = cal.get(Calendar.YEAR)
    val m = cal.get(Calendar.MONTH) + 1
    val d = cal.get(Calendar.DAY_OF_MONTH)
    val results = mutableListOf<Pair<String, String>>()
    for (day in d..31) {
        if (results.size >= count) break
        try {
            val kd = KhmerCalendarHelper.getKhmerDate(y, m, day)
            if (kd.holiday != null) {
                results.add(Pair(
                    kd.holiday!!,
                    "${KM_MONTHS.getOrElse(m - 1){""}} ${KhmerCalendarHelper.toKhmerNumeral(day)}"
                ))
            }
        } catch (_: Exception) {}
    }
    return results
}

// ── Widget class ──────────────────────────────────────────────────────────────

class KhmerCalendarGlanceWidget : GlanceAppWidget() {

    companion object {
        val MINI_H = DpSize(130.dp, 60.dp)   // 2×1
        val MINI_S = DpSize(130.dp, 130.dp)  // 2×2
        val MEDIUM = DpSize(260.dp, 130.dp)  // 4×2
        val LARGE  = DpSize(260.dp, 260.dp)  // 4×4
    }

    override val sizeMode = SizeMode.Responsive(setOf(MINI_H, MINI_S, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = buildTodayInfo()
        provideContent {
            GlanceTheme {
                val size = LocalSize.current
                when {
                    size.width >= MEDIUM.width && size.height >= LARGE.height ->
                        LargeWidget(today)
                    size.width >= MEDIUM.width ->
                        MediumWidget(today)
                    size.height >= MINI_S.height ->
                        MiniSquareWidget(today)
                    else ->
                        MiniHWidget(today)
                }
            }
        }
    }
}

// ── Shared header shimmer bar ─────────────────────────────────────────────────
@Composable
private fun GoldBar() {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(2.dp)
            .background(ColorProvider(WGold))
    ) {}
}

@Composable
private fun Divider(vertical: Boolean = false) {
    if (vertical) {
        Box(
            modifier = GlanceModifier
                .width(1.dp)
                .fillMaxHeight()
                .background(ColorProvider(WDivider))
        ) {}
    } else {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(1.dp)
                .background(ColorProvider(WDivider))
        ) {}
    }
}

// ── Mini 2×1 Widget ───────────────────────────────────────────────────────────
@Composable
private fun MiniHWidget(today: TodayInfo) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WBg))
    ) {
        GoldBar()
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: day + date
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = today.dayOfWeek,
                    style = TextStyle(
                        color = ColorProvider(WGold),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = today.dayKm,
                    style = TextStyle(
                        color = ColorProvider(WText),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "${today.monthKm} ${today.yearKm}",
                    style = TextStyle(color = ColorProvider(WSub), fontSize = 7.sp)
                )
            }
            // Right: moon + zodiac + lunar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = GlanceModifier.padding(start = 6.dp)
            ) {
                Text(text = today.moonEmoji, style = TextStyle(fontSize = 22.sp))
                Text(
                    text = today.zodiac,
                    style = TextStyle(color = ColorProvider(WGold), fontSize = 8.sp)
                )
                Text(
                    text = today.lunarLabel,
                    style = TextStyle(color = ColorProvider(WDim), fontSize = 7.sp),
                    maxLines = 1
                )
            }
        }
    }
}

// ── Mini 2×2 Widget ───────────────────────────────────────────────────────────
@Composable
private fun MiniSquareWidget(today: TodayInfo) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WBg))
    ) {
        GoldBar()
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Top row: day name | moon
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = today.dayOfWeek,
                    style = TextStyle(
                        color = ColorProvider(WGold),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Text(text = today.moonEmoji, style = TextStyle(fontSize = 18.sp))
            }
            Spacer(GlanceModifier.height(4.dp))
            // Big date number centered
            Box(
                modifier = GlanceModifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = today.dayKm,
                    style = TextStyle(
                        color = ColorProvider(WText),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                )
            }
            Text(
                text = today.monthKm,
                style = TextStyle(
                    color = ColorProvider(WSub),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = GlanceModifier.fillMaxWidth()
            )
            Spacer(GlanceModifier.defaultWeight())
            Divider()
            Spacer(GlanceModifier.height(4.dp))
            // Bottom row: lunar day | zodiac
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = today.lunarLabel,
                    style = TextStyle(color = ColorProvider(WDim), fontSize = 8.sp),
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1
                )
                Text(
                    text = today.zodiac,
                    style = TextStyle(color = ColorProvider(WGold), fontSize = 8.sp)
                )
            }
        }
    }
}

// ── Medium 4×2 Widget ─────────────────────────────────────────────────────────
@Composable
private fun MediumWidget(today: TodayInfo) {
    val holidays = upcomingHolidays(3)
    val cal = Calendar.getInstance()
    val todayDow = cal.get(Calendar.DAY_OF_WEEK) - 1  // 0=Sun..6=Sat
    val daysInMonth = java.util.Calendar.getInstance().apply {
        set(today.year, today.month - 1, 1)
    }.getActualMaximum(Calendar.DAY_OF_MONTH)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WBg))
    ) {
        GoldBar()
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // ── Left: date block ─────────────────────────────────────────────
            Column(modifier = GlanceModifier.width(88.dp)) {
                Text(
                    text = today.dayOfWeek,
                    style = TextStyle(color = ColorProvider(WGold), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = today.dayKm,
                    style = TextStyle(color = ColorProvider(WText), fontSize = 44.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    text = today.monthKm,
                    style = TextStyle(color = ColorProvider(WSub), fontSize = 10.sp)
                )
                Text(
                    text = today.yearKm,
                    style = TextStyle(color = ColorProvider(WDim), fontSize = 9.sp)
                )
                Spacer(GlanceModifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = today.moonEmoji, style = TextStyle(fontSize = 18.sp))
                    Spacer(GlanceModifier.width(4.dp))
                    Column {
                        Text(
                            text = today.lunarLabel,
                            style = TextStyle(color = ColorProvider(WSub), fontSize = 7.sp),
                            maxLines = 1
                        )
                        Text(
                            text = today.zodiac,
                            style = TextStyle(color = ColorProvider(WGold), fontSize = 7.sp)
                        )
                    }
                }
            }

            Spacer(GlanceModifier.width(8.dp))
            Divider(vertical = true)
            Spacer(GlanceModifier.width(8.dp))

            // ── Right: this-week strip + upcoming events ──────────────────────
            Column(modifier = GlanceModifier.defaultWeight()) {
                // Week header labels
                val headers = listOf("Su","Mo","Tu","We","Th","Fr","Sa")
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    headers.forEach { h ->
                        Box(
                            modifier = GlanceModifier.defaultWeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = h,
                                style = TextStyle(color = ColorProvider(WDim), fontSize = 7.sp)
                            )
                        }
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
                // Current week days
                val weekStart = today.day - todayDow
                val weekDays = (0..6).map { i -> weekStart + i }
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    weekDays.forEach { d ->
                        Box(
                            modifier = GlanceModifier.defaultWeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            val isValid = d in 1..daysInMonth
                            val isToday = d == today.day
                            Box(
                                modifier = if (isToday)
                                    GlanceModifier.background(ColorProvider(WGold)).padding(horizontal = 5.dp, vertical = 1.dp)
                                else GlanceModifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isValid) KhmerCalendarHelper.toKhmerNumeral(d) else "",
                                    style = TextStyle(
                                        color = ColorProvider(
                                            when {
                                                !isValid -> Color.Transparent
                                                isToday  -> WBg
                                                else     -> WSub
                                            }
                                        ),
                                        fontSize = 9.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(GlanceModifier.height(6.dp))
                Divider()
                Spacer(GlanceModifier.height(4.dp))
                // Upcoming holidays
                Text(
                    text = "ព្រឹត្តិការណ៍ខាងមុខ",
                    style = TextStyle(color = ColorProvider(WGold), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.height(2.dp))
                if (holidays.isEmpty()) {
                    Text(
                        text = "គ្មានព្រឹត្តិការណ៍",
                        style = TextStyle(color = ColorProvider(WDim), fontSize = 7.sp)
                    )
                } else {
                    holidays.forEach { (name, date) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = GlanceModifier
                                    .width(4.dp)
                                    .height(4.dp)
                                    .background(ColorProvider(WRed))
                            ) {}
                            Spacer(GlanceModifier.width(4.dp))
                            Text(
                                text = "$date — $name",
                                style = TextStyle(color = ColorProvider(WSub), fontSize = 7.sp),
                                maxLines = 1
                            )
                        }
                        Spacer(GlanceModifier.height(2.dp))
                    }
                }
            }
        }
    }
}

// ── Large 4×4 Widget ─────────────────────────────────────────────────────────
@Composable
private fun LargeWidget(today: TodayInfo) {
    val cells = buildMonthGrid(today.year, today.month, today.day)
    val weekHeaders = listOf("អា","ច","អ","ព","ព្រ","សុ","ស")
    val rows = (cells.size + 6) / 7

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(WBg))
    ) {
        GoldBar()
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            // ── Header row ──────────────────────────────────────────────────
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        text = "${today.monthKm} ${today.yearKm}",
                        style = TextStyle(
                            color = ColorProvider(WText),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "${today.lunarMonthKm} · ${today.zodiac}",
                        style = TextStyle(color = ColorProvider(WSub), fontSize = 8.sp)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = today.moonEmoji, style = TextStyle(fontSize = 22.sp))
                    Text(
                        text = "ថ្ងៃ${today.dayKm}",
                        style = TextStyle(color = ColorProvider(WDim), fontSize = 7.sp)
                    )
                }
            }
            Spacer(GlanceModifier.height(6.dp))

            // ── Weekday header row ───────────────────────────────────────────
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                weekHeaders.forEachIndexed { i, h ->
                    Box(
                        modifier = GlanceModifier.defaultWeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = h,
                            style = TextStyle(
                                color = ColorProvider(
                                    when (i) { 0 -> WRed; 6 -> WBlue; else -> WDim }
                                ),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(2.dp))
            Divider()
            Spacer(GlanceModifier.height(3.dp))

            // ── Calendar grid ────────────────────────────────────────────────
            val maxRows = minOf(rows, 6)
            for (row in 0 until maxRows) {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    for (col in 0..6) {
                        val cellIdx = row * 7 + col
                        val cell = cells.getOrNull(cellIdx)
                        Box(
                            modifier = GlanceModifier.defaultWeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (cell != null && cell.dayNum > 0) {
                                val textColor = when {
                                    cell.isToday   -> WBg
                                    cell.isHoliday -> WRed
                                    col == 6       -> WBlue
                                    col == 0       -> WRed.copy(alpha = 0.8f)
                                    else           -> WText
                                }
                                Box(
                                    modifier = if (cell.isToday)
                                        GlanceModifier.background(ColorProvider(WGold)).padding(horizontal = 4.dp, vertical = 1.dp)
                                    else GlanceModifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = KhmerCalendarHelper.toKhmerNumeral(cell.dayNum),
                                        style = TextStyle(
                                            color = ColorProvider(textColor),
                                            fontSize = 10.sp,
                                            fontWeight = if (cell.isToday) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(GlanceModifier.height(1.dp))
            }

            Spacer(GlanceModifier.height(6.dp))
            Divider()
            Spacer(GlanceModifier.height(4.dp))

            // ── Today detail strip ───────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = today.dayKm,
                    style = TextStyle(
                        color = ColorProvider(WGold),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(GlanceModifier.width(8.dp))
                Column {
                    Text(
                        text = "${today.dayOfWeek} ${today.monthKm} ${today.yearKm}",
                        style = TextStyle(color = ColorProvider(WText), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${today.lunarLabel} ខែ${today.lunarMonthKm}",
                        style = TextStyle(color = ColorProvider(WSub), fontSize = 8.sp)
                    )
                }
                Spacer(GlanceModifier.defaultWeight())
                Text(text = today.moonEmoji, style = TextStyle(fontSize = 18.sp))
            }
        }
    }
}
