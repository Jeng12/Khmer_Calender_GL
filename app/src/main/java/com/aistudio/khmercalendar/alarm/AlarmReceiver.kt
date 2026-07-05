package com.aistudio.khmercalendar.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.aistudio.khmercalendar.data.AppStore

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title   = intent.getStringExtra("title")   ?: "ការរំលឹកប្រតិទិនខ្មែរ"
        val message = intent.getStringExtra("message") ?: ""
        val ringtoneUri = intent.getStringExtra("ringtoneUri")
        val insistent = intent.getBooleanExtra("insistent", false)
        val kind = intent.getStringExtra("kind") ?: "reminder"
        val requestCode = intent.getIntExtra("requestCode", 0)
        showNotification(context, requestCode, title, message, ringtoneUri, insistent, kind)

        // This was a one-shot alarm; drop it from persistence now that it fired
        // (boot rescheduling only re-arms future alarms anyway).
        runCatching {
            val rc = intent.getIntExtra("requestCode", 0)
            if (rc != 0) AppStore.removeReminder(context, rc)
        }
    }

    private fun showNotification(
        context: Context,
        requestCode: Int,
        title: String,
        message: String,
        ringtoneUriStr: String?,
        insistent: Boolean,
        kind: String
    ) {
        // Work-shift reminders get a calendar/work icon; others an alarm-clock icon.
        val smallIcon = if (kind == "shift")
            android.R.drawable.ic_menu_my_calendar
        else
            android.R.drawable.ic_lock_idle_alarm
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val alarmSound: Uri = ringtoneUriStr?.let { runCatching { Uri.parse(it) }.getOrNull() }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val vibPattern = longArrayOf(0, 600, 200, 600, 200, 600)

        // A channel's sound is immutable once created, so derive a stable channel
        // id per chosen ringtone — picking a new ringtone uses a new channel.
        val channelId = "khmer_calendar_reminders_" + (ringtoneUriStr?.hashCode() ?: 0)

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

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
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

        val notification = builder.build()
        // "Ring until dismissed" — keep alerting until the user clears it.
        if (insistent) {
            notification.flags = notification.flags or android.app.Notification.FLAG_INSISTENT
        }

        // Use the alarm's unique requestCode as the notification id so two reminders
        // firing in the same millisecond don't replace each other in the shade.
        val notifId = if (requestCode != 0) requestCode and 0x7FFFFFFF
                      else System.currentTimeMillis().toInt() and 0x7FFFFFFF
        nm.notify(notifId, notification)
    }
}
