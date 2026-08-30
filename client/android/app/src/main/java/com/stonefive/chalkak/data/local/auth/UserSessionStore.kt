package com.stonefive.chalkak.data.local.auth

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stonefive.chalkak.domain.model.UserSessionState
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.authDataStore by preferencesDataStore(name = "auth_session")
private val userIdKey = stringPreferencesKey("user_id")
private val encryptedAccessTokenKey = stringPreferencesKey("encrypted_access_token")
private val expiresAtEpochSecondsKey = longPreferencesKey("expires_at_epoch_seconds")
private val isGuestKey = booleanPreferencesKey("is_guest")
private val legacyPlaintextAccessTokenKey = stringPreferencesKey("access_token")

class UserSessionStore(
    context: Context,
    private val scope: CoroutineScope,
    private val accessTokenCipher: AccessTokenCipher = AndroidKeystoreAccessTokenCipher(),
    private val currentEpochSeconds: () -> Long = { Instant.now().epochSecond },
) : SessionStore {
    private val dataStore = context.authDataStore
    private val sessionMutex = Mutex()
    private val mutableSession = MutableStateFlow<LocalSession>(LocalSession.Loading)
    private val mutableSessionState = MutableStateFlow<UserSessionState>(UserSessionState.Loading)
    private var expiryJob: Job? = null

    override val session: StateFlow<LocalSession> = mutableSession.asStateFlow()
    override val sessionState: StateFlow<UserSessionState> = mutableSessionState.asStateFlow()

    init {
        scope.launch {
            dataStore.data
                .catch { error ->
                    if (error is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw error
                    }
                }.collect { preferences ->
                    val storedSession = preferences[encryptedAccessTokenKey]
                        ?.let(accessTokenCipher::decrypt)
                        ?.let { accessToken ->
                            val userId = preferences[userIdKey]
                            val expiresAt = preferences[expiresAtEpochSecondsKey]
                            if (
                                !userId.isNullOrBlank() &&
                                accessToken.isNotBlank() &&
                                expiresAt != null &&
                                expiresAt > currentEpochSeconds()
                            ) {
                                LocalSession.Authenticated(
                                    SessionCredentials(
                                        userId = userId,
                                        accessToken = accessToken,
                                        expiresAtEpochSeconds = expiresAt,
                                    ),
                                )
                            } else {
                                null
                            }
                        }

                    when {
                        storedSession != null -> publish(storedSession)

                        preferences[isGuestKey] == true -> publish(LocalSession.Guest)

                        else -> {
                            publish(LocalSession.SignedOut)
                            if (preferences.containsStoredCredentials()) {
                                scope.launch { clear() }
                            }
                        }
                    }
                }
        }
    }

    override suspend fun continueAsGuest() {
        sessionMutex.withLock {
            dataStore.edit { preferences ->
                preferences.removeStoredCredentials()
                preferences[isGuestKey] = true
            }
            publish(LocalSession.Guest)
        }
    }

    override suspend fun saveSession(credentials: SessionCredentials) {
        require(credentials.userId.isNotBlank())
        require(credentials.accessToken.isNotBlank())
        require(credentials.expiresAtEpochSeconds > currentEpochSeconds())

        sessionMutex.withLock {
            val encryptedAccessToken = accessTokenCipher.encrypt(credentials.accessToken)
            dataStore.edit { preferences ->
                preferences[userIdKey] = credentials.userId
                preferences[encryptedAccessTokenKey] = encryptedAccessToken
                preferences[expiresAtEpochSecondsKey] = credentials.expiresAtEpochSeconds
                preferences.remove(legacyPlaintextAccessTokenKey)
                preferences.remove(isGuestKey)
            }
            publish(LocalSession.Authenticated(credentials))
        }
    }

    override suspend fun clear() {
        sessionMutex.withLock {
            clearStoredCredentials()
            publish(LocalSession.SignedOut)
        }
    }

    override suspend fun clearIfAccessTokenMatches(accessToken: String) {
        sessionMutex.withLock {
            val authenticated = mutableSession.value as? LocalSession.Authenticated
            if (authenticated?.credentials?.accessToken != accessToken) return

            clearStoredCredentials()
            publish(LocalSession.SignedOut)
        }
    }

    private suspend fun clearStoredCredentials() {
        dataStore.edit { preferences ->
            preferences.removeStoredCredentials()
            preferences.remove(isGuestKey)
        }
    }

    private fun publish(session: LocalSession) {
        mutableSession.value = session
        mutableSessionState.value = session.toUserSessionState()
        expiryJob?.cancel()
        expiryJob = (session as? LocalSession.Authenticated)?.let { authenticated ->
            val delayMillis = (
                authenticated.credentials.expiresAtEpochSeconds - currentEpochSeconds()
                ).coerceIn(0, Long.MAX_VALUE / MILLIS_PER_SECOND) * MILLIS_PER_SECOND
            scope.launch {
                delay(delayMillis)
                clearIfAccessTokenMatches(authenticated.credentials.accessToken)
            }
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
    }
}

private fun androidx.datastore.preferences.core.MutablePreferences.removeStoredCredentials() {
    remove(userIdKey)
    remove(encryptedAccessTokenKey)
    remove(expiresAtEpochSecondsKey)
    remove(legacyPlaintextAccessTokenKey)
}

private fun androidx.datastore.preferences.core.Preferences.containsStoredCredentials(): Boolean =
    this[userIdKey] != null ||
        this[encryptedAccessTokenKey] != null ||
        this[expiresAtEpochSecondsKey] != null ||
        this[legacyPlaintextAccessTokenKey] != null
