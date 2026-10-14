package com.stonefive.chalkak.data.local.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Old rescheduling broadcasts now only clean up local reminders.
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in supportedActions) return
        LegacyReminderCleanup.cancel(context)
    }
}

private val supportedActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
)
