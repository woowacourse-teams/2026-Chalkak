package com.stonefive.chalkak.data.local.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stonefive.chalkak.ChalkakApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in supportedActions) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as ChalkakApplication
                application.appContainer.reconcileReminderAlarm()
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private val supportedActions = setOf(
    Intent.ACTION_BOOT_COMPLETED,
    Intent.ACTION_MY_PACKAGE_REPLACED,
    Intent.ACTION_TIME_CHANGED,
    Intent.ACTION_TIMEZONE_CHANGED,
)
