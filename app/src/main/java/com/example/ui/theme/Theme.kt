package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

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

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = KhmerDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
