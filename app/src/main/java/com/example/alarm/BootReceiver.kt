package com.example.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        val prefs = context.getSharedPreferences("khmer_calendar_alarms", Context.MODE_PRIVATE)
        val alarmsJson = prefs.getString("alarms", "[]") ?: "[]"
        val now = System.currentTimeMillis()

        try {
            val alarms = JSONArray(alarmsJson)
            val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val futureAlarms = JSONArray()

            for (i in 0 until alarms.length()) {
                val alarm = alarms.getJSONObject(i)
                val triggerMs = alarm.getLong("triggerMs")
                if (triggerMs <= now) continue

                futureAlarms.put(alarm)

                val alarmTitle   = alarm.getString("title")
                val alarmMessage = alarm.getString("message")
                val requestCode  = alarm.getInt("requestCode")

                val alarmIntent = Intent(context, AlarmReceiver::class.java).apply {
                    putExtra("title", alarmTitle)
                    putExtra("message", alarmMessage)
                }
                val pi = PendingIntent.getBroadcast(
                    context, requestCode, alarmIntent,
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

            prefs.edit().putString("alarms", futureAlarms.toString()).apply()
        } catch (_: Exception) {}
    }
}
