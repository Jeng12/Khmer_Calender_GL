package com.example.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Beautiful Khmer Heritage Color Palette
val NightBlack = Color(0xFF0D0A0F)      // #0D0A0F
val DeepAmethyst = Color(0xFF140F1A)    // #120E16 in Jetpack
val PlumSurface = Color(0xFF1D1726)     // #1A1520 in Jetpack
val PlumCard = Color(0xFF261E30)        // #221C2A in Jetpack
val DeepBorder = Color(0xFF322640)      // #2E2538 in Jetpack
val DeepMuted = Color(0xFF453556)       // #3D3349 in Jetpack
val SandText = Color(0xFFF5EDD8)        // #F5EDD8 (Cream)
val GoldSubText = Color(0xFFC7B38E)     // #9B8E7A
val DimColor = Color(0xFFA090B8)        // improved contrast on dark backgrounds
val OnAccent = Color(0xFF1A1108)        // readable on gold, green, pink, and sky controls
val TraditionalGold = Color(0xFFC8973A) // #C8973A
val LightGold = Color(0xFFE8B84B)       // #E8B84B
val CrimsonHoliday = Color(0xFFC0392B)  // #C0392B
val LotusPink = Color(0xFFE8768A)       // #E8768A
val JadeGreen = Color(0xFF4DAF7C)       // #4DAF7C
val MoonWheat = Color(0xFFF2E8C6)       // #F2E8C6
val SkyBlue = Color(0xFF7BA7BC)         // #7BA7BC

// Hoisted gradient brushes – allocated once, not on every recomposition
val GoldBorderGradient = Brush.linearGradient(listOf(TraditionalGold, Color(0xFFFFF0C0), TraditionalGold))
val GoldLotusBrush     = Brush.linearGradient(listOf(TraditionalGold, LotusPink))
val AccentBarBrush     = Brush.horizontalGradient(listOf(CrimsonHoliday, TraditionalGold, LotusPink))

// ── Themeable color scheme (positional destructuring maps to legacy names) ──────
// val (NightBlack, DeepAmethyst, PlumSurface, PlumCard, DeepBorder, DeepMuted,
//      SandText, GoldSubText, DimColor) = LocalAppColors.current
data class AppColors(
    val bg:      Color,  // NightBlack
    val deepBg:  Color,  // DeepAmethyst
    val surface: Color,  // PlumSurface
    val card:    Color,  // PlumCard
    val border:  Color,  // DeepBorder
    val muted:   Color,  // DeepMuted
    val text:    Color,  // SandText
    val subText: Color,  // GoldSubText
    val dim:     Color,  // DimColor
    val accentText: Color,
    val onAccent: Color,
)

val DarkAppColors = AppColors(
    bg = NightBlack, deepBg = DeepAmethyst, surface = PlumSurface, card = PlumCard,
    border = DeepBorder, muted = DeepMuted, text = SandText, subText = GoldSubText, dim = DimColor,
    accentText = TraditionalGold, onAccent = OnAccent
)

val LightAppColors = AppColors(
    bg      = Color(0xFFFAF7F1),
    deepBg  = Color(0xFFF1E6D8),
    surface = Color(0xFFFFFFFF),
    card    = Color(0xFFF4EADF),
    border  = Color(0xFFC7B49E),
    muted   = Color(0xFF9A8267),
    text    = Color(0xFF1A1108),
    subText = Color(0xFF4C3821),
    dim     = Color(0xFF6B5436),
    accentText = Color(0xFF7A4F09),
    onAccent = OnAccent,
)

val LocalAppColors = compositionLocalOf { DarkAppColors }
