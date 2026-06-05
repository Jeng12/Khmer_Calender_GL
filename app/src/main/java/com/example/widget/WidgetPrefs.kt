package com.example.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.example.core.AppLanguage

/**
 * Shared settings for the home-screen widget, persisted in the same
 * SharedPreferences file the rest of the app uses ("khmer_calendar_prefs").
 *
 * Both the Profile settings UI and the widget's `provideGlance` read through
 * here so they agree on a single source of truth. The "follow" values defer to
 * the app-wide language/theme the user already picked.
 */
object WidgetPrefs {
    private const val FILE = "khmer_calendar_prefs"

    const val KEY_LANG = "widget_lang"    // "follow" | "km" | "en"
    const val KEY_THEME = "widget_theme"  // "follow" | "dark" | "light"

    const val FOLLOW = "follow"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun langSetting(ctx: Context): String = prefs(ctx).getString(KEY_LANG, FOLLOW) ?: FOLLOW
    fun themeSetting(ctx: Context): String = prefs(ctx).getString(KEY_THEME, FOLLOW) ?: FOLLOW

    fun setLang(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_LANG, value).apply()
    }

    fun setTheme(ctx: Context, value: String) {
        prefs(ctx).edit().putString(KEY_THEME, value).apply()
    }

    /** Effective widget language, resolving "follow" against the app language. */
    fun resolveLang(ctx: Context): AppLanguage {
        val p = prefs(ctx)
        return when (p.getString(KEY_LANG, FOLLOW)) {
            "en" -> AppLanguage.EN
            "km" -> AppLanguage.KM
            else -> if (p.getString("app_lang", "km") == "en") AppLanguage.EN else AppLanguage.KM
        }
    }

    /** Effective widget theme (dark = true), resolving "follow" against dark mode. */
    fun resolveDark(ctx: Context): Boolean {
        val p = prefs(ctx)
        return when (p.getString(KEY_THEME, FOLLOW)) {
            "dark" -> true
            "light" -> false
            else -> p.getBoolean("dark_mode", true)
        }
    }

    /** Re-render every placed instance of both widgets (e.g. after a setting/data change). */
    suspend fun refresh(ctx: Context) {
        KhmerCalendarWidget().updateAll(ctx)
        KhmerAgendaWidget().updateAll(ctx)
    }
}
