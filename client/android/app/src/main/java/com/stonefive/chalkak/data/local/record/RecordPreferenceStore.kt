package com.stonefive.chalkak.data.local.record

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stonefive.chalkak.domain.repository.RecordPreferenceRepository
import kotlinx.coroutines.flow.first

private val Context.recordPreferenceDataStore by preferencesDataStore(name = "record_preferences")
private val photoStackStyleKey = stringPreferencesKey("photo_stack_style")

class RecordPreferenceStore(context: Context) : RecordPreferenceRepository {
    private val dataStore = context.applicationContext.recordPreferenceDataStore

    override suspend fun getPhotoStackStyle(): String? = dataStore.data.first()[photoStackStyleKey]

    override suspend fun setPhotoStackStyle(name: String) {
        dataStore.edit { preferences -> preferences[photoStackStyleKey] = name }
    }
}
