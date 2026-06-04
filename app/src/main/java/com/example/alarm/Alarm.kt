package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.calendar.KhmerCalendarHelper
import com.example.calendar.KhmerDate
import com.example.core.AppLanguage
import com.example.core.GREG_MONTHS_EN
import com.example.core.GREG_MONTHS_KM
import org.json.JSONArray
import org.json.JSONObject

// ── Alarm/Reminder helper ────────────────────────────────────────────────────
fun scheduleAlarm(
    context: android.content.Context,
    year: Int, month: Int, day: Int,
    hour: Int, minute: Int,
    alarmTitle: String,
    khmerDate: KhmerDate,
    lang: AppLanguage
) {
    val cal = java.util.Calendar.getInstance().apply {
        set(year, month - 1, day, hour, minute, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val triggerMs = cal.timeInMillis
    val alarmMgr = context.getSystemService(android.content.Context.ALARM_SERVICE) as AlarmManager
    val title = alarmTitle.ifBlank {
        if (lang == AppLanguage.EN) "Khmer Calendar Reminder" else "ការរំលឹកប្រតិទិនខ្មែរ"
    }
    val msg = if (lang == AppLanguage.EN)
        "${khmerDate.dayOfWeekEn}, $day ${GREG_MONTHS_EN.getOrElse(month - 1) { "" }} $year"
    else
        "ថ្ងៃ${khmerDate.dayOfWeek} ទី${KhmerCalendarHelper.toKhmerNumeral(day)} ខែ${GREG_MONTHS_KM.getOrElse(month - 1) { "" }}"
    val requestCode = year * 10000 + month * 100 + day

    // Persist so BootReceiver can reschedule after device reboot
    try {
        val prefs = context.getSharedPreferences("khmer_calendar_alarms", android.content.Context.MODE_PRIVATE)
        val existing = org.json.JSONArray(prefs.getString("alarms", "[]") ?: "[]")
        val updated = org.json.JSONArray()
        for (i in 0 until existing.length()) {
            if (existing.getJSONObject(i).getInt("requestCode") != requestCode)
                updated.put(existing.getJSONObject(i))
        }
        updated.put(org.json.JSONObject().apply {
            put("requestCode", requestCode)
            put("triggerMs", triggerMs)
            put("title", title)
            put("message", msg)
        })
        prefs.edit().putString("alarms", updated.toString()).apply()
    } catch (_: Exception) {}

    val intent = Intent(context, AlarmReceiver::class.java).apply {
        putExtra("title", title)
        putExtra("message", msg)
    }
    val pi = PendingIntent.getBroadcast(
        context, requestCode, intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (alarmMgr.canScheduleExactAlarms())
                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            else
                alarmMgr.setWindow(AlarmManager.RTC_WAKEUP, triggerMs, 5 * 60_000L, pi)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
        else ->
            alarmMgr.setExact(AlarmManager.RTC_WAKEUP, triggerMs, pi)
    }
}
