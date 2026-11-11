package com.stonefive.chalkak.feature.developertools

import com.stonefive.chalkak.MainDispatcherRule
import com.stonefive.chalkak.core.notification.LocalNotificationPublisher
import com.stonefive.chalkak.core.notification.NotificationPushData
import com.stonefive.chalkak.domain.model.AppConfig
import com.stonefive.chalkak.domain.model.AppConfigDiagnostics
import com.stonefive.chalkak.domain.model.HomeIssueExperiment
import com.stonefive.chalkak.domain.repository.AppConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeveloperToolsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `테스트 알림을 보내면 화면 URI를 실은 화면 푸시를 띄운다`() {
        // Given  알림을 띄울 수 있다
        val publisher = FakeLocalNotificationPublisher(enabled = true)
        val viewModel = DeveloperToolsViewModel(FakeAppConfigRepository(), publisher)

        // When   "알림 설정" 테스트 알림을 보낸다
        val sent = viewModel.sendTestNotification(DeveloperToolsTestNotification.NOTIFICATION_SETTINGS, "제목")

        // Then   chalkak://screen/notification-settings를 실은, notificationId 없는 알림 하나
        val published = publisher.published.single()
        assertTrue(sent)
        assertEquals("chalkak://screen/notification-settings", published.payload.screen)
        assertNull(published.payload.notificationId)
        assertTrue(
            published.payload.eventId!!
                .startsWith("developer-tools-"),
        )
        assertEquals("제목", published.title)
        assertEquals("chalkak://screen/notification-settings", published.body)
    }

    @Test
    fun `알림이 꺼져 있으면 보내지 못했다고 알린다`() {
        // Given  앱 알림이나 알림 채널이 꺼져 있다
        val viewModel = DeveloperToolsViewModel(
            FakeAppConfigRepository(),
            FakeLocalNotificationPublisher(enabled = false),
        )

        // When   테스트 알림을 보낸다
        val sent = viewModel.sendTestNotification(DeveloperToolsTestNotification.RECORD, "제목")

        // Then   false(화면이 안내를 띄운다)
        assertFalse(sent)
    }

    private data class Published(
        val payload: NotificationPushData,
        val title: String,
        val body: String,
    )

    private class FakeLocalNotificationPublisher(private val enabled: Boolean) : LocalNotificationPublisher {
        val published = mutableListOf<Published>()

        override fun publish(
            payload: NotificationPushData,
            title: String,
            body: String,
        ): Boolean {
            if (!enabled) return false
            published += Published(payload, title, body)
            return true
        }
    }

    private class FakeAppConfigRepository : AppConfigRepository {
        override val config: StateFlow<AppConfig> = MutableStateFlow(AppConfig())
        override val diagnostics: StateFlow<AppConfigDiagnostics> = MutableStateFlow(AppConfigDiagnostics())

        override suspend fun refresh() = Unit

        override suspend fun homeIssueExperiment(): HomeIssueExperiment = error("사용하지 않음")
    }
}
