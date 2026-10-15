package com.stonefive.chalkak.data.remote

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionCredentials
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.local.auth.toUserSessionState
import com.stonefive.chalkak.data.remote.notification.PushDeviceRegistrationCoordinator
import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PushSessionReadinessTest {
    @Test
    fun successfulRefreshWithoutPushSessionClaimKeepsLoginAndSkipsRegistration() = runBlocking {
        val store = FakePushSessionStore()
        val refreshed = store.credentials.copy(accessToken = "header.e30.signature", refreshToken = "rotated")
        val coordinator =
            SessionRefreshCoordinator(store, FakePushTokenRefresher(TokenRefreshResult.Success(refreshed)))

        val result = coordinator.ensurePushRegistrationSession("user")
        val notifications = FakePushRegistrationRepository()
        PushDeviceRegistrationCoordinator(store, coordinator, notifications).register("fcm-token")

        assertEquals(0, notifications.registrationCount)
        assertEquals(PushSessionReadiness.RegistrationUnavailable, result)
        assertEquals(UserSessionState.Authenticated("user"), store.sessionState.value)
        assertEquals(refreshed, (store.session.value as LocalSession.Authenticated).credentials)
    }

    @Test
    fun rejectedRefreshStillRequiresReauthentication() = runBlocking {
        val store = FakePushSessionStore()
        val coordinator =
            SessionRefreshCoordinator(store, FakePushTokenRefresher(TokenRefreshResult.ReauthenticationRequired))

        assertEquals(PushSessionReadiness.ReauthenticationRequired, coordinator.ensurePushRegistrationSession("user"))
        PushDeviceRegistrationCoordinator(store, coordinator, FakePushRegistrationRepository()).register("fcm-token")
        assertEquals(UserSessionState.SignedOut, store.sessionState.value)
    }

    @Test
    fun refreshedTokenWithSessionClaimCanRegisterPushDevice() = runBlocking {
        val store = FakePushSessionStore()
        val refreshed = store.credentials.copy(accessToken = "header.eyJzZXNzaW9uX2lkIjoic2Vzc2lvbiJ9.signature")
        val coordinator =
            SessionRefreshCoordinator(store, FakePushTokenRefresher(TokenRefreshResult.Success(refreshed)))

        assertEquals(PushSessionReadiness.Ready(refreshed), coordinator.ensurePushRegistrationSession("user"))
        val notifications = FakePushRegistrationRepository()
        PushDeviceRegistrationCoordinator(store, coordinator, notifications).register("fcm-token")
        assertEquals(1, notifications.registrationCount)
    }
}

private class FakePushTokenRefresher(private val result: TokenRefreshResult) : TokenRefresher {
    override suspend fun refresh(
        userId: String,
        refreshToken: String,
    ): TokenRefreshResult = result
}

private class FakePushSessionStore : SessionStore {
    val credentials = SessionCredentials("user", "header.e30.signature", Long.MAX_VALUE, "refresh", Long.MAX_VALUE)
    override val session = MutableStateFlow<LocalSession>(LocalSession.Authenticated(credentials))
    override val sessionState = MutableStateFlow<UserSessionState>(UserSessionState.Authenticated("user"))

    override suspend fun saveSession(credentials: SessionCredentials) {
        session.value = LocalSession.Authenticated(credentials)
        sessionState.value = session.value.toUserSessionState()
    }

    override suspend fun updateTokens(credentials: SessionCredentials): Boolean {
        saveSession(credentials)
        return true
    }

    override suspend fun continueAsGuest() = error("Not used")

    override suspend fun clear() {
        session.value = LocalSession.SignedOut
        sessionState.value = UserSessionState.SignedOut
    }
}

private class FakePushRegistrationRepository : NotificationRepository {
    var registrationCount = 0

    override suspend fun getNotifications(
        page: Int,
        pageSize: Int,
    ) = error("Not used")

    override suspend fun getNotification(notificationId: String) = error("Not used")

    override suspend fun getUnreadStatus() = error("Not used")

    override suspend fun markAsRead(notificationId: String) = error("Not used")

    override suspend fun markAllAsRead() = error("Not used")

    override suspend fun getPushSettings() = error("Not used")

    override suspend fun updatePushSettings(settings: NotificationPushSettingsUpdate) = error("Not used")

    override suspend fun registerCurrentPushDevice(fcmToken: String): NotificationResult<Unit> {
        registrationCount++
        return NotificationResult.Success(Unit)
    }
}
