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
import com.example.calendar.KhmerDate
import com.example.core.AppLanguage
import com.example.core.gregMonth
import com.example.core.localizeDual
import com.example.core.lunarDayLabel
import com.example.core.lunarMonth
import com.example.core.num
import com.example.core.numStr
import com.example.core.weekdayLabels
import com.example.core.zodiac
import java.util.Calendar

// Shared glass palette + WidgetStyle live in WidgetTheme.kt (GOLD, RED, cp(), …).

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
    val upcoming: List<UpHoliday>,
    val notes: List<AgendaItem>,
    val events: List<AgendaItem>
)

/** Everything a layout needs: the data plus the resolved language and theme. */
private data class WidgetUi(
    val data: WidgetData,
    val lang: AppLanguage,
    val style: WidgetStyle
)

// ── Localized label helpers ────────────────────────────────────────────────
private fun dowText(lang: AppLanguage, d: KhmerDate) =
    if (lang == AppLanguage.EN) d.dayOfWeekEn else "ថ្ងៃ ${d.dayOfWeek}"

private fun lunarDayText(lang: AppLanguage, d: KhmerDate) =
    if (lang == AppLanguage.EN) lunarDayLabel(lang, d) else "ថ្ងៃ ${d.lunarDayName}"

private fun lunarMonthText(lang: AppLanguage, d: KhmerDate) =
    if (lang == AppLanguage.EN) lunarMonth(lang, d.lunarMonthName) else "ខែ ${d.lunarMonthName}"

private fun upcomingHeader(lang: AppLanguage) =
    if (lang == AppLanguage.EN) "Upcoming holidays" else "ថ្ងៃបុណ្យខាងមុខ"

private fun noHolidaysText(lang: AppLanguage) =
    if (lang == AppLanguage.EN) "No holidays" else "គ្មានថ្ងៃបុណ្យ"

private fun noteHeader(lang: AppLanguage) =
    if (lang == AppLanguage.EN) "Note" else "កំណត់ត្រា"

private fun eventHeader(lang: AppLanguage) =
    if (lang == AppLanguage.EN) "Event" else "ព្រឹត្តិការណ៍"

/** "5 Jun · text" for a note, "5 Jun 09:00 · title" for an event. */
private fun agendaLineLabel(lang: AppLanguage, item: AgendaItem): String = buildString {
    append("${num(lang, item.day)} ${gregMonth(lang, item.month - 1)}")
    if (item.isEvent && item.hour >= 0) append(" ${numStr(lang, "%02d:%02d".format(item.hour, item.minute))}")
    append(" · ${item.text}")
}

/**
 * Glassmorphism home-screen widget for the Khmer calendar. Resizable across four
 * sizes; renders today's Khmer lunar date, moon phase, zodiac, holidays and (at
 * larger sizes) a week strip and full month grid. Language and light/dark theme
 * follow the user's choices in [WidgetPrefs]. All data comes from
 * [KhmerCalendarHelper] — fully offline. Tapping opens the app.
 */
class KhmerCalendarWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SIZE_MINI, SIZE_SQUARE, SIZE_MEDIUM, SIZE_LARGE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = buildData(context)
        val lang = WidgetPrefs.resolveLang(context)
        val style = if (WidgetPrefs.resolveDark(context)) DARK_STYLE else LIGHT_STYLE
        provideContent {
            val ctx = LocalContext.current
            val ui = WidgetUi(data, lang, style)
            GlassRoot(ctx, style.bgRes) {
                when (LocalSize.current) {
                    SIZE_LARGE -> LargeLayout(ui)
                    SIZE_MEDIUM -> MediumLayout(ui)
                    SIZE_SQUARE -> SquareLayout(ui)
                    else -> MiniLayout(ui)
                }
            }
        }
    }

    // ── Data assembly ──────────────────────────────────────────────────────
    private fun buildData(context: Context): WidgetData {
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

        val agenda = AgendaRepository.loadForMonth(context, y, m)
        return WidgetData(
            today = today,
            year = y,
            month = m,
            daysInMonth = daysInMonth,
            firstDow = firstDow,
            monthDays = monthDays,
            currentWeek = currentWeek,
            upcoming = computeUpcoming(y, m, d),
            notes = agenda.filter { !it.isEvent },
            events = agenda.filter { it.isEvent }
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

// ── Mini 2×1 ───────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun MiniLayout(ui: WidgetUi) {
    val t = ui.data.today
    val lang = ui.lang
    val s = ui.style
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(dowText(lang, t), style = TextStyle(color = cp(GOLD), fontSize = 11.sp))
            Text(
                num(lang, t.day),
                style = TextStyle(color = cp(s.text), fontSize = 36.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                "${gregMonth(lang, t.month - 1)} ${num(lang, t.year)}",
                style = TextStyle(color = cp(s.sub), fontSize = 11.sp)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(t.moonEmoji, style = TextStyle(fontSize = 32.sp))
            Text(zodiac(lang, t.zodiac), style = TextStyle(color = cp(GOLD), fontSize = 10.sp))
            if (t.holiday != null) {
                Text("🔴", style = TextStyle(fontSize = 9.sp))
            }
        }
    }
}

// ── Square 2×2 ───────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun SquareLayout(ui: WidgetUi) {
    val t = ui.data.today
    val lang = ui.lang
    val s = ui.style
    Column(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(dowText(lang, t), style = TextStyle(color = cp(GOLD), fontSize = 11.sp),
                modifier = GlanceModifier.defaultWeight())
            Text(t.moonEmoji, style = TextStyle(fontSize = 24.sp))
        }
        Spacer(GlanceModifier.defaultWeight())
        Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                num(lang, t.day),
                style = TextStyle(color = cp(s.text), fontSize = 54.sp, fontWeight = FontWeight.Bold)
            )
            Text(gregMonth(lang, t.month - 1), style = TextStyle(color = cp(s.sub), fontSize = 13.sp))
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(lunarDayText(lang, t), style = TextStyle(color = cp(s.dim), fontSize = 11.sp),
                modifier = GlanceModifier.defaultWeight())
            Text(zodiac(lang, t.zodiac), style = TextStyle(color = cp(GOLD), fontSize = 11.sp))
        }
    }
}

// ── Medium 4×2 ───────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun MediumLayout(ui: WidgetUi) {
    val t = ui.data.today
    val lang = ui.lang
    val s = ui.style
    Row(modifier = GlanceModifier.fillMaxSize().padding(16.dp)) {
        // Left: date block
        Column(modifier = GlanceModifier.width(96.dp)) {
            Text(dowText(lang, t), style = TextStyle(color = cp(GOLD), fontSize = 11.sp))
            Text(
                num(lang, t.day),
                style = TextStyle(color = cp(s.text), fontSize = 52.sp, fontWeight = FontWeight.Bold)
            )
            Text(gregMonth(lang, t.month - 1), style = TextStyle(color = cp(s.sub), fontSize = 13.sp))
            Spacer(GlanceModifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.moonEmoji, style = TextStyle(fontSize = 22.sp))
                Spacer(GlanceModifier.width(5.dp))
                Column {
                    Text(lunarDayText(lang, t), style = TextStyle(color = cp(s.sub), fontSize = 11.sp))
                    Text(zodiac(lang, t.zodiac), style = TextStyle(color = cp(GOLD), fontSize = 11.sp))
                }
            }
        }

        Spacer(GlanceModifier.width(12.dp))
        Spacer(GlanceModifier.width(1.dp).fillMaxHeight().background(cp(s.hairline)))
        Spacer(GlanceModifier.width(12.dp))

        // Right: mini week calendar on top, then compact Holiday / Note / Event lines
        Column(modifier = GlanceModifier.defaultWeight()) {
            WeekStrip(ui)
            Spacer(GlanceModifier.height(5.dp))
            GlassDivider(s.hairline)
            
            Column {
                Spacer(GlanceModifier.height(6.dp))
                Text("⛱️ ថ្ងៃបុណ្យខាងមុខ", style = TextStyle(color = cp(GOLD), fontSize = 12.sp))
                ui.data.upcoming.take(1).forEach { h ->
                    IconLine("", "${num(lang, h.day)} ${gregMonth(lang, h.month - 1)} · ${localizeDual(lang, h.name)}", s)
                }
                Spacer(GlanceModifier.height(6.dp))
                Text("📝 កំណត់ចំណាំ", style = TextStyle(color = cp(GOLD), fontSize = 12.sp))
                ui.data.notes.take(1).forEach { n -> IconLine("", agendaLineLabel(lang, n), s) }
                Spacer(GlanceModifier.height(6.dp))
                Text("⏰ ព្រឹត្តិការណ៍", style = TextStyle(color = cp(GOLD), fontSize = 12.sp))
                ui.data.events.take(1).forEach { e -> IconLine("", agendaLineLabel(lang, e), s) }
            }
        }
    }
}

/** A compact one-line entry with a leading category icon (Holiday / Note / Event). */
@androidx.compose.runtime.Composable
private fun IconLine(icon: String, label: String, s: WidgetStyle) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.padding(top = 1.dp)) {
        Text(icon, style = TextStyle(fontSize = 9.sp))
        Spacer(GlanceModifier.width(4.dp))
        Text(label, style = TextStyle(color = cp(s.sub), fontSize = 11.sp), maxLines = 1)
    }
}

/** Gold section header with a leading icon (Holiday / Note / Event). */
@androidx.compose.runtime.Composable
private fun SectionHeader(icon: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, style = TextStyle(fontSize = 10.sp))
        Spacer(GlanceModifier.width(4.dp))
        Text(label, style = TextStyle(color = cp(GOLD), fontSize = 11.sp, fontWeight = FontWeight.Bold))
    }
}

/** A compact one-line agenda entry under a [SectionHeader]. */
@androidx.compose.runtime.Composable
private fun AgendaLine(label: String, s: WidgetStyle) {
    Text(
        "·  $label",
        style = TextStyle(color = cp(s.sub), fontSize = 11.sp),
        maxLines = 1,
        modifier = GlanceModifier.padding(top = 1.dp)
    )
}

@androidx.compose.runtime.Composable
private fun WeekStrip(ui: WidgetUi) {
    val s = ui.style
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        weekdayLabels(ui.lang).forEach { h ->
            Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                Text(h, style = TextStyle(color = cp(s.dim), fontSize = 11.sp))
            }
        }
    }
    Spacer(GlanceModifier.height(2.dp))
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        ui.data.currentWeek.forEach { day ->
            Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                val isToday = day != null && day == ui.data.today.day
                if (isToday) {
                    Box(
                        modifier = GlanceModifier.size(20.dp).background(ImageProvider(R.drawable.widget_today_circle)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(num(ui.lang, day), style = TextStyle(color = cp(DARK), fontSize = 12.sp, fontWeight = FontWeight.Bold))
                    }
                } else {
                    Text(
                        day?.let { num(ui.lang, it) } ?: "",
                        style = TextStyle(color = cp(s.sub), fontSize = 12.sp)
                    )
                }
            }
        }
    }
}

// ── Large 4×4 ───────────────────────────────────────────────────────────────
@androidx.compose.runtime.Composable
private fun LargeLayout(ui: WidgetUi) {
    val t = ui.data.today
    val lang = ui.lang
    val s = ui.style
    Column(modifier = GlanceModifier.fillMaxSize().padding(18.dp)) {
        // Header
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "${gregMonth(lang, ui.data.month - 1)} ${num(lang, ui.data.year)}",
                    style = TextStyle(color = cp(s.text), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                )
                Text(
                    "${lunarMonth(lang, t.lunarMonthName)} · ${zodiac(lang, t.zodiac)}",
                    style = TextStyle(color = cp(s.sub), fontSize = 11.sp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t.moonEmoji, style = TextStyle(fontSize = 28.sp))
                Text(lunarDayText(lang, t), style = TextStyle(color = cp(s.dim), fontSize = 10.sp))
            }
        }
        Spacer(GlanceModifier.height(10.dp))

        // Weekday headers
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            weekdayLabels(lang).forEachIndexed { i, h ->
                Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                    val color = when (i) {
                        0 -> RED
                        6 -> SAT_BLUE
                        else -> s.dim
                    }
                    Text(h, style = TextStyle(color = cp(color), fontSize = 11.sp))
                }
            }
        }
        Spacer(GlanceModifier.height(4.dp))
        GlassDivider(s.hairline)
        Spacer(GlanceModifier.height(4.dp))

        // Calendar grid
        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            val cells = buildList {
                repeat(ui.data.firstDow) { add(null) }
                for (day in 1..ui.data.daysInMonth) add(day)
                while (size % 7 != 0) add(null)
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    week.forEachIndexed { col, day ->
                        GridCell(ui, day, col)
                    }
                }
            }
        }

        // Footer: next note + next event, beneath the grid
        val note = ui.data.notes.firstOrNull()
        val event = ui.data.events.firstOrNull()
        if (note != null || event != null) {
            Column {
                Spacer(GlanceModifier.height(5.dp))
                GlassDivider(s.hairline)
                Spacer(GlanceModifier.height(4.dp))
                if (note != null) {
                    Text("📝 ${agendaLineLabel(lang, note)}",
                        style = TextStyle(color = cp(s.sub), fontSize = 10.sp), maxLines = 1)
                }
                if (event != null) {
                    Text("⏰ ${agendaLineLabel(lang, event)}",
                        style = TextStyle(color = cp(s.sub), fontSize = 10.sp), maxLines = 1)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun androidx.glance.layout.RowScope.GridCell(ui: WidgetUi, day: Int?, col: Int) {
    val s = ui.style
    if (day == null) {
        Box(modifier = GlanceModifier.defaultWeight()) {}
        return
    }
    val kd = ui.data.monthDays.getOrNull(day - 1)
    val isToday = day == ui.data.today.day
    val dayColor = when {
        isToday -> GOLD
        col == 0 -> RED
        col == 6 -> SAT_BLUE
        else -> s.text
    }
    var cellMod = GlanceModifier.defaultWeight().padding(1.dp)
    if (isToday) cellMod = cellMod.background(ImageProvider(R.drawable.widget_today_bg))

    Box(modifier = cellMod, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                num(ui.lang, day),
                style = TextStyle(
                    color = cp(dayColor),
                    fontSize = 13.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                )
            )
            if (kd != null) {
                val lunarHighlight = kd.lunarDayVal == 1 || kd.lunarDayVal == 15
                Text(
                    num(ui.lang, kd.lunarDayVal),
                    style = TextStyle(color = cp(if (lunarHighlight) GOLD else s.dim), fontSize = 9.sp)
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
