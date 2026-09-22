package com.stonefive.chalkak.data.local.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.ReminderPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val hour = intent.getIntExtra(EXTRA_REMINDER_HOUR, INVALID_TIME)
        val minute = intent.getIntExtra(EXTRA_REMINDER_MINUTE, INVALID_TIME)
        if (hour !in 0..23 || minute !in 0..59) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val application = context.applicationContext as ChalkakApplication
                val appContainer = application.appContainer
                val preference = appContainer.reminderPreferenceRepository.preference
                    .first { preference -> preference !is ReminderPreference.Loading }
                if (preference is ReminderPreference.Enabled &&
                    preference.hour == hour &&
                    preference.minute == minute
                ) {
                    ReminderNotificationManager.show(context)
                    appContainer.reminderAlarmScheduler.schedule(hour, minute)
                } else {
                    appContainer.reminderAlarmScheduler.cancel()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private const val INVALID_TIME = -1
