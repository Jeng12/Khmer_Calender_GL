package com.example.widget

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.MainActivity
import com.example.R
import com.example.calendar.KhmerCalendarHelper
import com.example.calendar.KhmerCalendarHelper.toKhmerNumeral
import com.example.calendar.KhmerDate
import com.example.core.GREG_MONTHS_KM
import com.example.core.WEEKDAYS_SHORT_KM
import java.util.Calendar

// ── Glassmorphism palette (mirrors the JSX design) ─────────────────────────
private val GOLD = Color(0xFFC8973A)
private val GOLD_LIGHT = Color(0xFFE8B84B)
private val RED = Color(0xFFE53935)
private val TEXT = Color(0xFFF5F0E8)
private val SUB = Color(0x8CF5F0E8)   // ~55% cream
private val DIM = Color(0x47F5F0E8)   // ~28% cream
private val DARK = Color(0xFF0D0820)
private val HAIRLINE = Color(0x14FFFFFF)

private fun cp(c: Color) = ColorProvider(c)

// Responsive breakpoints — one widget, four layouts. Glance picks the largest
// size that fits the slot the user dropped/resized the widget into.
//
// Tuned for large high-density grids like the Vivo X200 Ultra (~411dp wide,
// ~100dp×140dp cells in OriginOS). The heights matter most: Large requires a
// 3-row-tall slot so a short, wide 4×2 lands on Medium (not the full calendar),
// and Square requires a 2-row slot so a 2×1 lands on Mini.
private val SIZE_MINI = DpSize(170.dp, 110.dp)   // 2×1  (short & wide)
private val SIZE_SQUARE = DpSize(170.dp, 170.dp) // 2×2  (square)
private val SIZE_MEDIUM = DpSize(300.dp, 120.dp) // 4×2  (wide & short)
private val SIZE_LARGE = DpSize(300.dp, 320.dp)  // 4×4  (full calendar)

/** A single upcoming public holiday (day-of-month, month, name). */
private data class UpHoliday(val day: Int, val month: Int, val name: String)

/** All data the widget renders, computed off the main thread in [provideGlance]. */
private data class WidgetData(
    val today: KhmerDate,
    val year: Int,
    val month: Int,           // 1-based
    val daysInMonth: Int,
    val firstDow: Int,        // 0 = Sunday
    val monthDays: List<KhmerDate>,
    val currentWeek: List<Int?>,
    val upcoming: List<UpHoliday>
)

/**
 * Glassmorphism home-screen widget for the Khmer calendar. Resizable across four
 * sizes; renders today's Khmer lunar date, moon phase, zodiac, holidays and (at
 * larger sizes) a week strip and full month grid. All data comes from
 * [KhmerCalendarHelper] — fully offline. Tapping opens the app.
 */
class KhmerCalendarWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SIZE_MINI, SIZE_SQUARE, SIZE_MEDIUM, SIZE_LARGE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = buildData()
        provideContent {
            val context = LocalContext.current
            GlassRoot(context) {
                when (LocalSize.current) {
                    SIZE_LARGE -> LargeLayout(data)
                    SIZE_MEDIUM -> MediumLayout(data)
                    SIZE_SQUARE -> SquareLayout(data)
                    else -> MiniLayout(data)
                }
            }
        }
    }

    // ── Data assembly ──────────────────────────────────────────────────────
    private fun buildData(): WidgetData {
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        val today = KhmerCalendarHelper.getKhmerDate(y, m, d)
        val monthDays = KhmerCalendarHelper.getGregorianMonthDays(y, m)
        val daysInMonth = monthDays.size
        val firstDow = ((KhmerCalendarHelper.getSerialDay(y, m, 1) + 2) % 7 + 7) % 7

        // Current Sun..Sat week, blanks for days outside this month.
        val todayDow = ((KhmerCalendarHelper.getSerialDay(y, m, d) + 2) % 7 + 7) % 7
        val weekStart = d - todayDow
        val currentWeek = (0 until 7).map { i ->
            val day = weekStart + i
            if (day in 1..daysInMonth) day else null
        }

        return WidgetData(
            today = today,
            year = y,
            month = m,
            daysInMonth = daysInMonth,
            firstDow = firstDow,
            monthDays = monthDays,
            currentWeek = currentWeek,
            upcoming = computeUpcoming(y, m, d)
        )
    }

    /** Scan forward up to a year, collecting the next distinct public holidays. */
    private fun computeUpcoming(y: Int, m: Int, d: Int, limit: Int = 3): List<UpHoliday> {
        val cal = Calendar.getInstance().apply { set(y, m - 1, d, 12, 0, 0) }
        val out = ArrayList<UpHoliday>(limit)
        val seen = HashSet<String>()
        var i = 0
        while (i < 366 && out.size < limit) {
            val kd = KhmerCalendarHelper.getKhmerDate(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            val h = kd.holiday
            if (h != null && seen.add(h)) {
                out.add(UpHoliday(cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.MONTH) + 1, h))
            }
            cal.add(Calendar.DAY_OF_MONTH, 1)
            i++
        }
        return out
    }
}

// ── Shared glass container ─────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun GlassRoot(context: Context, content: @androidx.compose.runtime.Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_glass_bg))
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    ) {
        // Gold shimmer along the top edge.
        Spacer(GlanceModifier.fillMaxWidth().height(2.dp).background(ImageProvider(R.drawable.widget_shimmer)))
        content()
    }
}

@androidx.compose.runtime.Composable
private fun Divider() {
    Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(cp(HAIRLINE)))
}

// ── Mini 2×1 ───────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun MiniLayout(data: WidgetData) {
    val t = data.today
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text("ថ្ងៃ${t.dayOfWeek}", style = TextStyle(color = cp(GOLD), fontSize = 11.sp))
            Text(
                toKhmerNumeral(t.day),
                style = TextStyle(color = cp(TEXT), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                "${GREG_MONTHS_KM[t.month - 1]} ${toKhmerNumeral(t.year)}",
                style = TextStyle(color = cp(SUB), fontSize = 11.sp)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t.moonEmoji, style = TextStyle(fontSize = 32.sp))
            Text(t.zodiac, style = TextStyle(color = cp(GOLD), fontSize = 10.sp))
            if (t.holiday != null) {
                Text("🔴", style = TextStyle(fontSize = 9.sp))
            }
        }
    }
}

// ── Square 2×2 ───────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun SquareLayout(data: WidgetData) {
    val t = data.today
    Column(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ថ្ងៃ${t.dayOfWeek}", style = TextStyle(color = cp(GOLD), fontSize = 11.sp),
                modifier = GlanceModifier.defaultWeight())
            Text(t.moonEmoji, style = TextStyle(fontSize = 24.sp))
        }
        Spacer(GlanceModifier.defaultWeight())
        Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                toKhmerNumeral(t.day),
                style = TextStyle(color = cp(TEXT), fontSize = 54.sp, fontWeight = FontWeight.Bold)
            )
            Text(GREG_MONTHS_KM[t.month - 1], style = TextStyle(color = cp(SUB), fontSize = 12.sp))
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ថ្ងៃ${t.lunarDayName}", style = TextStyle(color = cp(DIM), fontSize = 10.sp),
                modifier = GlanceModifier.defaultWeight())
            Text(t.zodiac, style = TextStyle(color = cp(GOLD), fontSize = 10.sp))
        }
    }
}

// ── Medium 4×2 ───────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun MediumLayout(data: WidgetData) {
    val t = data.today
    Row(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
        // Left: date block
        Column(modifier = GlanceModifier.width(96.dp)) {
            Text("ថ្ងៃ${t.dayOfWeek}", style = TextStyle(color = cp(GOLD), fontSize = 11.sp))
            Text(
                toKhmerNumeral(t.day),
                style = TextStyle(color = cp(TEXT), fontSize = 52.sp, fontWeight = FontWeight.Bold)
            )
            Text(GREG_MONTHS_KM[t.month - 1], style = TextStyle(color = cp(SUB), fontSize = 12.sp))
            Spacer(GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.moonEmoji, style = TextStyle(fontSize = 22.sp))
                Spacer(GlanceModifier.width(5.dp))
                Column {
                    Text("ថ្ងៃ${t.lunarDayName}", style = TextStyle(color = cp(SUB), fontSize = 10.sp))
                    Text(t.zodiac, style = TextStyle(color = cp(GOLD), fontSize = 10.sp))
                }
            }
        }

        Spacer(GlanceModifier.width(12.dp))
        Spacer(GlanceModifier.width(1.dp).fillMaxHeight().background(cp(HAIRLINE)))
        Spacer(GlanceModifier.width(12.dp))

        // Right: week strip + upcoming holidays
        Column(modifier = GlanceModifier.defaultWeight()) {
            WeekStrip(data)
            Spacer(GlanceModifier.height(8.dp))
            Divider()
            Spacer(GlanceModifier.height(6.dp))
            Text("ថ្ងៃបុណ្យខាងមុខ", style = TextStyle(color = cp(GOLD), fontSize = 10.sp))
            Spacer(GlanceModifier.height(3.dp))
            if (data.upcoming.isEmpty()) {
                Text("គ្មានថ្ងៃបុណ្យ", style = TextStyle(color = cp(DIM), fontSize = 10.sp))
            } else {
                data.upcoming.take(2).forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.padding(bottom = 3.dp)) {
                        Box(GlanceModifier.size(4.dp).background(ImageProvider(R.drawable.widget_holiday_dot))) {}
                        Spacer(GlanceModifier.width(5.dp))
                        Text(
                            "${toKhmerNumeral(h.day)} ${GREG_MONTHS_KM[h.month - 1]} · ${h.name}",
                            style = TextStyle(color = cp(SUB), fontSize = 10.sp),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun WeekStrip(data: WidgetData) {
    val headers = listOf("អា", "ច", "អ", "ព", "ព្រ", "សុ", "ស")
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        headers.forEach { h ->
            Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                Text(h, style = TextStyle(color = cp(DIM), fontSize = 9.sp))
            }
        }
    }
    Spacer(GlanceModifier.height(2.dp))
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        data.currentWeek.forEach { day ->
            Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                val isToday = day != null && day == data.today.day
                if (isToday) {
                    Box(
                        modifier = GlanceModifier.size(20.dp).background(ImageProvider(R.drawable.widget_today_circle)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(toKhmerNumeral(day!!), style = TextStyle(color = cp(DARK), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }
                } else {
                    Text(
                        day?.let { toKhmerNumeral(it) } ?: "",
                        style = TextStyle(color = cp(SUB), fontSize = 12.sp)
                    )
                }
            }
        }
    }
}

// ── Large 4×4 ───────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun LargeLayout(data: WidgetData) {
    val t = data.today
    Column(modifier = GlanceModifier.fillMaxSize().padding(18.dp)) {
        // Header
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "${GREG_MONTHS_KM[data.month - 1]} ${toKhmerNumeral(data.year)}",
                    style = TextStyle(color = cp(TEXT), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    "${t.lunarMonthName} · ${t.zodiac}",
                    style = TextStyle(color = cp(SUB), fontSize = 11.sp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t.moonEmoji, style = TextStyle(fontSize = 28.sp))
                Text("ថ្ងៃ${toKhmerNumeral(t.lunarDayVal)}", style = TextStyle(color = cp(DIM), fontSize = 10.sp))
            }
        }
        Spacer(GlanceModifier.height(10.dp))

        // Weekday headers
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            WEEKDAYS_SHORT_KM.forEachIndexed { i, h ->
                Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                    val color = when (i) {
                        0 -> RED
                        6 -> Color(0xFF5599FF)
                        else -> DIM
                    }
                    Text(h, style = TextStyle(color = cp(color), fontSize = 11.sp))
                }
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        Divider()
        Spacer(GlanceModifier.height(4.dp))

        // Calendar grid
        val cells = buildList {
            repeat(data.firstDow) { add(null) }
            for (day in 1..data.daysInMonth) add(day)
            while (size % 7 != 0) add(null)
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                week.forEachIndexed { col, day ->
                    GridCell(data, day, col)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun androidx.glance.layout.RowScope.GridCell(data: WidgetData, day: Int?, col: Int) {
    if (day == null) {
        Box(modifier = GlanceModifier.defaultWeight()) {}
        return
    }
    val kd = data.monthDays.getOrNull(day - 1)
    val isToday = day == data.today.day
    val dayColor = when {
        isToday -> GOLD
        col == 0 -> RED
        col == 6 -> Color(0xFF5599FF)
        else -> TEXT
    }
    var cellMod = GlanceModifier.defaultWeight().padding(1.dp)
    if (isToday) cellMod = cellMod.background(ImageProvider(R.drawable.widget_today_bg))

    Box(modifier = cellMod, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                toKhmerNumeral(day),
                style = TextStyle(
                    color = cp(dayColor),
                    fontSize = 13.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
            )
            if (kd != null) {
                val lunarHighlight = kd.lunarDayVal == 1 || kd.lunarDayVal == 15
                Text(
                    toKhmerNumeral(kd.lunarDayVal),
                    style = TextStyle(color = cp(if (lunarHighlight) GOLD else DIM), fontSize = 9.sp)
                )
            }
            if (kd?.holiday != null) {
                Box(GlanceModifier.size(4.dp).background(ImageProvider(R.drawable.widget_holiday_dot))) {}
            }
        }
    }
}

/** Manifest-registered receiver that hosts [KhmerCalendarWidget]. */
class KhmerCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KhmerCalendarWidget()
}
