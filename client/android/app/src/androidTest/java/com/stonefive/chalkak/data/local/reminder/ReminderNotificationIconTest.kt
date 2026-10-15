package com.stonefive.chalkak.data.local.reminder

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stonefive.chalkak.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderNotificationIconTest {
    @Test
    fun notificationLogoUsesCanvasWithoutClipping() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val drawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_stat_chalkak))
        val bitmap = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, ICON_SIZE, ICON_SIZE)
        drawable.draw(Canvas(bitmap))
        val visibleBounds = Rect()
        for (y in 0 until ICON_SIZE) {
            for (x in 0 until ICON_SIZE) {
                if (Color.alpha(bitmap.getPixel(x, y)) >= VISIBLE_ALPHA) {
                    visibleBounds.union(x, y, x + 1, y + 1)
                }
            }
        }

        assertTrue("Logo must fill at least 80% of the width", visibleBounds.width() >= MIN_VISIBLE_SIZE)
        assertTrue("Logo must fill at least 80% of the height", visibleBounds.height() >= MIN_VISIBLE_SIZE)
        assertTrue(
            "Logo must keep transparent horizontal margins",
            visibleBounds.left > 0 && visibleBounds.right < ICON_SIZE,
        )
        assertTrue(
            "Logo must keep transparent vertical margins",
            visibleBounds.top > 0 && visibleBounds.bottom < ICON_SIZE,
        )
    }

    @Test
    fun reminderUsesNotificationOnlyIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            ReminderNotificationManager.show(context)
            val notification = manager.activeNotifications
                .single { it.id == REMINDER_ID }
                .notification
            assertEquals(R.drawable.ic_stat_chalkak, notification.smallIcon.resId)
            assertEquals("daily_topic_reminder", notification.channelId)
            assertTrue(notification.contentIntent != null)
        } finally {
            manager.cancel(REMINDER_ID)
        }
    }
}

private const val ICON_SIZE = 96
private const val MIN_VISIBLE_SIZE = 77
private const val VISIBLE_ALPHA = 128
private const val REMINDER_ID = 1_801
