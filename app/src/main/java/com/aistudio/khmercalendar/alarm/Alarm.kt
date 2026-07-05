package com.aistudio.khmercalendar.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.aistudio.khmercalendar.calendar.KhmerCalendarHelper
import com.aistudio.khmercalendar.calendar.KhmerDate
import com.aistudio.khmercalendar.core.AppLanguage
import com.aistudio.khmercalendar.core.GREG_MONTHS_EN
import com.aistudio.khmercalendar.core.GREG_MONTHS_KM
import com.aistudio.khmercalendar.data.AppStore

// ── Alarm / Reminder helpers ─────────────────────────────────────────────────

/**
 * Arm a one-shot alarm with the system [AlarmManager]. This only talks to the
 * OS — persistence is handled separately by [scheduleReminder] / [AppStore] so
 * that [BootReceiver] can re-arm everything after a reboot.
 */
fun armAlarm(
    context: Context,
    requestCode: Int,
    triggerMs: Long,
    title: String,
    message: String,
    ringtoneUri: String?,
    insistent: Boolean,
    kind: String = "reminder"
) {
    val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra("requestCode", requestCode)
        putExtra("title", title)
        putExtra("message", message)
        putExtra("ringtoneUri", ringtoneUri)
        putExtra("insistent", insistent)
        putExtra("kind", kind)
    }
    val pi = PendingIntent.getBroadcast(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    // Exact scheduling can throw SecurityException if the exact-alarm permission is
    // missing/revoked; fall back to an inexact window so the reminder still fires.
    try {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (alarmMgr.canScheduleExactAlarms())
                    alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
                else
                    alarmMgr.setWindow(AlarmManager.RTC_WAKEUP, triggerMs, 5 * 60_000L, pi)
            else -> alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        }
    } catch (_: SecurityException) {
        runCatching { alarmMgr.setWindow(AlarmManager.RTC_WAKEUP, triggerMs, 5 * 60_000L, pi) }
    }
}

/**
 * Schedule (or replace) a reminder identified by [requestCode] and persist it.
 * If [ringtoneUri] is null the user's chosen default ringtone is used.
 */
fun scheduleReminder(
    context: Context,
    requestCode: Int,
    triggerMs: Long,
    title: String,
    message: String,
    ringtoneUri: String? = null,
    insistent: Boolean? = null,
    kind: String = "reminder",
    shiftId: String? = null
): AppStore.Reminder {
    val ring = ringtoneUri ?: AppStore.getRingtoneUri(context)
    val loop = insistent ?: AppStore.isInsistent(context)
    val reminder = AppStore.Reminder(
        requestCode = requestCode,
        triggerMs = triggerMs,
        title = title,
        message = message,
        ringtoneUri = ring,
        insistent = loop,
        kind = kind,
        shiftId = shiftId
    )

    AppStore.upsertReminder(context, reminder)
    armAlarm(context, requestCode, triggerMs, title, message, ring, loop, kind)
    return reminder
}

/** Cancel a scheduled reminder and forget it. */
fun cancelReminder(context: Context, requestCode: Int) {
    val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val pi = PendingIntent.getBroadcast(
        context, requestCode, Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    alarmMgr.cancel(pi)
    AppStore.removeReminder(context, requestCode)
}

/**
 * Backwards-compatible day reminder used by the calendar's day dialog. Builds a
 * default message from the Khmer date and schedules a brand-new (unique)
 * reminder so multiple reminders per day no longer collide.
 */
fun scheduleAlarm(
    context: Context,
    year: Int, month: Int, day: Int,
    hour: Int, minute: Int,
    alarmTitle: String,
    khmerDate: KhmerDate,
    lang: AppLanguage
): AppStore.Reminder {
    val cal = java.util.Calendar.getInstance().apply {
        set(year, month - 1, day, hour, minute, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val title = alarmTitle.ifBlank {
        if (lang == AppLanguage.EN) "Khmer Calendar Reminder" else "ការរំលឹកប្រតិទិនខ្មែរ"
    }
    val msg = if (lang == AppLanguage.EN)
        "${khmerDate.dayOfWeekEn}, $day ${GREG_MONTHS_EN.getOrElse(month - 1) { "" }} $year"
    else
        "ថ្ងៃ${khmerDate.dayOfWeek} ទី${KhmerCalendarHelper.toKhmerNumeral(day)} ខែ${GREG_MONTHS_KM.getOrElse(month - 1) { "" }}"

    return scheduleReminder(
        context = context,
        requestCode = AppStore.nextRequestCode(context),
        triggerMs = cal.timeInMillis,
        title = title,
        message = msg
    )
}
