package com.aistudio.khmercalendar.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.aistudio.khmercalendar.R
import com.aistudio.khmercalendar.alarm.cancelReminder
import com.aistudio.khmercalendar.alarm.scheduleReminder
import com.aistudio.khmercalendar.core.AppLanguage
import com.aistudio.khmercalendar.core.numStr
import com.aistudio.khmercalendar.core.tr
import java.util.Calendar

/**
 * Countdown timer state shared between the widget UI, its tap actions and
 * [com.aistudio.khmercalendar.alarm.AlarmReceiver] (which clears it when the
 * timer fires). Idle when [endMs] == 0.
 */
object TimerWidgetState {
    private const val FILE = "khmer_calendar_timer"
    private const val KEY_MINUTES = "timer_minutes"
    private const val KEY_END_MS = "timer_end_ms"

    /** Fixed request code — there is only ever one widget timer at a time. */
    const val TIMER_REQUEST_CODE = 910_001

    /** Minutes wrap on a 0..60 wheel, matching the picker UI. */
    const val MAX_MINUTES = 60

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun selectedMinutes(ctx: Context): Int =
        prefs(ctx).getInt(KEY_MINUTES, 10).coerceIn(0, MAX_MINUTES)

    fun setSelectedMinutes(ctx: Context, minutes: Int) {
        prefs(ctx).edit().putInt(KEY_MINUTES, minutes.coerceIn(0, MAX_MINUTES)).apply()
    }

    /** End timestamp of the running timer (ms since epoch), or 0 when idle. */
    fun endMs(ctx: Context): Long = prefs(ctx).getLong(KEY_END_MS, 0L)

    fun setEndMs(ctx: Context, endMs: Long) {
        prefs(ctx).edit().putLong(KEY_END_MS, endMs).apply()
    }

    fun clearRunning(ctx: Context) = setEndMs(ctx, 0L)
}

/** Wrap any value onto the 0..60 minute wheel (…59, 60, 0, 1…). */
private fun wrapMinute(v: Int): Int {
    val span = TimerWidgetState.MAX_MINUTES + 1
    return ((v % span) + span) % span
}

private val MINUTES_PARAM = ActionParameters.Key<Int>("timer_minutes")

/** Tap on a wheel row: select that minute value. */
class TimerSelectMinutesAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        parameters[MINUTES_PARAM]?.let { TimerWidgetState.setSelectedMinutes(context, wrapMinute(it)) }
        KhmerTimerWidget().updateAll(context)
    }
}

/** Play: arm the countdown alarm and switch the widget to its running face. */
class TimerStartAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val minutes = TimerWidgetState.selectedMinutes(context)
        if (minutes > 0 && TimerWidgetState.endMs(context) <= System.currentTimeMillis()) {
            val lang = WidgetPrefs.resolveLang(context)
            val endMs = System.currentTimeMillis() + minutes * 60_000L
            TimerWidgetState.setEndMs(context, endMs)
            scheduleReminder(
                context = context,
                requestCode = TimerWidgetState.TIMER_REQUEST_CODE,
                triggerMs = endMs,
                title = tr(lang, "ម៉ោងកំណត់បានផុត!", "Timer finished!"),
                message = tr(
                    lang,
                    "កម្មវិធីកំណត់ម៉ោង ${numStr(lang, minutes.toString())} នាទី បានបញ្ចប់",
                    "Your $minutes minute timer is done"
                ),
                kind = "timer"
            )
        }
        KhmerTimerWidget().updateAll(context)
    }
}

/** Stop: cancel the alarm and go back to the picker. */
class TimerStopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        cancelReminder(context, TimerWidgetState.TIMER_REQUEST_CODE)
        TimerWidgetState.clearRunning(context)
        KhmerTimerWidget().updateAll(context)
    }
}

/**
 * Home-screen countdown timer widget. Idle it shows a wheel-style minute
 * picker (tap above/below the highlighted value to scroll) with a play button;
 * running it shows a live Chronometer countdown with the finish time and a
 * stop button. Finishing rings through the app's normal alarm notification.
 */
class KhmerTimerWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val ctx = LocalContext.current
            val lang = WidgetPrefs.resolveLang(ctx)
            val style = styleFor(ctx)
            val endMs = TimerWidgetState.endMs(ctx)
            val running = endMs > System.currentTimeMillis()
            GlassRoot(ctx, style.bgRes) {
                if (running) TimerRunningFace(ctx, lang, style, endMs)
                else TimerPickerFace(ctx, lang, style)
            }
        }
    }
}

/** Idle face: 5-row minute wheel + play button (mimics a scroll wheel). */
@Composable
private fun TimerPickerFace(context: Context, lang: AppLanguage, style: WidgetStyle) {
    val selected = TimerWidgetState.selectedMinutes(context)

    Box(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            Text(
                tr(lang, "កំណត់ម៉ោង · នាទី", "TIMER · MIN"),
                style = TextStyle(color = cp(style.dim), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            )
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WheelRow(lang, style, wrapMinute(selected - 2), 13.sp, style.dim)
                WheelRow(lang, style, wrapMinute(selected - 1), 19.sp, style.sub)
                // Selected value + the red "current position" accent bar.
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .clickable(actionRunCallback<TimerStartAction>()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        numStr(lang, selected.toString()),
                        style = TextStyle(color = cp(style.text), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    Spacer(
                        GlanceModifier
                            .width(46.dp)
                            .height(5.dp)
                            .background(ImageProvider(R.drawable.widget_timer_accent))
                    )
                }
                WheelRow(lang, style, wrapMinute(selected + 1), 19.sp, style.sub)
                WheelRow(lang, style, wrapMinute(selected + 2), 13.sp, style.dim)
            }
        }
        // Play — bottom-right, like the reference design.
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            RoundButton(
                bgRes = R.drawable.widget_timer_play_bg,
                label = "▶",
                action = actionRunCallback<TimerStartAction>()
            )
        }
    }
}

/** One dimmed wheel value; tapping it "scrolls" the wheel to that value. */
@Composable
private fun WheelRow(
    lang: AppLanguage,
    style: WidgetStyle,
    value: Int,
    size: androidx.compose.ui.unit.TextUnit,
    color: Color
) {
    Text(
        numStr(lang, value.toString()),
        style = TextStyle(color = cp(color), fontSize = size, fontWeight = FontWeight.Medium),
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clickable(actionRunCallback<TimerSelectMinutesAction>(actionParametersOf(MINUTES_PARAM to value)))
    )
}

/** Running face: live Chronometer countdown, finish time, stop button. */
@Composable
private fun TimerRunningFace(context: Context, lang: AppLanguage, style: WidgetStyle, endMs: Long) {
    val endCal = Calendar.getInstance().apply { timeInMillis = endMs }
    val endLabel = numStr(
        lang,
        "%02d:%02d".format(endCal.get(Calendar.HOUR_OF_DAY), endCal.get(Calendar.MINUTE))
    )

    Box(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                tr(lang, "កំពុងរាប់ថយក្រោយ", "COUNTING DOWN"),
                style = TextStyle(color = cp(GOLD), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.height(4.dp))
            // A real Chronometer ticks every second without waking the app.
            AndroidRemoteViews(
                remoteViews = RemoteViews(context.packageName, R.layout.widget_timer_chronometer).apply {
                    setChronometerCountDown(R.id.timer_widget_chronometer, true)
                    setChronometer(
                        R.id.timer_widget_chronometer,
                        SystemClock.elapsedRealtime() + (endMs - System.currentTimeMillis()),
                        null,
                        true
                    )
                    setTextColor(
                        R.id.timer_widget_chronometer,
                        android.graphics.Color.argb(
                            (style.text.alpha * 255).toInt(),
                            (style.text.red * 255).toInt(),
                            (style.text.green * 255).toInt(),
                            (style.text.blue * 255).toInt()
                        )
                    )
                },
                modifier = GlanceModifier.fillMaxWidth().height(44.dp)
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                tr(lang, "បញ្ចប់នៅម៉ោង $endLabel", "Ends at $endLabel"),
                style = TextStyle(color = cp(style.sub), fontSize = 10.sp)
            )
        }
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            RoundButton(
                bgRes = R.drawable.widget_timer_stop_bg,
                label = "■",
                action = actionRunCallback<TimerStopAction>()
            )
        }
    }
}

@Composable
private fun RoundButton(bgRes: Int, label: String, action: androidx.glance.action.Action) {
    Box(
        modifier = GlanceModifier
            .size(42.dp)
            .background(ImageProvider(bgRes))
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(color = cp(Color.White), fontSize = 16.sp, fontWeight = FontWeight.Bold))
    }
}

/** Manifest-registered receiver that hosts [KhmerTimerWidget]. */
class KhmerTimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KhmerTimerWidget()
}
