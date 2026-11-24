package com.stonefive.chalkak.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenPushTargetTest {
    @Test
    fun `허용한 화면 URI는 그 화면으로 이동 대상을 만든다`() {
        // Given  chalkak://screen/{notifications, notification-settings, record, settings}
        // When   이동 대상을 정한다
        val targets = listOf(
            "chalkak://screen/notifications",
            "chalkak://screen/notification-settings",
            "chalkak://screen/record",
            "chalkak://screen/settings",
        ).map(::screenPushTargetOf)

        // Then   알림함, 알림 설정, 기록 탭, 설정 탭
        assertEquals(
            listOf(
                NotificationTarget.NotificationList,
                NotificationTarget.NotificationSettings,
                NotificationTarget.RecordTab,
                NotificationTarget.SettingsTab,
            ),
            targets,
        )
    }

    @Test
    fun `허용 목록과 정확히 같지 않은 URI는 이동 대상을 만들지 않는다`() {
        // Given  모르는 화면, 다른 scheme·host, 사용자 정보·포트, 추가 경로, 쿼리, 조각, 대소문자 변형, 빈 경로, URI가 아닌 값
        val uris = listOf(
            "chalkak://screen/unknown",
            "https://screen/record",
            "chalkak://notification/record",
            "chalkak://user@screen/record",
            "chalkak://screen:123/record",
            "chalkak://screen/record/extra",
            "chalkak://screen/record?tab=1",
            "chalkak://screen/record#top",
            "chalkak://screen/Record",
            "CHALKAK://screen/record",
            "chalkak://screen/",
            "chalkak://screen",
            "chalkak://screen/re cord",
            "",
        )

        // When   이동 대상을 정한다
        // Then   모두 null(호출한 쪽이 알림함 + 안내로 처리)
        uris.forEach { uri -> assertNull(uri, screenPushTargetOf(uri)) }
    }

    @Test
    fun `알림 설정 푸시는 설정 탭 위에 알림 설정을 쌓는다`() {
        // Given  알림 설정, 기록 탭, 설정 탭, 알림함 대상
        // When   푸시 이동 경로를 정한다
        // Then   알림 설정만 설정 탭 → 알림 설정, 나머지는 하나(뒤로 가면 홈 또는 푸시 전 화면)
        assertEquals(
            listOf(NotificationTarget.SettingsTab, NotificationTarget.NotificationSettings),
            pushNotificationRouteOf(NotificationTarget.NotificationSettings),
        )
        assertEquals(listOf(NotificationTarget.RecordTab), pushNotificationRouteOf(NotificationTarget.RecordTab))
        assertEquals(listOf(NotificationTarget.SettingsTab), pushNotificationRouteOf(NotificationTarget.SettingsTab))
        assertEquals(
            listOf(NotificationTarget.NotificationList),
            pushNotificationRouteOf(NotificationTarget.NotificationList),
        )
    }
}
