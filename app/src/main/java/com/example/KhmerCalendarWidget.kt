package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Large (4×4) month-calendar widget styled after the in-app calendar:
 * header (Gregorian month/year + lunar month · zodiac + moon phase), a
 * 7-column grid showing each day's Gregorian numeral with its lunar day
 * beneath, today highlighted, weekend/holiday colouring, and a today footer.
 *
 * All personalization (transparency, accent, glass, font size/family/weight)
 * comes from the app via [DisplaySettings]; tapping opens the app.
 */
class KhmerCalendarWidget : AppWidgetProvider() {

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
        @Suppress("DEPRECATION") // RemoteViews.addView is supported for static (non-scrolling) grids
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

            val views = RemoteViews(context.packageName, R.layout.widget_khmer_calendar)
            views.applyBackground(R.id.widget_bg, s)

            // Header.
            views.applyText(R.id.widget_title, "${gregMonth(lang, month - 1)} ${num(lang, year)}", 20f, s, accent)
            views.applyText(
                R.id.widget_subtitle,
                "${lunarMonth(lang, todayDate.lunarMonthName)} · ${zodiac(lang, todayDate.zodiac)}",
                11f, s, W_GOLD_SUB
            )
            views.applyText(R.id.widget_moon, todayDate.moonEmoji, 30f, s)
            views.applyText(
                R.id.widget_moon_label,
                if (lang == AppLanguage.EN) "Day ${todayDate.lunarDayVal}" else "ថ្ងៃ${num(lang, todayDate.lunarDayVal)}",
                9f, s, accent
            )

            // Weekday header.
            views.removeAllViews(R.id.widget_weekdays)
            weekdayLabels(lang).forEachIndexed { idx, label ->
                val cell = RemoteViews(context.packageName, R.layout.widget_weekday_cell)
                cell.applyText(
                    R.id.widget_wd_text, label, 11f, s,
                    when (idx) { 0 -> W_CRIMSON; 6 -> W_SKY; else -> W_GOLD_SUB }
                )
                views.addView(R.id.widget_weekdays, cell)
            }

            // Month grid.
            views.removeAllViews(R.id.widget_grid)
            val startSerial = KhmerCalendarHelper.getSerialDay(year, month, 1)
            val startOffset = ((startSerial + 2) % 7 + 7) % 7
            val rows = (startOffset + days.size + 6) / 7
            for (row in 0 until rows) {
                val rowViews = RemoteViews(context.packageName, R.layout.widget_grid_row)
                for (col in 0..6) {
                    val cell = RemoteViews(context.packageName, R.layout.widget_grid_cell)
                    val dayNumber = row * 7 + col - startOffset + 1
                    if (dayNumber in 1..days.size) {
                        val date = days[dayNumber - 1]
                        val isToday = dayNumber == today
                        val gregColor = when {
                            isToday -> accent
                            date.holiday != null -> W_LOTUS
                            col == 0 -> W_CRIMSON
                            col == 6 -> W_SKY
                            else -> W_SAND
                        }
                        cell.applyText(R.id.widget_cell_greg, num(lang, dayNumber), 16f, s, gregColor)
                        cell.applyText(R.id.widget_cell_lunar, num(lang, date.lunarDayVal), 9f, s, if (isToday) accent else W_DIM)
                        if (isToday) {
                            cell.setInt(R.id.widget_cell_box, "setBackgroundResource", R.drawable.widget_cell_today)
                        }
                    } else {
                        cell.setTextViewText(R.id.widget_cell_greg, "")
                        cell.setTextViewText(R.id.widget_cell_lunar, "")
                    }
                    rowViews.addView(R.id.widget_row, cell)
                }
                views.addView(R.id.widget_grid, rowViews)
            }

            // Footer summary (today).
            views.applyText(R.id.widget_footer_day, num(lang, today), 26f, s, accent)
            views.applyText(
                R.id.widget_footer_title,
                if (lang == AppLanguage.EN)
                    "${todayDate.dayOfWeekEn}, ${todayDate.day} ${gregMonth(lang, month - 1)} $year"
                else
                    "${todayDate.dayOfWeek} ${gregMonth(lang, month - 1)} ${num(lang, year)}",
                12f, s, W_SAND
            )
            views.applyText(
                R.id.widget_footer_sub,
                "${lunarDayLabel(lang, todayDate)} ${lunarMonth(lang, todayDate.lunarMonthName)}",
                10f, s, W_GOLD_SUB
            )
            views.applyText(R.id.widget_footer_moon, todayDate.moonEmoji, 22f, s)

            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}

/** Shared PendingIntent that launches the app when a widget is tapped. */
internal fun openAppIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        context, 0, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
