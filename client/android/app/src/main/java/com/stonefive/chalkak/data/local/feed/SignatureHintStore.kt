package com.stonefive.chalkak.data.local.feed

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.stonefive.chalkak.domain.repository.SignatureHintRepository
import kotlinx.coroutines.flow.first

private val Context.signatureHintDataStore by preferencesDataStore(name = "signature_hint")
private val completedKey = booleanPreferencesKey("author_posts_hint_completed")

class SignatureHintStore(context: Context) : SignatureHintRepository {
    private val dataStore = context.applicationContext.signatureHintDataStore

    override suspend fun isCompleted(): Boolean = dataStore.data.first()[completedKey] == true

    override suspend fun complete() {
        dataStore.edit { preferences -> preferences[completedKey] = true }
    }
}
