package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title   = intent.getStringExtra("title")   ?: "ការរំលឹកប្រតិទិនខ្មែរ"
        val message = intent.getStringExtra("message") ?: ""
        showNotification(context, title, message)

        val repeat = runCatching {
            AlarmRepeat.valueOf(intent.getStringExtra("repeat") ?: "ONCE")
        }.getOrDefault(AlarmRepeat.ONCE)

        if (repeat != AlarmRepeat.ONCE) {
            rescheduleAlarm(context, intent, repeat)
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val channelId = "khmer_calendar_reminders"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibPattern = longArrayOf(0, 600, 200, 600, 200, 600)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttrs = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
            val ch = NotificationChannel(
                channelId,
                "Khmer Calendar Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders set from the Khmer Calendar app"
                setSound(alarmSound, audioAttrs)
                enableVibration(true)
                setVibrationPattern(vibPattern)
                enableLights(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(ch)
        }

        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pi = PendingIntent.getActivity(
            context, 0, launchIntent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .setAutoCancel(true)
            .setSound(alarmSound)
            .setVibrate(vibPattern)
            .build()

        nm.notify(System.currentTimeMillis().toInt() and 0x7FFFFFFF, notification)
    }

    private fun rescheduleAlarm(context: Context, intent: Intent, repeat: AlarmRepeat) {
        val year        = intent.getIntExtra("year", 0)
        val month       = intent.getIntExtra("month", 0)
        val day         = intent.getIntExtra("day", 0)
        val hour        = intent.getIntExtra("hour", 0)
        val minute      = intent.getIntExtra("minute", 0)
        val requestCode = intent.getIntExtra("requestCode", 0)
        val title       = intent.getStringExtra("title") ?: ""
        val message     = intent.getStringExtra("message") ?: ""

        if (year == 0 || month == 0 || day == 0 || requestCode == 0) return

        val nextCal = java.util.Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            when (repeat) {
                AlarmRepeat.DAILY   -> add(java.util.Calendar.DAY_OF_MONTH, 1)
                AlarmRepeat.WEEKLY  -> add(java.util.Calendar.WEEK_OF_YEAR, 1)
                AlarmRepeat.MONTHLY -> add(java.util.Calendar.MONTH, 1)
                AlarmRepeat.YEARLY  -> add(java.util.Calendar.YEAR, 1)
                AlarmRepeat.ONCE    -> {}
            }
        }
        val nextTrigger = nextCal.timeInMillis
        val nextYear  = nextCal.get(java.util.Calendar.YEAR)
        val nextMonth = nextCal.get(java.util.Calendar.MONTH) + 1
        val nextDay   = nextCal.get(java.util.Calendar.DAY_OF_MONTH)

        val newIntent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("title",       title)
            putExtra("message",     message)
            putExtra("repeat",      repeat.name)
            putExtra("year",        nextYear)
            putExtra("month",       nextMonth)
            putExtra("day",         nextDay)
            putExtra("hour",        hour)
            putExtra("minute",      minute)
            putExtra("requestCode", requestCode)
        }

        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val pi = PendingIntent.getBroadcast(
            context, requestCode, newIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (alarmMgr.canScheduleExactAlarms())
                    alarmMgr.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, nextTrigger, pi)
                else
                    alarmMgr.setWindow(android.app.AlarmManager.RTC_WAKEUP, nextTrigger, 5 * 60_000L, pi)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                alarmMgr.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, nextTrigger, pi)
            else ->
                alarmMgr.setExact(android.app.AlarmManager.RTC_WAKEUP, nextTrigger, pi)
        }

        // Update stored triggerMs + date in SharedPreferences
        try {
            val prefs = context.getSharedPreferences("khmer_calendar_alarms", Context.MODE_PRIVATE)
            val arr = JSONArray(prefs.getString("alarms", "[]") ?: "[]")
            val updated = JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getInt("requestCode") == requestCode) {
                    obj.put("triggerMs", nextTrigger)
                    obj.put("year", nextYear)
                    obj.put("month", nextMonth)
                    obj.put("day", nextDay)
                }
                updated.put(obj)
            }
            prefs.edit().putString("alarms", updated.toString()).apply()
        } catch (_: Exception) {}
    }
}
