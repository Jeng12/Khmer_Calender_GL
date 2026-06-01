package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

// ─────────────────────────────────────────────────────────────────────────────
//  Task 1.2 — Custom colors from Color.kt wired into Material 3 color schemes.
//  Both a dark and a light scheme are provided so MaterialTheme.colorScheme
//  (.background / .surface / .primary …) stays in sync with the app's
//  dark/light toggle.
// ─────────────────────────────────────────────────────────────────────────────
val KhmerDarkColorScheme = darkColorScheme(
    primary            = TraditionalGold,
    onPrimary          = NightBlack,
    primaryContainer   = PlumCard,
    onPrimaryContainer = SandText,
    secondary          = LotusPink,
    onSecondary        = NightBlack,
    tertiary           = JadeGreen,
    onTertiary         = NightBlack,
    background         = NightBlack,
    onBackground       = SandText,
    surface            = PlumSurface,
    onSurface          = SandText,
    surfaceVariant     = PlumCard,
    onSurfaceVariant   = GoldSubText,
    outline            = DeepBorder,
    error              = CrimsonHoliday,
    onError            = SandText,
)

val KhmerLightColorScheme = lightColorScheme(
    primary            = TraditionalGold,
    onPrimary          = NightBlack,
    primaryContainer   = LightAppColors.card,
    onPrimaryContainer = LightAppColors.text,
    secondary          = LotusPink,
    onSecondary        = NightBlack,
    tertiary           = JadeGreen,
    onTertiary         = NightBlack,
    background         = LightAppColors.bg,
    onBackground       = LightAppColors.text,
    surface            = LightAppColors.surface,
    onSurface          = LightAppColors.text,
    surfaceVariant     = LightAppColors.card,
    onSurfaceVariant   = LightAppColors.subText,
    outline            = LightAppColors.border,
    error              = CrimsonHoliday,
    onError            = SandText,
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) KhmerDarkColorScheme else KhmerLightColorScheme,
        typography = Typography,
    ) {
        // Default every Text (even those that only set fontSize/color inline) to the
        // Khmer font family, so the whole app shares one consistent typeface.
        CompositionLocalProvider(
            LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = KhmerFontFamily),
            content = content,
        )
    }
}
