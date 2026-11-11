package com.stonefive.chalkak.feature.developertools

import androidx.annotation.StringRes
import com.stonefive.chalkak.R
import com.stonefive.chalkak.core.notification.SCREEN_PUSH_URI_PREFIX

/** 개발자 도구에서 보낼 수 있는 화면 푸시. [INVALID]는 허용하지 않는 URI로 알림함 + 안내를 확인한다. */
enum class DeveloperToolsTestNotification(
    @param:StringRes val labelRes: Int,
    val uri: String,
) {
    NOTIFICATIONS(R.string.developer_tools_test_notification_notifications, "${SCREEN_PUSH_URI_PREFIX}notifications"),
    NOTIFICATION_SETTINGS(
        R.string.developer_tools_test_notification_notification_settings,
        "${SCREEN_PUSH_URI_PREFIX}notification-settings",
    ),
    RECORD(R.string.developer_tools_test_notification_record, "${SCREEN_PUSH_URI_PREFIX}record"),
    SETTINGS(R.string.developer_tools_test_notification_settings, "${SCREEN_PUSH_URI_PREFIX}settings"),
    INVALID(R.string.developer_tools_test_notification_invalid, "${SCREEN_PUSH_URI_PREFIX}unknown"),
}
