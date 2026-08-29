package com.stonefive.chalkak.data.local.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stonefive.chalkak.domain.model.UserSessionState
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private val Context.authDataStore by preferencesDataStore(name = "auth_session")

class UserSessionStore(
    context: Context,
    scope: CoroutineScope,
) : SessionStore {
    private val dataStore = context.authDataStore
    private val mutableSessionState = MutableStateFlow<UserSessionState>(UserSessionState.Loading)
    private val mutableAccessToken = MutableStateFlow<String?>(null)

    override val sessionState: StateFlow<UserSessionState> = mutableSessionState.asStateFlow()
    override val accessToken: String?
        get() = mutableAccessToken.value

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
                    val userId = preferences[USER_ID]
                    val accessToken = preferences[ACCESS_TOKEN]
                    mutableAccessToken.value = accessToken
                    mutableSessionState.value = if (!userId.isNullOrBlank() && !accessToken.isNullOrBlank()) {
                        UserSessionState.Authenticated(userId)
                    } else {
                        UserSessionState.SignedOut
                    }
                }
        }
    }

    override suspend fun continueAsGuest() {
        mutableSessionState.value = UserSessionState.Guest
    }

    override suspend fun saveUserId(userId: String) {
        dataStore.edit { preferences ->
            preferences[USER_ID] = userId
            preferences.remove(ACCESS_TOKEN)
        }
        mutableAccessToken.value = null
        mutableSessionState.value = UserSessionState.SignedOut
    }

    override suspend fun saveSession(
        userId: String,
        accessToken: String,
    ) {
        dataStore.edit { preferences ->
            preferences[USER_ID] = userId
            preferences[ACCESS_TOKEN] = accessToken
        }
        mutableAccessToken.value = accessToken
        mutableSessionState.value = UserSessionState.Authenticated(userId)
    }

    override suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.remove(USER_ID)
            preferences.remove(ACCESS_TOKEN)
        }
        mutableAccessToken.value = null
        mutableSessionState.value = UserSessionState.SignedOut
    }

    private companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
    }
}
