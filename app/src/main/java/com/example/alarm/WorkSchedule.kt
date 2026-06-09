package com.example.alarm

import android.content.Context
import com.example.core.AppLanguage
import com.example.data.AppStore
import java.util.Calendar

/**
 * Turns the user's recurring weekly [AppStore.WorkShift]s into concrete one-shot
 * reminders. Rather than relying on repeating alarms, we materialise every
 * shift occurrence within a rolling [HORIZON_DAYS] window. [sync] is called on
 * app launch and after a reboot, which keeps the window topped up.
 */
object WorkScheduleScheduler {

    private const val HORIZON_DAYS = 14

    private fun appLanguage(context: Context): AppLanguage {
        val p = context.getSharedPreferences(AppStore.SETTINGS_FILE, Context.MODE_PRIVATE)
        return if (p.getString("app_lang", "km") == "en") AppLanguage.EN else AppLanguage.KM
    }

    /** Cancel previously-scheduled shift reminders and re-materialise the window. */
    fun sync(context: Context) {
        // 1. Clear out any existing shift-kind reminders.
        AppStore.getReminders(context)
            .filter { it.kind == "shift" }
            .forEach { cancelReminder(context, it.requestCode) }

        val shifts = AppStore.getShifts(context).filter { it.remind && it.daysOfWeek.isNotEmpty() }
        if (shifts.isEmpty()) return

        val lang = appLanguage(context)
        val now = System.currentTimeMillis()

        for (offset in 0..HORIZON_DAYS) {
            val day = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            val dow = day.get(Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat

            shifts.filter { dow in it.daysOfWeek }.forEach { shift ->
                val triggerCal = Calendar.getInstance().apply {
                    timeInMillis = day.timeInMillis
                    set(Calendar.HOUR_OF_DAY, shift.startHour)
                    set(Calendar.MINUTE, shift.startMin)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.MINUTE, -shift.reminderMinutesBefore)
                }
                if (triggerCal.timeInMillis <= now) return@forEach

                val timeStr = "%02d:%02d".format(shift.startHour, shift.startMin)
                val title = if (lang == AppLanguage.EN) "Work · ${shift.label}" else "ការងារ · ${shift.label}"
                val message = if (lang == AppLanguage.EN)
                    "Shift starts at $timeStr"
                else
                    "វេនចាប់ផ្តើមនៅម៉ោង $timeStr"

                scheduleReminder(
                    context = context,
                    requestCode = AppStore.nextRequestCode(context),
                    triggerMs = triggerCal.timeInMillis,
                    title = title,
                    message = message,
                    kind = "shift",
                    shiftId = shift.id
                )
            }
        }
    }
}
