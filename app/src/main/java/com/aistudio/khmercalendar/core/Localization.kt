package com.aistudio.khmercalendar.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.aistudio.khmercalendar.calendar.KhmerCalendarHelper
import com.aistudio.khmercalendar.calendar.KhmerDate

/**
 * Lightweight in-app localization layer.
 *
 * The app keeps its Khmer text inline but every user-facing string is funneled
 * through [tr]/[num] (composable) or their language-parameter variants so the
 * whole UI can switch between Khmer and English live, without recreating the
 * Activity or relying on Android resource qualifiers.
 */
enum class AppLanguage { KM, EN }

/** Current UI language. Defaults to Khmer so existing screenshots stay Khmer. */
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.KM }

/** Pick a Khmer/English string based on the active language. */
@Composable
fun tr(km: String, en: String): String =
    if (LocalAppLanguage.current == AppLanguage.EN) en else km

/** Non-composable variant for use inside remember{} blocks and data builders. */
fun tr(lang: AppLanguage, km: String, en: String): String =
    if (lang == AppLanguage.EN) en else km

/** Localize an integer: Khmer numerals for KM, Arabic for EN. */
@Composable
fun num(n: Int): String = num(LocalAppLanguage.current, n)

fun num(lang: AppLanguage, n: Int): String =
    if (lang == AppLanguage.EN) n.toString() else KhmerCalendarHelper.toKhmerNumeral(n)

/** Localize a numeric string (digits only) without touching non-digit chars. */
fun numStr(lang: AppLanguage, s: String): String =
    if (lang == AppLanguage.EN) s else s.map { c -> KHMER_DIGITS[c] ?: c }.joinToString("")

private val KHMER_DIGITS = mapOf(
    '0' to '០', '1' to '១', '2' to '២', '3' to '៣', '4' to '៤',
    '5' to '៥', '6' to '៦', '7' to '៧', '8' to '៨', '9' to '៩'
)

// ─── Gregorian months ──────────────────────────────────────────────────────
val GREG_MONTHS_KM = listOf(
    "មករា", "កុម្ភៈ", "មីនា", "មេសា", "ឧសភា", "មិថុនា",
    "កក្កដា", "សីហា", "កញ្ញា", "តុលា", "វិច្ឆិកា", "ធ្នូ"
)
val GREG_MONTHS_EN = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

/** idx is 0-based (0 = January). */
fun gregMonth(lang: AppLanguage, idx: Int): String {
    val i = idx.coerceIn(0, 11)
    return if (lang == AppLanguage.EN) GREG_MONTHS_EN[i] else GREG_MONTHS_KM[i]
}

// ─── Weekday short labels (Sunday-first) ────────────────────────────────────
val WEEKDAYS_SHORT_KM = listOf("អា", "ច", "អ", "ព", "ព្រ", "សុ", "ស")
val WEEKDAYS_SHORT_EN = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

fun weekdayLabels(lang: AppLanguage): List<String> =
    if (lang == AppLanguage.EN) WEEKDAYS_SHORT_EN else WEEKDAYS_SHORT_KM

// ─── Lunar month names ──────────────────────────────────────────────────────
private val LUNAR_MONTH_EN = mapOf(
    "ចេត្រ" to "Chetr",
    "ពិសាខ" to "Visakha",
    "ជេស្ឋ" to "Chesth",
    "អាសាឍ" to "Asadh",
    "អាសាឍ ១" to "Asadh I",
    "អាសាឍ ២" to "Asadh II",
    "ស្រាពណ៍" to "Srap",
    "ភទ្របទ" to "Phutrobot",
    "អស្សុជ" to "Assoch",
    "កត្តិក" to "Kakdek",
    "មិគសិរ" to "Migasir",
    "បុស្ស" to "Boss",
    "មាឃ" to "Meak",
    "ផល្គុន" to "Phalkun"
)

fun lunarMonth(lang: AppLanguage, khName: String): String =
    if (lang == AppLanguage.EN) (LUNAR_MONTH_EN[khName] ?: khName) else khName

// ─── Zodiac names ───────────────────────────────────────────────────────────
private val ZODIAC_EN = mapOf(
    "ឆ្នាំជូត" to "Year of the Rat",
    "ឆ្នាំឆ្លូវ" to "Year of the Ox",
    "ឆ្នាំខាល" to "Year of the Tiger",
    "ឆ្នាំថោះ" to "Year of the Rabbit",
    "ឆ្នាំរោង" to "Year of the Dragon",
    "ឆ្នាំម្សាញ់" to "Year of the Snake",
    "ឆ្នាំមមី" to "Year of the Horse",
    "ឆ្នាំមមែ" to "Year of the Goat",
    "ឆ្នាំវក" to "Year of the Monkey",
    "ឆ្នាំរកា" to "Year of the Rooster",
    "ឆ្នាំច" to "Year of the Dog",
    "ឆ្នាំកុរ" to "Year of the Pig"
)

fun zodiac(lang: AppLanguage, khZodiac: String): String =
    if (lang == AppLanguage.EN) (ZODIAC_EN[khZodiac] ?: khZodiac) else khZodiac

/** "១៥ កើត" / "15 Waxing". */
fun lunarDayLabel(lang: AppLanguage, date: KhmerDate): String {
    val phase = if (lang == AppLanguage.EN) {
        if (date.isWaxing) "Waxing" else "Waning"
    } else {
        if (date.isWaxing) "កើត" else "រោច"
    }
    return "${num(lang, date.lunarDayVal)} $phase"
}

/**
 * Many data strings are stored as "Khmer (English)". In EN mode show only the
 * English portion in the parentheses; in KM mode show the original. Strings
 * without a parenthetical fall back to the original text.
 */
fun localizeDual(lang: AppLanguage, s: String): String {
    if (lang == AppLanguage.KM) return s
    val m = Regex("\\(([^)]*)\\)").find(s)
    return m?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } ?: s
}
