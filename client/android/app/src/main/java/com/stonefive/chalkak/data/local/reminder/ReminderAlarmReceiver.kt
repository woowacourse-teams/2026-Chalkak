package com.stonefive.chalkak.data.local.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// Keep this component name so PendingIntents created by older versions can still be canceled.
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        LegacyReminderCleanup.cancel(context)
    }
}
