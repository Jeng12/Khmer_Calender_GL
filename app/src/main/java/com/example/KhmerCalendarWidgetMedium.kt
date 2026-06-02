package com.example

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Medium (4×2) widget: a left "today" panel (day numeral, month/year, moon
 * and lunar info) beside a compact two-week mini grid and an upcoming-events
 * list. Honours the same [DisplaySettings] personalization as the large widget.
 */
class KhmerCalendarWidgetMedium : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> refreshAllWidgets(context)
        }
    }

    companion object {
        private fun columnOf(year: Int, month: Int, day: Int): Int =
            ((KhmerCalendarHelper.getSerialDay(year, month, day) + 2) % 7 + 7) % 7

        @Suppress("DEPRECATION")
        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val s = DisplaySettings.load(context)
            val accent = s.widgetAccent.colorInt()
            val lang = widgetLang(context)

            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH) + 1
            val today = now.get(Calendar.DAY_OF_MONTH)
            val todayDate = KhmerCalendarHelper.getKhmerDate(year, month, today)
            val days = KhmerCalendarHelper.getGregorianMonthDays(year, month)

            val views = RemoteViews(context.packageName, R.layout.widget_khmer_medium)
            views.applyBackground(R.id.widget_bg, s)

            // Left panel.
            views.applyText(
                R.id.widget_m_dow,
                if (lang == AppLanguage.EN) todayDate.dayOfWeekEn else todayDate.dayOfWeek,
                11f, s, W_GOLD_SUB
            )
            views.applyText(R.id.widget_m_day, num(lang, today), 44f, s, W_MOON_WHEAT)
            views.applyText(R.id.widget_m_month, gregMonth(lang, month - 1), 13f, s, W_SAND)
            views.applyText(R.id.widget_m_year, num(lang, year), 12f, s, W_GOLD_SUB)
            views.applyText(R.id.widget_m_moon, todayDate.moonEmoji, 22f, s)
            views.applyText(
                R.id.widget_m_lunar,
                if (lang == AppLanguage.EN) lunarDayLabel(lang, todayDate)
                else "ថ្ងៃ${lunarDayLabel(lang, todayDate)}",
                10f, s, accent
            )
            views.applyText(R.id.widget_m_zodiac, zodiac(lang, todayDate.zodiac), 9f, s, W_GOLD_SUB)

            // Weekday header.
            views.removeAllViews(R.id.widget_m_weekdays)
            weekdayLabels(lang).forEachIndexed { idx, label ->
                val cell = RemoteViews(context.packageName, R.layout.widget_weekday_cell)
                cell.applyText(
                    R.id.widget_wd_text, label, 9f, s,
                    when (idx) { 0 -> W_CRIMSON; 6 -> W_SKY; else -> W_GOLD_SUB }
                )
                views.addView(R.id.widget_m_weekdays, cell)
            }

            // Mini grid: two weeks starting from the Sunday of today's week.
            views.removeAllViews(R.id.widget_m_grid)
            val startDom = today - columnOf(year, month, today)
            for (row in 0 until 2) {
                val rowViews = RemoteViews(context.packageName, R.layout.widget_grid_row)
                for (col in 0..6) {
                    val cell = RemoteViews(context.packageName, R.layout.widget_mini_cell)
                    val dom = startDom + row * 7 + col
                    if (dom in 1..days.size) {
                        val isToday = dom == today
                        val color = when {
                            isToday -> accent
                            days[dom - 1].holiday != null -> W_LOTUS
                            col == 0 -> W_CRIMSON
                            col == 6 -> W_SKY
                            else -> W_SAND
                        }
                        cell.applyText(R.id.widget_mini_text, num(lang, dom), 13f, s, color)
                        if (isToday) cell.setInt(R.id.widget_mini_text, "setBackgroundResource", R.drawable.widget_cell_today)
                    } else {
                        cell.setTextViewText(R.id.widget_mini_text, "")
                    }
                    rowViews.addView(R.id.widget_row, cell)
                }
                views.addView(R.id.widget_m_grid, rowViews)
            }

            // Upcoming events title + list.
            views.applyText(
                R.id.widget_m_events_title,
                if (lang == AppLanguage.EN) "UPCOMING EVENTS" else "ព្រឹត្តិការណ៍ខាងមុខ",
                10f, s, accent
            )
            views.removeAllViews(R.id.widget_m_events)
            val upcoming = CalendarStore.upcomingItems(context, year, month, today, 3)
            if (upcoming.isEmpty()) {
                val line = RemoteViews(context.packageName, R.layout.widget_event_line)
                line.applyText(
                    R.id.widget_event_text,
                    if (lang == AppLanguage.EN) "No upcoming events" else "គ្មានព្រឹត្តិការណ៍ខាងមុខ",
                    10f, s, W_DIM
                )
                views.addView(R.id.widget_m_events, line)
            } else {
                upcoming.forEach { d ->
                    val line = RemoteViews(context.packageName, R.layout.widget_event_line)
                    val dateStr = "${num(lang, d.day)} ${gregMonth(lang, d.month - 1)}"
                    line.applyText(
                        R.id.widget_event_text, "• $dateStr — ${d.item.title}", 10f, s, W_SAND
                    )
                    views.addView(R.id.widget_m_events, line)
                }
            }

            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
