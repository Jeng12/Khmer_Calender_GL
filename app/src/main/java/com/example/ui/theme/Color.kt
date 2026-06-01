package com.example.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────────
//  Khmer Heritage Palette — single source of truth for the whole app.
//  These constants were previously duplicated in MainActivity.kt; they now live
//  here and are imported via `com.example.ui.theme.*`.
// ─────────────────────────────────────────────────────────────────────────────
val NightBlack      = Color(0xFF0D0A0F) // app background (dark)
val DeepAmethyst    = Color(0xFF140F1A) // deep background accent
val PlumSurface     = Color(0xFF1D1726) // raised surface
val PlumCard        = Color(0xFF261E30) // card surface
val DeepBorder      = Color(0xFF322640) // hairline borders
val DeepMuted       = Color(0xFF453556) // muted dividers
val SandText        = Color(0xFFF5EDD8) // primary text on dark
val GoldSubText     = Color(0xFFC7B38E) // secondary text on dark
val DimColor        = Color(0xFFA090B8) // tertiary text (WCAG-tuned for dark bg)
val TraditionalGold = Color(0xFFC8973A) // primary accent
val LightGold       = Color(0xFFE8B84B) // bright accent
val CrimsonHoliday  = Color(0xFFC0392B) // holidays / errors
val LotusPink       = Color(0xFFE8768A) // secondary accent
val JadeGreen       = Color(0xFF4DAF7C) // auspicious / success
val MoonWheat       = Color(0xFFF2E8C6) // headline text
val SkyBlue         = Color(0xFF7BA7BC) // converter / notes accent

// ── Hoisted gradient brushes — allocated once, not per recomposition ─────────
val GoldBorderGradient = Brush.linearGradient(listOf(TraditionalGold, Color(0xFFFFF0C0), TraditionalGold))
val GoldLotusBrush     = Brush.linearGradient(listOf(TraditionalGold, LotusPink))
val AccentBarBrush     = Brush.horizontalGradient(listOf(CrimsonHoliday, TraditionalGold, LotusPink))

// ─────────────────────────────────────────────────────────────────────────────
//  Task 1.4 — Semi-transparent color variants.
//  Centralised here so opacity tweaks live in one place instead of being
//  re-derived inline (e.g. `TraditionalGold.copy(alpha = 0.3f)`) all over the UI.
// ─────────────────────────────────────────────────────────────────────────────
val GoldDivider    = TraditionalGold.copy(alpha = 0.30f)
val GoldFaint      = TraditionalGold.copy(alpha = 0.15f)
val GoldGlow       = TraditionalGold.copy(alpha = 0.40f)
val LotusFaint     = LotusPink.copy(alpha = 0.12f)
val LotusGlow      = LotusPink.copy(alpha = 0.40f)
val JadeFaint      = JadeGreen.copy(alpha = 0.12f)
val JadeGlow       = JadeGreen.copy(alpha = 0.40f)
val SkyFaint       = SkyBlue.copy(alpha = 0.15f)
val SkyGlow        = SkyBlue.copy(alpha = 0.40f)
val CrimsonFaint   = CrimsonHoliday.copy(alpha = 0.10f)
val CrimsonGlow    = CrimsonHoliday.copy(alpha = 0.40f)
val WhiteSubtle    = Color.White.copy(alpha = 0.70f)

// ─────────────────────────────────────────────────────────────────────────────
//  Task 1.5 — Traditional Khmer "seven colors of the week" (ពណ៌ប្រចាំថ្ងៃ).
//  Cambodian astrological tradition assigns a color to each weekday. We use
//  them as day-of-week accents for calendar indicators. Index 0 = Sunday.
//    អាទិត្យ (Sun)  – Red          ច័ន្ទ (Mon)   – Cream/Orange
//    អង្គារ (Tue)   – Purple        ពុធ (Wed)     – Green
//    ព្រហស្បតិ៍ (Thu) – Grey-Green   សុក្រ (Fri)   – Blue
//    សៅរ៍ (Sat)     – Deep Violet
// ─────────────────────────────────────────────────────────────────────────────
val DaySunday    = Color(0xFFE0524A) // ក្រហម      – Red
val DayMonday    = Color(0xFFF2C879) // ទឹកក្រូច    – Cream/Orange
val DayTuesday   = Color(0xFFB57EDC) // ស្វាយ      – Purple
val DayWednesday = Color(0xFF6FCF97) // បៃតង       – Green
val DayThursday  = Color(0xFF93B0A5) // បៃតងប្រផេះ – Grey-green
val DayFriday    = Color(0xFF6FA8DC) // ខៀវ        – Blue
val DaySaturday  = Color(0xFF8E7CC3) // ស្វាយចាស់  – Deep violet

// Indexed Sunday→Saturday for easy lookup by Calendar.DAY_OF_WEEK - 1.
val KhmerWeekdayColors = listOf(
    DaySunday, DayMonday, DayTuesday, DayWednesday, DayThursday, DayFriday, DaySaturday
)

/** Returns the traditional Khmer color for a weekday index (0 = Sunday). */
fun khmerWeekdayColor(dayOfWeekIndex: Int): Color =
    KhmerWeekdayColors[((dayOfWeekIndex % 7) + 7) % 7]

// ─────────────────────────────────────────────────────────────────────────────
//  Themeable color bundle. Positional destructuring in composables maps these
//  back to the legacy constant names:
//    val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder,
//         DeepMuted, SandText, GoldSubText, DimColor) = LocalAppColors.current
// ─────────────────────────────────────────────────────────────────────────────
data class AppColors(
    val bg:      Color, // NightBlack
    val deepBg:  Color, // DeepAmethyst
    val surface: Color, // PlumSurface
    val card:    Color, // PlumCard
    val border:  Color, // DeepBorder
    val muted:   Color, // DeepMuted
    val text:    Color, // SandText
    val subText: Color, // GoldSubText
    val dim:     Color, // DimColor
)

val DarkAppColors = AppColors(
    bg = NightBlack, deepBg = DeepAmethyst, surface = PlumSurface, card = PlumCard,
    border = DeepBorder, muted = DeepMuted, text = SandText, subText = GoldSubText, dim = DimColor
)

val LightAppColors = AppColors(
    bg      = Color(0xFFFAF5EE),
    deepBg  = Color(0xFFF0E8DC),
    surface = Color(0xFFFFFFFF),
    card    = Color(0xFFF5EFE6),
    border  = Color(0xFFE2D5C3),
    muted   = Color(0xFFCFC0A8),
    text    = Color(0xFF2C1F0E),
    subText = Color(0xFF6B5028), // darkened from 0xFF7A5F3A for WCAG AA on light bg
    dim     = Color(0xFF7A6450), // darkened from 0xFF9A8068 for WCAG AA on light bg
)

val LocalAppColors = compositionLocalOf { DarkAppColors }
