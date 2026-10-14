package com.stonefive.chalkak.data.local.reminder

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.stonefive.chalkak.R
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyReminderCleanupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        LegacyReminderCleanup.cancel(context)
    }

    @Test
    fun cleanupCancelsExistingReminderWithOriginalTimeExtrasAndCanRunRepeatedly() {
        scheduleLegacyReminder()

        LegacyReminderCleanup.cancel(context)
        LegacyReminderCleanup.cancel(context)

        assertNull(findLegacyPendingIntent())
    }

    @Test
    fun receivingLegacyAlarmCancelsItInsteadOfSchedulingAnother() {
        scheduleLegacyReminder()

        ReminderAlarmReceiver().onReceive(context, legacyIntent())

        assertNull(findLegacyPendingIntent())
    }

    @Test
    fun packageUpdateAndBootCancelLegacyReminder() {
        listOf(Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_BOOT_COMPLETED).forEach { action ->
            scheduleLegacyReminder()

            ReminderRescheduleReceiver().onReceive(context, Intent(action))

            assertNull(findLegacyPendingIntent())
        }
    }

    @Test
    fun cleanupLeavesUnrelatedAlarmsIntact() {
        val unrelatedIntent = Intent(context, ReminderAlarmReceiver::class.java).setAction("unrelated")
        val unrelated = PendingIntent.getBroadcast(
            context,
            9_000,
            unrelatedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 3_600_000L,
            unrelated,
        )
        try {
            scheduleLegacyReminder()

            LegacyReminderCleanup.cancel(context)

            assertNotNull(
                PendingIntent.getBroadcast(
                    context,
                    9_000,
                    unrelatedIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } finally {
            alarmManager.cancel(unrelated)
            unrelated.cancel()
        }
    }

    @Test
    fun cleanupRemovesLegacyNotificationAndPreservesPushWithSameNumericId() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val legacyChannel = "daily_topic_reminder"
        val pushChannel = "legacy_cleanup_test_push"
        listOf(legacyChannel, pushChannel).forEach { channel ->
            manager.createNotificationChannel(
                NotificationChannel(channel, channel, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        fun show(channel: String) {
            manager.notify(
                1_801,
                Notification
                    .Builder(context, channel)
                    .setSmallIcon(R.drawable.ic_notification_logo)
                    .setContentTitle("Cleanup regression test")
                    .build(),
            )
            val deadline = SystemClock.elapsedRealtime() + 2_000L
            while (manager.activeNotifications.none { it.id == 1_801 } &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                SystemClock.sleep(20)
            }
            assertTrue(manager.activeNotifications.any { it.id == 1_801 })
        }
        try {
            show(legacyChannel)
            LegacyReminderCleanup.cancel(context)
            val cancellationDeadline = SystemClock.elapsedRealtime() + 2_000L
            while (manager.activeNotifications.any { it.id == 1_801 } &&
                SystemClock.elapsedRealtime() < cancellationDeadline
            ) {
                SystemClock.sleep(20)
            }
            assertTrue(manager.activeNotifications.none { it.id == 1_801 })

            show(pushChannel)
            LegacyReminderCleanup.cancel(context)
            assertTrue(manager.activeNotifications.any { it.id == 1_801 })
        } finally {
            manager.cancel(1_801)
            manager.deleteNotificationChannel(pushChannel)
        }
    }

    private fun scheduleLegacyReminder() {
        val operation = PendingIntent.getBroadcast(
            context,
            1_800,
            legacyIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 3_600_000L,
            operation,
        )
        assertNotNull(findLegacyPendingIntent())
    }

    private fun findLegacyPendingIntent(): PendingIntent? = PendingIntent.getBroadcast(
        context,
        1_800,
        legacyIntent(),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun legacyIntent() = Intent(context, ReminderAlarmReceiver::class.java).apply {
        putExtra("reminder_hour", 18)
        putExtra("reminder_minute", 30)
    }
}
