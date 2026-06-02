package com.example

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontFamily
import org.json.JSONArray
import org.json.JSONObject

/* ─────────────────────────────────────────────────────────────
   Day items — user-created Notes & Events stored per calendar day
   ───────────────────────────────────────────────────────────── */

enum class DayItemType { NOTE, EVENT }

/**
 * A single user-created entry attached to a Gregorian calendar day.
 * [time] is an optional "HH:mm" string used by events that also set a reminder.
 */
data class DayItem(
    val id: String,
    val type: DayItemType,
    val title: String,
    val time: String? = null
)

/** A [DayItem] together with the Gregorian day it belongs to. */
data class DatedDayItem(
    val year: Int,
    val month: Int,
    val day: Int,
    val item: DayItem
)

/**
 * Lightweight SharedPreferences-backed store for per-day notes & events.
 * Each day key ("year_month_day") holds a JSON array of [DayItem]s.
 * Legacy values (a plain note string) are read transparently as a single note.
 */
object CalendarStore {
    private const val PREFS = "khmer_calendar_notes"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun keyFor(year: Int, month: Int, day: Int) = "${year}_${month}_${day}"

    private fun parseKey(key: String): Triple<Int, Int, Int>? {
        val parts = key.split("_")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        val d = parts[2].toIntOrNull() ?: return null
        return Triple(y, m, d)
    }

    private fun decode(raw: String?): List<DayItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val title = o.optString("title", "")
                if (title.isBlank()) return@mapNotNull null
                DayItem(
                    id = o.optString("id", i.toString()),
                    type = if (o.optString("type", "note") == "event") DayItemType.EVENT else DayItemType.NOTE,
                    title = title,
                    time = if (o.has("time") && !o.isNull("time")) o.optString("time").ifBlank { null } else null
                )
            }
        } catch (_: Exception) {
            // Legacy: a single free-text note stored as a raw string.
            listOf(DayItem(id = "legacy", type = DayItemType.NOTE, title = raw))
        }
    }

    private fun encode(items: List<DayItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("type", if (item.type == DayItemType.EVENT) "event" else "note")
                put("title", item.title)
                if (item.time != null) put("time", item.time)
            })
        }
        return arr.toString()
    }

    fun getItems(context: Context, year: Int, month: Int, day: Int): List<DayItem> =
        decode(prefs(context).getString(keyFor(year, month, day), ""))

    private fun saveItems(context: Context, year: Int, month: Int, day: Int, items: List<DayItem>) {
        val editor = prefs(context).edit()
        val key = keyFor(year, month, day)
        if (items.isEmpty()) editor.remove(key) else editor.putString(key, encode(items))
        editor.apply()
    }

    fun addItem(context: Context, year: Int, month: Int, day: Int, item: DayItem) {
        saveItems(context, year, month, day, getItems(context, year, month, day) + item)
    }

    fun removeItem(context: Context, year: Int, month: Int, day: Int, itemId: String) {
        saveItems(context, year, month, day, getItems(context, year, month, day).filterNot { it.id == itemId })
    }

    /** Days within the given month that hold at least one note or event. */
    fun daysWithItems(context: Context, year: Int, month: Int): Set<Int> =
        (1..31).filter { d -> getItems(context, year, month, d).isNotEmpty() }.toSet()

    /** Every user item across all stored days, sorted chronologically. */
    fun allItems(context: Context): List<DatedDayItem> {
        val all = prefs(context).all
        val result = mutableListOf<DatedDayItem>()
        for ((key, value) in all) {
            val ymd = parseKey(key) ?: continue
            decode(value as? String).forEach { item ->
                result.add(DatedDayItem(ymd.first, ymd.second, ymd.third, item))
            }
        }
        return result.sortedWith(
            compareBy({ it.year }, { it.month }, { it.day }, { it.item.time ?: "" })
        )
    }
}

/* ─────────────────────────────────────────────────────────────
   Display / personalization settings (font, opacity, glass …)
   ───────────────────────────────────────────────────────────── */

enum class AppFontChoice { DEFAULT, SERIF, MONOSPACE, SANS }fun AppFontChoice.toFontFamily(): FontFamily = when (this) {
    AppFontChoice.SERIF -> FontFamily.Serif
    AppFontChoice.MONOSPACE -> FontFamily.Monospace
    AppFontChoice.SANS -> FontFamily.SansSerif
    AppFontChoice.DEFAULT -> FontFamily.Default
}

fun AppFontChoice.label(): String = when (this) {
    AppFontChoice.DEFAULT -> "Default"
    AppFontChoice.SERIF -> "Serif"
    AppFontChoice.MONOSPACE -> "Mono"
    AppFontChoice.SANS -> "Sans"
}

/** Accent colour applied to the home-screen widget, chosen in the app. */
enum class WidgetAccent { GOLD, ROSE, JADE, BLUE }

/** ARGB int for use with RemoteViews (non-Compose). */
fun WidgetAccent.colorInt(): Int = when (this) {
    WidgetAccent.GOLD -> 0xFFC8973A.toInt()
    WidgetAccent.ROSE -> 0xFFE8768A.toInt()
    WidgetAccent.JADE -> 0xFF4DAF7C.toInt()
    WidgetAccent.BLUE -> 0xFF7BA7BC.toInt()
}

fun WidgetAccent.label(): String = when (this) {
    WidgetAccent.GOLD -> "Gold"
    WidgetAccent.ROSE -> "Rose"
    WidgetAccent.JADE -> "Jade"
    WidgetAccent.BLUE -> "Blue"
}

/**
 * App-wide personalization controlled from the Profile screen.
 *  - [fontScale]      multiplies every sp text size (0.8–1.4)
 *  - [fontFamily]     chosen typeface
 *  - [boldText]       the "weight" setting — heavier body text
 *  - [bgOpacity]      how opaque the background surface is (0.5–1.0);
 *                     lower values reveal a decorative gradient (color change)
 *  - [glassEffect]    adds a frosted translucent sheen over the app
 *  - [widgetOpacity]  transparency of the home-screen widget (0.2–1.0)
 *  - [widgetAccent]   accent colour of the home-screen widget
 */
data class DisplaySettings(
    val fontScale: Float = 1f,
    val fontFamily: AppFontChoice = AppFontChoice.DEFAULT,
    val boldText: Boolean = false,
    val bgOpacity: Float = 1f,
    val glassEffect: Boolean = false,
    val widgetOpacity: Float = 1f,
    val widgetAccent: WidgetAccent = WidgetAccent.GOLD
) {
    companion object {
        fun load(context: Context): DisplaySettings {
            val p = context.getSharedPreferences("khmer_calendar_prefs", Context.MODE_PRIVATE)
            return DisplaySettings(
                fontScale = p.getFloat("disp_font_scale", 1f),
                fontFamily = runCatching {
                    AppFontChoice.valueOf(p.getString("disp_font_family", "DEFAULT") ?: "DEFAULT")
                }.getOrDefault(AppFontChoice.DEFAULT),
                boldText = p.getBoolean("disp_bold_text", false),
                bgOpacity = p.getFloat("disp_bg_opacity", 1f),
                glassEffect = p.getBoolean("disp_glass_effect", false),
                widgetOpacity = p.getFloat("widget_opacity", 1f),
                widgetAccent = runCatching {
                    WidgetAccent.valueOf(p.getString("widget_accent", "GOLD") ?: "GOLD")
                }.getOrDefault(WidgetAccent.GOLD)
            )
        }
    }

    fun save(context: Context) {
        context.getSharedPreferences("khmer_calendar_prefs", Context.MODE_PRIVATE)
            .edit()
            .putFloat("disp_font_scale", fontScale)
            .putString("disp_font_family", fontFamily.name)
            .putBoolean("disp_bold_text", boldText)
            .putFloat("disp_bg_opacity", bgOpacity)
            .putBoolean("disp_glass_effect", glassEffect)
            .putFloat("widget_opacity", widgetOpacity)
            .putString("widget_accent", widgetAccent.name)
            .apply()
    }
}

val LocalDisplaySettings = compositionLocalOf { DisplaySettings() }
