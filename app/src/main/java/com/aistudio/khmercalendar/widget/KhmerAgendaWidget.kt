package com.aistudio.khmercalendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.aistudio.khmercalendar.core.AppLanguage
import com.aistudio.khmercalendar.core.gregMonth
import com.aistudio.khmercalendar.core.num
import com.aistudio.khmercalendar.core.numStr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Calendar

/** A note or reminder, ready to render. [hour] = -1 for notes (no time). */
internal data class AgendaItem(
    val sortKey: Long,
    val isEvent: Boolean,
    val year: Int,
    val month: Int,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val text: String
)

/** Reads the user's notes + reminders from SharedPreferences into agenda items. */
internal object AgendaRepository {

    private const val MAX_ITEMS = 50

    /** Every note + event, parsed from prefs (no date filtering). */
    private fun loadAll(context: Context): List<AgendaItem> {
        val items = ArrayList<AgendaItem>()

        // Events / reminders (khmer_calendar_alarms → "alarms": JSON array)
        runCatching {
            val prefs = context.getSharedPreferences("khmer_calendar_alarms", Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString("alarms", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                // Work-shift reminders have their own "Work shifts" section on the
                // widget, so keep them out of the Notes/Events list.
                if (o.optString("kind") == "shift") continue
                val triggerMs = o.optLong("triggerMs", 0L)
                val c = Calendar.getInstance().apply { timeInMillis = triggerMs }
                items += AgendaItem(
                    sortKey = triggerMs,
                    isEvent = true,
                    year = c.get(Calendar.YEAR),
                    month = c.get(Calendar.MONTH) + 1,
                    day = c.get(Calendar.DAY_OF_MONTH),
                    hour = c.get(Calendar.HOUR_OF_DAY),
                    minute = c.get(Calendar.MINUTE),
                    text = o.optString("title").ifBlank { o.optString("message") }
                )
            }
        }

        // Notes (khmer_calendar_notes → key "YEAR_MONTH_DAY" → text)
        runCatching {
            val prefs = context.getSharedPreferences("khmer_calendar_notes", Context.MODE_PRIVATE)
            for ((key, value) in prefs.all) {
                val text = (value as? String)?.trim().orEmpty()
                if (text.isEmpty()) continue
                val parts = key.split("_")
                if (parts.size != 3) continue
                val y = parts[0].toIntOrNull() ?: continue
                val m = parts[1].toIntOrNull() ?: continue
                val d = parts[2].toIntOrNull() ?: continue
                val dayStart = Calendar.getInstance().apply {
                    set(y, m - 1, d, 0, 0, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                items += AgendaItem(dayStart, false, y, m, d, -1, -1, text)
            }
        }
        return items
    }

    /** Upcoming items (today onward) — used by the dedicated agenda widget. */
    fun load(context: Context): List<AgendaItem> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return loadAll(context).filter { it.sortKey >= todayStart }.sortedBy { it.sortKey }.take(MAX_ITEMS)
    }

    /** Items falling within the given month — used by the calendar widget. */
    fun loadForMonth(context: Context, year: Int, month: Int): List<AgendaItem> =
        loadAll(context).filter { it.year == year && it.month == month }.sortedBy { it.sortKey }
}

/**
 * A dedicated home-screen widget that lists the user's upcoming notes and
 * reminders as a scrollable agenda. Companion to [KhmerCalendarWidget]; shares
 * the same glass styling, language and theme settings ([WidgetPrefs]).
 */
class KhmerAgendaWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val lang = WidgetPrefs.resolveLang(context)
        val style = styleFor(context)
        val items = withContext(Dispatchers.IO) { AgendaRepository.load(context) }
        provideContent {
            val ctx = LocalContext.current
            GlassRoot(ctx, style.bgRes) {
                AgendaBody(items, lang, style)
            }
        }
    }
}

@Composable
private fun AgendaBody(items: List<AgendaItem>, lang: AppLanguage, style: WidgetStyle) {
    Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
            Text("📅", style = TextStyle(fontSize = 15.sp))
            Spacer(GlanceModifier.width(6.dp))
            Text(
                if (lang == AppLanguage.EN) "Notes & Events" else "កំណត់ត្រា និងព្រឹត្តិការណ៍",
                style = TextStyle(color = cp(GOLD), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        GlassDivider(style.hairline)
        Spacer(GlanceModifier.height(6.dp))

        if (items.isEmpty()) {
            Text(
                if (lang == AppLanguage.EN) "No upcoming notes or events" else "គ្មានកំណត់ត្រា ឬព្រឹត្តិការណ៍ខាងមុខ",
                style = TextStyle(color = cp(style.dim), fontSize = 11.sp)
            )
        } else {
            LazyColumn(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                items(items) { item -> AgendaRow(item, lang, style) }
            }
        }
    }
}

@Composable
private fun AgendaRow(item: AgendaItem, lang: AppLanguage, style: WidgetStyle) {
    val dateLabel = buildString {
        append("${num(lang, item.day)} ${gregMonth(lang, item.month - 1)}")
        if (item.isEvent && item.hour >= 0) {
            append(" · ")
            append(numStr(lang, "%02d:%02d".format(item.hour, item.minute)))
        }
    }
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (item.isEvent) "⏰" else "📝", style = TextStyle(fontSize = 14.sp))
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                item.text.ifBlank { if (lang == AppLanguage.EN) "(untitled)" else "(គ្មានចំណងជើង)" },
                style = TextStyle(color = cp(style.text), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                maxLines = 2
            )
            Text(dateLabel, style = TextStyle(color = cp(if (item.isEvent) GOLD else style.sub), fontSize = 9.sp), maxLines = 1)
        }
    }
}

/** Manifest-registered receiver that hosts [KhmerAgendaWidget]. */
class KhmerAgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KhmerAgendaWidget()
}
