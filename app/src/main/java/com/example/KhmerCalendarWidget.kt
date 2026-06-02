package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Home-screen month-calendar widget styled after the in-app calendar:
 * a header (Gregorian month/year + lunar month·zodiac), a 7-column grid
 * showing each day's Gregorian numeral with its lunar day beneath, today
 * highlighted, weekend/holiday colouring, and a footer summary for today.
 *
 * Transparency and accent colour are controlled from the app (Profile →
 * Widget) via [DisplaySettings]; tapping the widget opens the app. It
 * refreshes on the periodic update and whenever the date/time changes.
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
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_REFRESH -> refreshAll(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.example.action.WIDGET_REFRESH"

        // Palette (matches the app theme).
        private val SAND = 0xFFF5EDD8.toInt()
        private val GOLD_SUB = 0xFFC7B38E.toInt()
        private val DIM = 0xFFA090B8.toInt()
        private val CRIMSON = 0xFFC0392B.toInt()
        private val LOTUS = 0xFFE8768A.toInt()
        private val SKY = 0xFF7BA7BC.toInt()

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, KhmerCalendarWidget::class.java))
            for (id in ids) updateWidget(context, mgr, id)
        }

        @Suppress("DEPRECATION") // RemoteViews.addView is fine for static (non-scrolling) grids
        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val settings = DisplaySettings.load(context)
            val accent = settings.widgetAccent.colorInt()
            val lang = if (context.getSharedPreferences("khmer_calendar_prefs", Context.MODE_PRIVATE)
                    .getString("app_lang", "km") == "en"
            ) AppLanguage.EN else AppLanguage.KM

            val now = Calendar.getInstance()
            val year = now.get(Calendar.YEAR)
            val month = now.get(Calendar.MONTH) + 1
            val today = now.get(Calendar.DAY_OF_MONTH)
            val todayDate = KhmerCalendarHelper.getKhmerDate(year, month, today)
            val days = KhmerCalendarHelper.getGregorianMonthDays(year, month)

            val views = RemoteViews(context.packageName, R.layout.widget_khmer_calendar)

            // Transparency (0.2–1.0 → 51–255 alpha).
            views.setInt(R.id.widget_bg, "setImageAlpha", (settings.widgetOpacity * 255f).roundToInt().coerceIn(0, 255))

            // Header.
            views.setTextViewText(R.id.widget_title, "${gregMonth(lang, month - 1)} ${num(lang, year)}")
            views.setTextColor(R.id.widget_title, accent)
            views.setTextViewText(
                R.id.widget_subtitle,
                "${lunarMonth(lang, todayDate.lunarMonthName)} · ${zodiac(lang, todayDate.zodiac)}"
            )
            views.setTextViewText(R.id.widget_moon, todayDate.moonEmoji)
            views.setTextViewText(
                R.id.widget_moon_label,
                if (lang == AppLanguage.EN) "Day ${todayDate.lunarDayVal}" else "ថ្ងៃ${num(lang, todayDate.lunarDayVal)}"
            )
            views.setTextColor(R.id.widget_moon_label, accent)

            // Weekday header.
            views.removeAllViews(R.id.widget_weekdays)
            val labels = weekdayLabels(lang)
            labels.forEachIndexed { idx, label ->
                val cell = RemoteViews(context.packageName, R.layout.widget_weekday_cell)
                cell.setTextViewText(R.id.widget_wd_text, label)
                cell.setTextColor(
                    R.id.widget_wd_text,
                    when (idx) { 0 -> CRIMSON; 6 -> SKY; else -> GOLD_SUB }
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
                    val cellIdx = row * 7 + col
                    val dayNumber = cellIdx - startOffset + 1
                    if (dayNumber in 1..days.size) {
                        val date = days[dayNumber - 1]
                        val isToday = dayNumber == today
                        val gregColor = when {
                            isToday -> accent
                            date.holiday != null -> LOTUS
                            col == 0 -> CRIMSON
                            col == 6 -> SKY
                            else -> SAND
                        }
                        cell.setTextViewText(R.id.widget_cell_greg, num(lang, dayNumber))
                        cell.setTextColor(R.id.widget_cell_greg, gregColor)
                        cell.setTextViewText(R.id.widget_cell_lunar, num(lang, date.lunarDayVal))
                        cell.setTextColor(R.id.widget_cell_lunar, if (isToday) accent else DIM)
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
            views.setTextViewText(R.id.widget_footer_day, num(lang, today))
            views.setTextColor(R.id.widget_footer_day, accent)
            views.setTextViewText(
                R.id.widget_footer_title,
                if (lang == AppLanguage.EN)
                    "${todayDate.dayOfWeekEn}, ${todayDate.day} ${gregMonth(lang, month - 1)} $year"
                else
                    "${todayDate.dayOfWeek} ${gregMonth(lang, month - 1)} ${num(lang, year)}"
            )
            views.setTextViewText(
                R.id.widget_footer_sub,
                "${lunarDayLabel(lang, todayDate)} ${lunarMonth(lang, todayDate.lunarMonthName)}"
            )
            views.setTextViewText(R.id.widget_footer_moon, todayDate.moonEmoji)

            // Tap → open app.
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
