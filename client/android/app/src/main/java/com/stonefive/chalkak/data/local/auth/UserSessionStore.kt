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
private val encryptedRefreshTokenKey = stringPreferencesKey("encrypted_refresh_token")
private val refreshTokenExpiresAtEpochSecondsKey =
    longPreferencesKey("refresh_token_expires_at_epoch_seconds")
private val isGuestKey = booleanPreferencesKey("is_guest")
private val legacyPlaintextAccessTokenKey = stringPreferencesKey("access_token")

class UserSessionStore(
    context: Context,
    private val scope: CoroutineScope,
    private val tokenCipher: TokenCipher = AndroidKeystoreTokenCipher(),
    private val currentEpochSeconds: () -> Long = { Instant.now().epochSecond },
) : SessionStore {
    private val dataStore = context.authDataStore
    private val sessionMutex = Mutex()
    private val mutableSession = MutableStateFlow<LocalSession>(LocalSession.Loading)
    private val mutableSessionState = MutableStateFlow<UserSessionState>(UserSessionState.Loading)

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
                    val storedSession = preferences.toStoredSession()

                    when {
                        storedSession != null -> publish(storedSession)

                        preferences[isGuestKey] == true -> publish(LocalSession.Guest)

                        else -> {
                            publish(LocalSession.SignedOut)
                            if (preferences.containsStoredCredentials()) {
                                scope.launch { clearInvalidStoredCredentials() }
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
        require(credentials.refreshToken.isNotBlank())
        require(credentials.refreshTokenExpiresAtEpochSeconds > currentEpochSeconds())

        sessionMutex.withLock {
            val encryptedAccessToken = tokenCipher.encrypt(credentials.accessToken)
            val encryptedRefreshToken = tokenCipher.encrypt(credentials.refreshToken)
            dataStore.edit { preferences ->
                preferences[userIdKey] = credentials.userId
                preferences[encryptedAccessTokenKey] = encryptedAccessToken
                preferences[expiresAtEpochSecondsKey] = credentials.expiresAtEpochSeconds
                preferences[encryptedRefreshTokenKey] = encryptedRefreshToken
                preferences[refreshTokenExpiresAtEpochSecondsKey] =
                    credentials.refreshTokenExpiresAtEpochSeconds
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

    private suspend fun clearStoredCredentials() {
        dataStore.edit { preferences ->
            preferences.removeStoredCredentials()
            preferences.remove(isGuestKey)
        }
    }

    private suspend fun clearInvalidStoredCredentials() {
        sessionMutex.withLock {
            var cleared = false
            dataStore.edit { preferences ->
                if (preferences.containsStoredCredentials() && preferences.toStoredSession() == null) {
                    preferences.removeStoredCredentials()
                    preferences.remove(isGuestKey)
                    cleared = true
                }
            }
            if (cleared) publish(LocalSession.SignedOut)
        }
    }

    private fun androidx.datastore.preferences.core.Preferences.toStoredSession(): LocalSession.Authenticated? {
        val accessToken = this[encryptedAccessTokenKey]?.let(tokenCipher::decrypt)
        val refreshToken = this[encryptedRefreshTokenKey]?.let(tokenCipher::decrypt)
        val userId = this[userIdKey]
        val expiresAt = this[expiresAtEpochSecondsKey]
        val refreshTokenExpiresAt = this[refreshTokenExpiresAtEpochSecondsKey]

        val sessionIsValid = !userId.isNullOrBlank() &&
            !accessToken.isNullOrBlank() &&
            !refreshToken.isNullOrBlank() &&
            expiresAt != null &&
            refreshTokenExpiresAt != null &&
            refreshTokenExpiresAt > currentEpochSeconds()

        return if (sessionIsValid) {
            LocalSession.Authenticated(
                SessionCredentials(
                    userId = userId!!,
                    accessToken = accessToken!!,
                    expiresAtEpochSeconds = expiresAt!!,
                    refreshToken = refreshToken!!,
                    refreshTokenExpiresAtEpochSeconds = refreshTokenExpiresAt!!,
                ),
            )
        } else {
            null
        }
    }

    private fun publish(session: LocalSession) {
        mutableSession.value = session
        mutableSessionState.value = session.toUserSessionState()
    }
}

private fun androidx.datastore.preferences.core.MutablePreferences.removeStoredCredentials() {
    remove(userIdKey)
    remove(encryptedAccessTokenKey)
    remove(expiresAtEpochSecondsKey)
    remove(encryptedRefreshTokenKey)
    remove(refreshTokenExpiresAtEpochSecondsKey)
    remove(legacyPlaintextAccessTokenKey)
}

private fun androidx.datastore.preferences.core.Preferences.containsStoredCredentials(): Boolean =
    this[userIdKey] != null ||
        this[encryptedAccessTokenKey] != null ||
        this[expiresAtEpochSecondsKey] != null ||
        this[encryptedRefreshTokenKey] != null ||
        this[refreshTokenExpiresAtEpochSecondsKey] != null ||
        this[legacyPlaintextAccessTokenKey] != null
