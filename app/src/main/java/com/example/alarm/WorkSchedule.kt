package com.example.alarm

import android.content.Context
import com.example.core.AppLanguage
import com.example.data.AppStore
import com.example.data.WorkCycleEngine
import java.util.Calendar

/**
 * Materialises the user's rotating [AppStore.ShiftCycle] into concrete one-shot
 * reminders over a rolling [HORIZON_DAYS] window. Blocked (no-rest) days are
 * skipped. [sync] runs on app launch and after reboot, keeping the window
 * topped up.
 */
object WorkScheduleScheduler {

    private const val HORIZON_DAYS = 14

    private fun appLanguage(context: Context): AppLanguage {
        val p = context.getSharedPreferences(AppStore.SETTINGS_FILE, Context.MODE_PRIVATE)
        return if (p.getString("app_lang", "km") == "en") AppLanguage.EN else AppLanguage.KM
    }

    fun sync(context: Context) {
        // Clear out any previously-scheduled shift reminders first.
        AppStore.getReminders(context)
            .filter { it.kind == "shift" }
            .forEach { cancelReminder(context, it.requestCode) }

        val cycle = AppStore.getShiftCycle(context) ?: return
        if (!cycle.isConfigured || !cycle.remind) return

        val lang = appLanguage(context)
        val now = System.currentTimeMillis()

        // Start two days early so the no-rest rule has correct edge context.
        val from = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -2) }
        val to = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, HORIZON_DAYS) }

        WorkCycleEngine.buildWorkDays(cycle, from, to).forEach { wd ->
            if (wd.blocked) return@forEach
            val triggerMs = wd.startMs - cycle.reminderMinutesBefore * 60_000L
            if (triggerMs <= now) return@forEach

            val timeStr = "%02d:%02d".format(wd.shift.startHour, wd.shift.startMin)
            val title = if (lang == AppLanguage.EN) "Work · ${wd.shift.name}" else "ការងារ · ${wd.shift.name}"
            val message = if (lang == AppLanguage.EN)
                "${wd.shift.name} shift starts at $timeStr"
            else
                "វេន${wd.shift.name} ចាប់ផ្តើមនៅម៉ោង $timeStr"

            scheduleReminder(
                context = context,
                requestCode = AppStore.nextRequestCode(context),
                triggerMs = triggerMs,
                title = title,
                message = message,
                kind = "shift",
                shiftId = wd.shift.id
            )
        }
    }
}
