package com.stonefive.chalkak.data.local.feed

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.stonefive.chalkak.domain.repository.SignatureHintRepository

private val Context.signatureHintDataStore by preferencesDataStore(name = "signature_hint")
private val exposedKey = booleanPreferencesKey("author_posts_hint_exposed")

class SignatureHintStore(context: Context) : SignatureHintRepository {
    private val dataStore = context.applicationContext.signatureHintDataStore

    override suspend fun claimFirstExposure(): Boolean {
        var claimed = false
        dataStore.edit { preferences ->
            if (preferences[exposedKey] != true) {
                preferences[exposedKey] = true
                claimed = true
            }
        }
        return claimed
    }
}
