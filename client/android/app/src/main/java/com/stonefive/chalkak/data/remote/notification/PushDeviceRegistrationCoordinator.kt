package com.stonefive.chalkak.data.remote.notification

import com.stonefive.chalkak.data.local.auth.LocalSession
import com.stonefive.chalkak.data.local.auth.SessionStore
import com.stonefive.chalkak.data.remote.PushSessionReadiness
import com.stonefive.chalkak.data.remote.SessionRefreshCoordinator
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.repository.NotificationRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PushDeviceRegistrationCoordinator(
    private val sessionStore: SessionStore,
    private val sessionRefreshCoordinator: SessionRefreshCoordinator,
    private val notificationRepository: NotificationRepository,
) {
    private val registrationMutex = Mutex()
    private var activeUserId: String? = null
    private var lastRegisteredUserId: String? = null
    private var lastRegisteredToken: String? = null

    suspend fun onSessionChanged(userId: String?) = registrationMutex.withLock {
        if (activeUserId != userId) {
            activeUserId = userId
            lastRegisteredUserId = null
            lastRegisteredToken = null
        }
    }

    suspend fun register(fcmToken: String) = registrationMutex.withLock {
        if (fcmToken.isBlank()) {
            return@withLock
        }

        val expectedUserId = (sessionStore.session.value as? LocalSession.Authenticated)
            ?.credentials
            ?.userId
            ?: run {
                return@withLock
            }
        val readiness = sessionRefreshCoordinator.ensurePushRegistrationSession(expectedUserId)
        val credentials = when (readiness) {
            is PushSessionReadiness.Ready -> {
                if (readiness.credentials.userId != expectedUserId) return@withLock
                readiness.credentials
            }

            PushSessionReadiness.NoSession,
            PushSessionReadiness.SessionChanged,
            PushSessionReadiness.TransientFailure,
            -> {
                return@withLock
            }

            PushSessionReadiness.ReauthenticationRequired -> {
                val currentUserId = (sessionStore.session.value as? LocalSession.Authenticated)
                    ?.credentials
                    ?.userId
                if (currentUserId == expectedUserId) sessionStore.clear()
                return@withLock
            }
        }

        val current = (sessionStore.session.value as? LocalSession.Authenticated)
            ?.credentials
            ?: run {
                return@withLock
            }
        if (current.userId != credentials.userId || current.accessToken != credentials.accessToken) {
            return@withLock
        }
        if (lastRegisteredUserId == current.userId && lastRegisteredToken == fcmToken) {
            return@withLock
        }

        when (notificationRepository.registerCurrentPushDevice(fcmToken)) {
            is NotificationResult.Success -> {
                lastRegisteredUserId = current.userId
                lastRegisteredToken = fcmToken
            }

            is NotificationResult.Failure -> Unit
        }
    }
}
