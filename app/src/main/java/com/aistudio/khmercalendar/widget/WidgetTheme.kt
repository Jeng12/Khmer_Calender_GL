package com.aistudio.khmercalendar.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.unit.ColorProvider
import com.aistudio.khmercalendar.MainActivity
import com.aistudio.khmercalendar.R

// ── Shared glassmorphism palette + theming for all home-screen widgets ──────
internal val GOLD = Color(0xFFC8973A)
internal val GOLD_LIGHT = Color(0xFFE8B84B)
internal val RED = Color(0xFFE53935)
internal val SAT_BLUE = Color(0xFF5599FF)
internal val DARK = Color(0xFF0D0820)

internal fun cp(c: Color) = ColorProvider(c)

/** Theme-dependent surface + text colors (chosen from the widget theme setting). */
internal data class WidgetStyle(
    val bgRes: Int,
    val text: Color,
    val sub: Color,
    val dim: Color,
    val hairline: Color
)

internal val DARK_STYLE = WidgetStyle(
    bgRes = R.drawable.widget_glass_bg,
    text = Color(0xFFF5F0E8),
    sub = Color(0x8CF5F0E8),   // ~55% cream
    dim = Color(0x47F5F0E8),   // ~28% cream
    hairline = Color(0x14FFFFFF)
)

internal val LIGHT_STYLE = WidgetStyle(
    bgRes = R.drawable.widget_glass_bg_light,
    text = Color(0xFF2C1F0E),
    sub = Color(0xCC6B5436),
    dim = Color(0x996B5436),
    hairline = Color(0x1A000000)
)

/** Resolve the active style from the user's widget theme preference. */
internal fun styleFor(context: Context): WidgetStyle =
    if (WidgetPrefs.resolveDark(context)) DARK_STYLE else LIGHT_STYLE

/** Glass card container with a gold top shimmer; tapping opens the app. */
@Composable
internal fun GlassRoot(context: Context, bgRes: Int, content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(bgRes))
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
    ) {
        Spacer(GlanceModifier.fillMaxWidth().height(2.dp).background(ImageProvider(R.drawable.widget_shimmer)))
        content()
    }
}

@Composable
internal fun GlassDivider(color: Color) {
    Spacer(GlanceModifier.fillMaxWidth().height(1.dp).background(cp(color)))
}
