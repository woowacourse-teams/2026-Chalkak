package com.stonefive.chalkak.data.local.notification

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stonefive.chalkak.domain.repository.LikePushMigrationRepository
import kotlinx.coroutines.flow.first

private val Context.likePushMigrationDataStore by preferencesDataStore(name = "like_push_migration")
private val completedUserIdsKey = stringSetPreferencesKey("completed_user_ids")

class LikePushMigrationStore(context: Context) : LikePushMigrationRepository {
    private val dataStore = context.applicationContext.likePushMigrationDataStore

    override suspend fun isCompleted(userId: String): Boolean {
        val preferences = dataStore.data.first()
        return userId in preferences[completedUserIdsKey].orEmpty()
    }

    override suspend fun complete(userId: String) {
        dataStore.edit { preferences ->
            preferences[completedUserIdsKey] = preferences[completedUserIdsKey].orEmpty() + userId
        }
    }
}
