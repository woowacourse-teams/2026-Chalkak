package com.stonefive.chalkak.core.notification

import android.content.Intent

data class NotificationPushData(
    val eventId: String?,
    val notificationId: String?,
    val type: String?,
    val sourceType: String?,
    val sourceId: String?,
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
            val data = PUSH_DATA_KEYS
                .mapNotNull { key ->
                    extras.getString(key)?.let { value -> key to value }
                }.toMap()
            if (extras.getBoolean(EXTRA_IS_NOTIFICATION_PUSH) ||
                extras.containsKey("google.message_id")
            ) {
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
const val EXTRA_IS_NOTIFICATION_PUSH = "com.stonefive.chalkak.extra.IS_NOTIFICATION_PUSH"

private const val TYPE_KEY = NOTIFICATION_TYPE_KEY
private val PUSH_DATA_KEYS = setOf(
    EVENT_ID_KEY,
    NOTIFICATION_ID_KEY,
    NOTIFICATION_TYPE_KEY,
    SOURCE_TYPE_KEY,
    SOURCE_ID_KEY,
)
