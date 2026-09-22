package com.stonefive.chalkak.data.local.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

class AndroidReminderAlarmScheduler(context: Context) {
    private val applicationContext = context.applicationContext
    private val alarmManager = applicationContext.getSystemService(AlarmManager::class.java)

    fun schedule(
        hour: Int,
        minute: Int,
    ) {
        require(hour in 0..23)
        require(minute in 0..59)

        val alarmIntent = reminderAlarmPendingIntent(
            context = applicationContext,
            hour = hour,
            minute = minute,
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextReminderTriggerAtMillis(hour, minute),
            alarmIntent,
        )
    }

    fun cancel() {
        alarmManager.cancel(
            reminderAlarmPendingIntent(
                context = applicationContext,
                hour = 0,
                minute = 0,
            ),
        )
    }
}

internal fun nextReminderTriggerAtMillis(
    hour: Int,
    minute: Int,
    now: ZonedDateTime = ZonedDateTime.now(),
): Long {
    require(hour in 0..23)
    require(minute in 0..59)

    val today = now.toLocalDate()
    val todayAtReminderTime = today.atTime(hour, minute).atZone(now.zone)
    val nextReminderTime = if (todayAtReminderTime.isAfter(now)) {
        todayAtReminderTime
    } else {
        today
            .plusDays(1)
            .atTime(hour, minute)
            .atZone(now.zone)
    }
    return nextReminderTime.toInstant().toEpochMilli()
}

private fun reminderAlarmPendingIntent(
    context: Context,
    hour: Int,
    minute: Int,
): PendingIntent {
    val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
        putExtra(EXTRA_REMINDER_HOUR, hour)
        putExtra(EXTRA_REMINDER_MINUTE, minute)
    }
    return PendingIntent.getBroadcast(
        context,
        REMINDER_ALARM_REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

internal const val EXTRA_REMINDER_HOUR = "reminder_hour"
internal const val EXTRA_REMINDER_MINUTE = "reminder_minute"
private const val REMINDER_ALARM_REQUEST_CODE = 1_800
