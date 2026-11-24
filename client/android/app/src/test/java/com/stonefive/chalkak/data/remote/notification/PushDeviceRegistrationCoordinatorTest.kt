package com.stonefive.chalkak.data.remote.notification

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.SessionRefreshCoordinator
import com.stonefive.chalkak.data.remote.TokenRefreshResult
import com.stonefive.chalkak.data.remote.TokenRefresher
import com.stonefive.chalkak.domain.model.NotificationDetail
import com.stonefive.chalkak.domain.model.NotificationPage
import com.stonefive.chalkak.domain.model.NotificationPushSettings
import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushDeviceRegistrationCoordinatorTest {
    @Test
    fun `푸시 기기 등록 중 Refresh 토큰이 거절되면 재인증 필요 상태가 된다`() = runTest {
        // Given "u1" 로그인, AT에 session_id 없음, 갱신 요청이 401 REAUTHENTICATION_REQUIRED (갱신기가 ReauthenticationRequired를 돌려줌)
        // When  푸시 기기 등록(register("fcm-1"))
        // Then  세션 상태 ReauthenticationRequired("u1"), 저장된 토큰 없음, 기기 등록 요청 0회
        val store = FakeSessionStore(
            SessionCredentials(
                userId = "u1",
                accessToken = "at-without-session-id",
                expiresAtEpochSeconds = Long.MAX_VALUE,
                refreshToken = "rt-1",
                refreshTokenExpiresAtEpochSeconds = Long.MAX_VALUE,
            ),
        )
        val refresher = object : TokenRefresher {
            override suspend fun refresh(
                userId: String,
                refreshToken: String,
            ): TokenRefreshResult = TokenRefreshResult.ReauthenticationRequired
        }
        val notifications = FakeNotificationRepository()
        val coordinator = PushDeviceRegistrationCoordinator(
            sessionStore = store,
            sessionRefreshCoordinator = SessionRefreshCoordinator(store, refresher),
            notificationRepository = notifications,
        )

        coordinator.register("fcm-1")

        assertEquals(UserSessionState.ReauthenticationRequired("u1"), store.sessionState.value)
        assertNull((store.session.value as? LocalSession.Authenticated)?.credentials)
        assertEquals(0, notifications.registerRequests)
    }
}

private class FakeSessionStore(initial: SessionCredentials) : SessionStore {
    private val mutableSession = MutableStateFlow<LocalSession>(LocalSession.Authenticated(initial))
    private val mutableSessionState =
        MutableStateFlow<UserSessionState>(UserSessionState.Authenticated(initial.userId))

    override val session: StateFlow<LocalSession> = mutableSession
    override val sessionState: StateFlow<UserSessionState> = mutableSessionState

    override suspend fun continueAsGuest() = error("unused")

    override suspend fun saveSession(credentials: SessionCredentials) {
        mutableSession.value = LocalSession.Authenticated(credentials)
        mutableSessionState.value = UserSessionState.Authenticated(credentials.userId)
    }

    override suspend fun updateTokens(credentials: SessionCredentials): Boolean {
        val current = (mutableSession.value as? LocalSession.Authenticated)?.credentials
        if (current?.userId != credentials.userId) return false
        saveSession(credentials)
        return true
    }

    override suspend fun clear() {
        mutableSession.value = LocalSession.SignedOut
        mutableSessionState.value = UserSessionState.SignedOut
    }

    override suspend fun requireReauthentication(userId: String) {
        val current = (mutableSession.value as? LocalSession.Authenticated)?.credentials ?: return
        if (current.userId != userId) return
        mutableSession.value = LocalSession.ReauthenticationRequired(userId)
        mutableSessionState.value = UserSessionState.ReauthenticationRequired(userId)
    }
}

private class FakeNotificationRepository : NotificationRepository {
    var registerRequests = 0

    override suspend fun registerCurrentPushDevice(fcmToken: String): NotificationResult<Unit> {
        registerRequests++
        return NotificationResult.Success(Unit)
    }

    override suspend fun getUnreadStatus(): NotificationResult<Boolean> = error("unused")

    override suspend fun getNotifications(
        page: Int,
        pageSize: Int,
    ): NotificationResult<NotificationPage> = error("unused")

    override suspend fun getNotification(notificationId: String): NotificationResult<NotificationDetail> =
        error("unused")

    override suspend fun markAsRead(notificationId: String): NotificationResult<Unit> = error("unused")

    override suspend fun markAllAsRead(): NotificationResult<Unit> = error("unused")

    override suspend fun getPushSettings(): NotificationResult<NotificationPushSettings> = error("unused")

    override suspend fun updatePushSettings(settings: NotificationPushSettingsUpdate): NotificationResult<Unit> =
        error("unused")
}
