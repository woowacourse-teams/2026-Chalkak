package com.stonefive.chalkak.core.notification

import android.content.Intent

data class NotificationPushData(
    val eventId: String?,
    val notificationId: String?,
    val type: String?,
    val sourceType: String?,
    val sourceId: String?,
    // 앱이 만든 화면 푸시의 URI(`chalkak://screen/...`). 서버 푸시에는 없다. 검증은 이동할 때 한다.
    val screen: String? = null,
) {
    companion object {
        fun from(data: Map<String, String>): NotificationPushData? {
            if (PUSH_DATA_KEYS.none(data::containsKey)) return null
            return NotificationPushData(
                eventId = data[EVENT_ID_KEY],
                notificationId = data[NOTIFICATION_ID_KEY],
                type = data[TYPE_KEY],
                sourceType = data[SOURCE_TYPE_KEY],
                sourceId = data[SOURCE_ID_KEY],
            )
        }

        fun from(intent: Intent?): NotificationPushData? {
            val extras = intent?.extras ?: return null
            // 최근 앱에서 다시 열면 처음 알림으로 연 인텐트가 그대로 다시 온다. 이미 처리한 푸시라 다시 열지 않는다.
            if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
            val data = PUSH_DATA_KEYS
                .mapNotNull { key ->
                    extras.getString(key)?.let { value -> key to value }
                }.toMap()
            if (extras.getBoolean(EXTRA_IS_NOTIFICATION_PUSH)) {
                val push = from(data) ?: NotificationPushData(null, null, null, null, null)
                // 서버 푸시는 알림마다 다른 `chalkak://notification/...`을 쓴다. 그 밖의 data는 화면 푸시로 보고 이동할 때 검증한다.
                val screen = intent.dataString?.takeUnless { it.startsWith(SERVER_PUSH_URI_PREFIX) }
                return push.copy(screen = screen)
            }
            if (extras.containsKey("google.message_id")) {
                return from(data) ?: NotificationPushData(null, null, null, null, null)
            }
            return from(data)
        }
    }
}

const val EVENT_ID_KEY = "eventId"
const val NOTIFICATION_ID_KEY = "notificationId"
const val NOTIFICATION_TYPE_KEY = "type"
const val SOURCE_TYPE_KEY = "sourceType"
const val SOURCE_ID_KEY = "sourceId"
const val SCREEN_PUSH_URI_PREFIX = "chalkak://screen/"
const val SERVER_PUSH_URI_PREFIX = "chalkak://notification/"
const val EXTRA_IS_NOTIFICATION_PUSH = "com.stonefive.chalkak.extra.IS_NOTIFICATION_PUSH"

private const val TYPE_KEY = NOTIFICATION_TYPE_KEY
private val PUSH_DATA_KEYS = setOf(
    EVENT_ID_KEY,
    NOTIFICATION_ID_KEY,
    NOTIFICATION_TYPE_KEY,
    SOURCE_TYPE_KEY,
    SOURCE_ID_KEY,
)
