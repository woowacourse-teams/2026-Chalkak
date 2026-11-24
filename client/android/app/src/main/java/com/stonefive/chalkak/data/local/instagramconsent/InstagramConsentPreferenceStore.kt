package com.stonefive.chalkak.data.local.instagramconsent

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stonefive.chalkak.domain.model.InstagramConsent
import com.stonefive.chalkak.domain.repository.InstagramConsentPreferenceRepository
import kotlinx.coroutines.flow.first

private val Context.instagramConsentDataStore by preferencesDataStore(name = "instagram_consent_preferences")

class InstagramConsentPreferenceStore internal constructor(private val dataStore: DataStore<Preferences>) :
    InstagramConsentPreferenceRepository {
    constructor(context: Context) : this(context.applicationContext.instagramConsentDataStore)

    override suspend fun get(userId: String): InstagramConsent? {
        val preferences = dataStore.data.first()
        if (preferences[withdrawnKey(userId)] == true) return null
        val agreed = preferences[agreedKey(userId)] ?: return null
        return InstagramConsent(agreed, preferences[idKey(userId)].takeIf { agreed })
    }

    override suspend fun save(
        userId: String,
        consent: InstagramConsent,
    ) {
        dataStore.edit { preferences ->
            if (preferences[withdrawnKey(userId)] != true) {
                preferences[agreedKey(userId)] = consent.agreed
                val id = consent.instagramId?.takeIf { consent.agreed && it.isNotBlank() }
                if (id == null) preferences.remove(idKey(userId)) else preferences[idKey(userId)] = id
            }
        }
    }

    override suspend fun clear(userId: String) {
        dataStore.edit { preferences ->
            preferences.remove(agreedKey(userId))
            preferences.remove(idKey(userId))
            preferences[withdrawnKey(userId)] = true
        }
    }

    private fun agreedKey(userId: String) = booleanPreferencesKey("agreed:$userId")

    private fun idKey(userId: String) = stringPreferencesKey("id:$userId")

    private fun withdrawnKey(userId: String) = booleanPreferencesKey("withdrawn:$userId")
}
