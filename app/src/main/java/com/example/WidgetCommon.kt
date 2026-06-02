package com.example

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.widget.RemoteViews
import kotlin.math.roundToInt

/* ─────────────────────────────────────────────────────────────
   Shared rendering helpers for the home-screen widgets.

   Both widgets honour the app's personalization settings:
     • transparency  → background image alpha
     • accent colour → titles / today / footer
     • glass effect  → frosted background drawable
     • font size     → every text scaled via setTextViewTextSize
     • font family   → TypefaceSpan (serif / monospace / sans-serif)
     • bold weight   → StyleSpan(BOLD)
   ───────────────────────────────────────────────────────────── */

// Palette (matches the app theme). Not const because of .toInt().
internal val W_SAND = 0xFFF5EDD8.toInt()
internal val W_MOON_WHEAT = 0xFFF2E8C6.toInt()
internal val W_GOLD_SUB = 0xFFC7B38E.toInt()
internal val W_DIM = 0xFFA090B8.toInt()
internal val W_CRIMSON = 0xFFC0392B.toInt()
internal val W_LOTUS = 0xFFE8768A.toInt()
internal val W_SKY = 0xFF7BA7BC.toInt()

/** Generic font-family name for a TypefaceSpan, or null to keep the layout default (Khmer). */
private fun AppFontChoice.spanFamily(): String? = when (this) {
    AppFontChoice.SERIF -> "serif"
    AppFontChoice.MONOSPACE -> "monospace"
    AppFontChoice.SANS -> "sans-serif"
    AppFontChoice.DEFAULT -> null
}

/** Wrap [text] with the chosen typeface/weight so it survives through RemoteViews. */
internal fun styledText(text: String, settings: DisplaySettings): CharSequence {
    val family = settings.fontFamily.spanFamily()
    if (family == null && !settings.boldText) return text
    val sp = SpannableString(text)
    val flags = Spannable.SPAN_INCLUSIVE_INCLUSIVE
    if (family != null) sp.setSpan(TypefaceSpan(family), 0, sp.length, flags)
    if (settings.boldText) sp.setSpan(StyleSpan(Typeface.BOLD), 0, sp.length, flags)
    return sp
}

/** Set text + scaled size (+ optional colour) on a TextView, applying all personalization. */
internal fun RemoteViews.applyText(
    viewId: Int,
    text: String,
    baseSp: Float,
    settings: DisplaySettings,
    color: Int? = null
) {
    setTextViewText(viewId, styledText(text, settings))
    setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, baseSp * settings.fontScale)
    if (color != null) setTextColor(viewId, color)
}

/** Apply transparency + glass-effect background to the widget's background ImageView. */
internal fun RemoteViews.applyBackground(bgViewId: Int, settings: DisplaySettings) {
    setImageViewResource(
        bgViewId,
        if (settings.glassEffect) R.drawable.widget_background_glass else R.drawable.widget_background
    )
    setInt(bgViewId, "setImageAlpha", (settings.widgetOpacity * 255f).roundToInt().coerceIn(0, 255))
}

/** Current UI language, shared with the in-app setting. */
internal fun widgetLang(context: Context): AppLanguage =
    if (context.getSharedPreferences("khmer_calendar_prefs", Context.MODE_PRIVATE)
            .getString("app_lang", "km") == "en"
    ) AppLanguage.EN else AppLanguage.KM

/** Refresh every instance of both widget sizes (call after a settings change). */
fun refreshAllWidgets(context: Context) {
    val mgr = AppWidgetManager.getInstance(context)
    for (cls in listOf(KhmerCalendarWidget::class.java, KhmerCalendarWidgetMedium::class.java)) {
        val ids = mgr.getAppWidgetIds(ComponentName(context, cls))
        if (ids.isEmpty()) continue
        val intent = android.content.Intent(context, cls).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }
}
