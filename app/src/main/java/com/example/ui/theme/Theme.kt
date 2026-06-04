package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = KhmerDarkColorScheme,
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
