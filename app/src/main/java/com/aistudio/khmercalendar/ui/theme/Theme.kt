package com.aistudio.khmercalendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val KhmerDarkColorScheme = darkColorScheme(
    primary         = TraditionalGoldTheme,
    onPrimary       = NightBlackTheme,
    primaryContainer = PlumCardTheme,
    onPrimaryContainer = SandTextTheme,
    secondary       = LotusPinkTheme,
    onSecondary     = NightBlackTheme,
    tertiary        = JadeGreenTheme,
    onTertiary      = NightBlackTheme,
    background      = NightBlackTheme,
    onBackground    = SandTextTheme,
    surface         = PlumSurfaceTheme,
    onSurface       = SandTextTheme,
    surfaceVariant  = PlumCardTheme,
    onSurfaceVariant = GoldSubTextTheme,
    outline         = DeepBorderTheme,
    error           = CrimsonHolidayTheme,
    onError         = SandTextTheme,
)

private val KhmerLightColorScheme = lightColorScheme(
    primary         = TraditionalGoldTheme,
    onPrimary       = OnAccentTheme,
    primaryContainer = LightCardTheme,
    onPrimaryContainer = LightTextTheme,
    secondary       = LotusPinkTheme,
    onSecondary     = OnAccentTheme,
    tertiary        = JadeGreenTheme,
    onTertiary      = OnAccentTheme,
    background      = LightBackgroundTheme,
    onBackground    = LightTextTheme,
    surface         = LightSurfaceTheme,
    onSurface       = LightTextTheme,
    surfaceVariant  = LightCardTheme,
    onSurfaceVariant = LightSubTextTheme,
    outline         = LightBorderTheme,
    error           = CrimsonHolidayTheme,
    onError         = SandTextTheme,
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
