package com.example

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

/**
 * Home-screen widget showing today's Khmer lunar date.
 *
 * Renders the Gregorian date, lunar day/month, moon phase, Buddhist Era year,
 * zodiac, and any holiday/auspicious note. Tapping the widget opens the app.
 * Refreshes on the system's periodic update, on date/time changes, and on tap.
 */
class KhmerCalendarWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Refresh when the calendar day rolls over or the clock/timezone changes.
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            ACTION_REFRESH -> refreshAll(context)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.example.action.WIDGET_REFRESH"

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, KhmerCalendarWidget::class.java))
            for (id in ids) updateWidget(context, mgr, id)
        }

        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            // Active language (shared with the in-app setting).
            val prefs = context.getSharedPreferences("khmer_calendar_prefs", Context.MODE_PRIVATE)
            val lang = if (prefs.getString("app_lang", "km") == "en") AppLanguage.EN else AppLanguage.KM

            val now = Calendar.getInstance()
            val y = now.get(Calendar.YEAR)
            val m = now.get(Calendar.MONTH) + 1
            val d = now.get(Calendar.DAY_OF_MONTH)
            val date = KhmerCalendarHelper.getKhmerDate(y, m, d)

            val views = RemoteViews(context.packageName, R.layout.widget_khmer_calendar)

            views.setTextViewText(
                R.id.widget_label,
                if (lang == AppLanguage.EN) "Khmer Lunar Calendar" else "ប្រតិទិនចន្ទគតិខ្មែរ"
            )
            views.setTextViewText(R.id.widget_moon, date.moonEmoji)

            val gregText = if (lang == AppLanguage.EN)
                "${date.dayOfWeekEn}, ${date.day} ${gregMonth(lang, m - 1)} $y"
            else
                "ថ្ងៃ${date.dayOfWeek} ទី${num(lang, date.day)} ${gregMonth(lang, m - 1)} ${num(lang, y)}"
            views.setTextViewText(R.id.widget_greg_date, gregText)

            views.setTextViewText(
                R.id.widget_lunar_date,
                "${lunarDayLabel(lang, date)} ${lunarMonth(lang, date.lunarMonthName)}"
            )

            views.setTextViewText(
                R.id.widget_be,
                if (lang == AppLanguage.EN) "BE ${date.BE}" else "ព.ស. ${num(lang, date.BE)}"
            )
            views.setTextViewText(R.id.widget_zodiac, zodiac(lang, date.zodiac))

            // Optional holiday / auspicious note line.
            val note = when {
                date.holiday != null ->
                    "🎉 " + localizeDual(lang, date.holiday!!)
                date.isAuspicious ->
                    if (lang == AppLanguage.EN) "🌿 Auspicious day" else "🌿 ថ្ងៃមង្គល"
                else -> null
            }
            if (note != null) {
                views.setTextViewText(R.id.widget_note, note)
                views.setViewVisibility(R.id.widget_note, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_note, View.GONE)
            }

            // Tap anywhere on the widget → open the app.
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
