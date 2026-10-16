package com.stonefive.chalkak.data.local.notification

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.notification.NotificationPushData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PushNotificationIconTest {
    @Test
    fun notificationLogoUsesCanvasWithoutClipping() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val drawable = requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_notification_logo))
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
    fun backgroundPushUsesSameNotificationOnlyIcon() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val applicationInfo = context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.GET_META_DATA,
        )

        assertEquals(
            R.drawable.ic_notification_logo,
            applicationInfo.metaData.getInt("com.google.firebase.messaging.default_notification_icon"),
        )
    }

    @Test
    fun foregroundPushUsesNotificationOnlyIcon() {
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
            PushNotificationManager.show(
                context = context,
                payload = NotificationPushData(
                    eventId = TEST_EVENT_ID,
                    notificationId = null,
                    type = "TOPIC",
                    sourceType = "TOPIC",
                    sourceId = "test-topic",
                ),
                title = "오늘의 주제",
                body = "테스트 주제",
            )
            val deadline = SystemClock.elapsedRealtime() + 2_000L
            while (manager.activeNotifications.none { it.id == TEST_EVENT_ID.hashCode() } &&
                SystemClock.elapsedRealtime() < deadline
            ) {
                SystemClock.sleep(20)
            }
            val notification = manager.activeNotifications
                .single { it.id == TEST_EVENT_ID.hashCode() }
                .notification
            assertEquals(R.drawable.ic_notification_logo, notification.smallIcon.resId)
            assertEquals(PushNotificationManager.CHANNEL_ID, notification.channelId)
            assertTrue(notification.contentIntent != null)
        } finally {
            manager.cancel(TEST_EVENT_ID.hashCode())
        }
    }
}

private const val ICON_SIZE = 96
private const val MIN_VISIBLE_SIZE = 77
private const val VISIBLE_ALPHA = 128
private const val TEST_EVENT_ID = "notification-icon-test"
