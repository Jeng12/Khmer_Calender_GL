package com.aistudio.khmercalendar.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
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

    /**
     * Glance keeps the widget composition alive in a session and only
     * recomposes when its own state changes — plain SharedPreferences reads
     * are invisible to it, so update() alone re-sends the stale UI. Bumping a
     * nonce through the widget state forces a real recomposition, which then
     * re-reads the prefs above. Call after every state change.
     */
    val NONCE_KEY = longPreferencesKey("timer_ui_nonce")

    suspend fun refreshWidgets(ctx: Context) {
        val widget = KhmerTimerWidget()
        GlanceAppWidgetManager(ctx).getGlanceIds(KhmerTimerWidget::class.java).forEach { id ->
            updateAppWidgetState(ctx, id) { prefs ->
                prefs[NONCE_KEY] = System.nanoTime()
            }
        }
        widget.updateAll(ctx)
    }
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
        TimerWidgetState.refreshWidgets(context)
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
        TimerWidgetState.refreshWidgets(context)
    }
}

/** Stop: cancel the alarm and go back to the picker. */
class TimerStopAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        cancelReminder(context, TimerWidgetState.TIMER_REQUEST_CODE)
        TimerWidgetState.clearRunning(context)
        TimerWidgetState.refreshWidgets(context)
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
            // Reading the nonce ties this composition to the widget state, so
            // refreshWidgets() forces a real recomposition (fresh prefs reads).
            @Suppress("UNUSED_VARIABLE")
            val uiNonce = currentState<Preferences>()[TimerWidgetState.NONCE_KEY]
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

/**
 * Idle face, vivo-timer style: a scrollable minute wheel on the left (native
 * ListView fling physics), and a fixed big selected value with a dot + red
 * pointer line at the center. Tapping a wheel value only SELECTS it — the
 * timer starts exclusively from the play button, so scroll gestures can't
 * fire it. The wheel rows are deliberately uniform: launchers cache widget
 * collection items and won't rebind them on update, so the selection
 * highlight lives OUTSIDE the list where updates always repaint.
 */
@Composable
private fun TimerPickerFace(context: Context, lang: AppLanguage, style: WidgetStyle) {
    val selected = TimerWidgetState.selectedMinutes(context)

    Box(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            LazyColumn(modifier = GlanceModifier.width(52.dp).fillMaxHeight()) {
                items((0..TimerWidgetState.MAX_MINUTES).toList()) { value ->
                    WheelRow(lang, style, value)
                }
            }
            Spacer(GlanceModifier.width(10.dp))
            // Fixed selection readout: big value · dot · red pointer line.
            Row(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    numStr(lang, selected.toString()),
                    style = TextStyle(color = cp(style.text), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(GlanceModifier.width(10.dp))
                Spacer(GlanceModifier.size(7.dp).background(ImageProvider(R.drawable.widget_timer_dot)))
                Spacer(GlanceModifier.width(6.dp))
                Spacer(
                    GlanceModifier
                        .defaultWeight()
                        .height(5.dp)
                        .background(ImageProvider(R.drawable.widget_timer_accent))
                )
            }
        }
        // Play — bottom-right, translucent round button like the reference.
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            RoundButton(
                bgRes = R.drawable.widget_timer_button_bg,
                label = "▶",
                action = actionRunCallback<TimerStartAction>()
            )
        }
    }
}

/** One scrollable wheel value; tapping it selects it — never starts the timer. */
@Composable
private fun WheelRow(lang: AppLanguage, style: WidgetStyle, value: Int) {
    Text(
        numStr(lang, value.toString()),
        style = TextStyle(color = cp(style.sub), fontSize = 16.sp, fontWeight = FontWeight.Medium),
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
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
            .size(46.dp)
            .background(ImageProvider(bgRes))
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(color = cp(Color.White), fontSize = 17.sp, fontWeight = FontWeight.Bold))
    }
}

/** Manifest-registered receiver that hosts [KhmerTimerWidget]. */
class KhmerTimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KhmerTimerWidget()
}
