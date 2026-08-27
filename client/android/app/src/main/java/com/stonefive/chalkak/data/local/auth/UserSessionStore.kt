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
                    mutableSessionState.value = preferences[USER_ID]
                        ?.let(UserSessionState::Authenticated)
                        ?: UserSessionState.SignedOut
                }
        }
    }

    override suspend fun continueAsGuest() {
        mutableSessionState.value = UserSessionState.Guest
    }

    override suspend fun saveUserId(userId: String) {
        dataStore.edit { preferences -> preferences[USER_ID] = userId }
        mutableSessionState.value = UserSessionState.Authenticated(userId)
    }

    override suspend fun clear() {
        dataStore.edit { preferences -> preferences.remove(USER_ID) }
        mutableSessionState.value = UserSessionState.SignedOut
    }

    private companion object {
        val USER_ID = stringPreferencesKey("user_id")
    }
}
