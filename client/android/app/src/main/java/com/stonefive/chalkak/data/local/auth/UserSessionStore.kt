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
                    // 판정·발행을 sessionMutex 안에서 한다(근거: PR #1017 리뷰). 토큰 저장 직후의 저장소 변경을 처리하기 전에
                    // requireReauthentication이 끼면, 잠그지 않은 수집기가 옛 자격 증명으로 Authenticated를 다시 내고
                    // 이어서 빈 저장소에서 SignedOut을 내 재인증 상태를 덮을 수 있다.
                    // 로그인·게스트·로그아웃은 각 함수가 직접 발행하므로 여기서 무시해도 상태가 빠지지 않는다.
                    sessionMutex.withLock {
                        // 재인증 필요 상태는 메모리에만 있다. 그 사이의 저장소 변경(토큰 삭제 포함)이 이 상태를 덮지 않게 맨 앞에서 거른다.
                        if (mutableSession.value is LocalSession.ReauthenticationRequired) return@withLock

                        val storedSession = preferences.toStoredSession()
                        when {
                            storedSession != null -> publish(storedSession)

                            preferences[isGuestKey] == true -> publish(LocalSession.Guest)

                            else -> {
                                publish(LocalSession.SignedOut)
                                if (preferences.containsStoredCredentials()) {
                                    // 같은 sessionMutex를 쓰므로 잠근 채 기다리지 않고 따로 띄운다(교착 방지).
                                    scope.launch { clearInvalidStoredCredentials() }
                                }
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
        validate(credentials)
        sessionMutex.withLock {
            persist(credentials)
        }
    }

    override suspend fun updateTokens(credentials: SessionCredentials): Boolean {
        validate(credentials)
        return sessionMutex.withLock {
            val current = (mutableSession.value as? LocalSession.Authenticated)?.credentials
            if (current == null || current.userId != credentials.userId) {
                false
            } else {
                persist(credentials)
                true
            }
        }
    }

    private fun validate(credentials: SessionCredentials) {
        require(credentials.userId.isNotBlank())
        require(credentials.accessToken.isNotBlank())
        require(credentials.refreshToken.isNotBlank())
        require(credentials.refreshTokenExpiresAtEpochSeconds > currentEpochSeconds())
    }

    private suspend fun persist(credentials: SessionCredentials) {
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

    override suspend fun clear() {
        sessionMutex.withLock {
            clearStoredCredentials()
            publish(LocalSession.SignedOut)
        }
    }

    override suspend fun requireReauthentication(userId: String) {
        sessionMutex.withLock {
            val current = (mutableSession.value as? LocalSession.Authenticated)?.credentials ?: return
            if (current.userId != userId) return
            // 저장소를 비우기 전에 먼저 알린다. 비운 뒤 DataStore 구독이 SignedOut을 먼저 내면
            // AuthGate가 앱 화면을 새로 만들어 보던 화면(백스택)이 사라진다.
            publish(LocalSession.ReauthenticationRequired(userId))
            clearStoredCredentials()
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
        val userId = this[userIdKey]?.takeIf(String::isNotBlank) ?: return null
        val accessToken = this[encryptedAccessTokenKey]
            ?.let(tokenCipher::decrypt)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val refreshToken = this[encryptedRefreshTokenKey]
            ?.let(tokenCipher::decrypt)
            ?.takeIf(String::isNotBlank)
            ?: return null
        val expiresAt = this[expiresAtEpochSecondsKey] ?: return null
        val refreshTokenExpiresAt = this[refreshTokenExpiresAtEpochSecondsKey] ?: return null
        if (refreshTokenExpiresAt <= currentEpochSeconds()) return null

        return LocalSession.Authenticated(
            SessionCredentials(
                userId = userId,
                accessToken = accessToken,
                expiresAtEpochSeconds = expiresAt,
                refreshToken = refreshToken,
                refreshTokenExpiresAtEpochSeconds = refreshTokenExpiresAt,
            ),
        )
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
