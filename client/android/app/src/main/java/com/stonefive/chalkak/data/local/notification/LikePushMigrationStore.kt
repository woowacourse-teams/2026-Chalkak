package com.stonefive.chalkak.data.local.notification

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stonefive.chalkak.domain.repository.LikePushMigrationRepository
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.likePushMigrationDataStore by preferencesDataStore(name = "like_push_migration")
private val completedUserIdsKey = stringSetPreferencesKey("completed_user_ids")

class LikePushMigrationStore(context: Context) : LikePushMigrationRepository {
    private val dataStore = context.applicationContext.likePushMigrationDataStore

    override suspend fun isCompleted(userId: String): Boolean {
        val preferences = dataStore.data
            .catch { error ->
                if (error is IOException) emit(emptyPreferences()) else throw error
            }.first()
        return userId in preferences[completedUserIdsKey].orEmpty()
    }

    override suspend fun complete(userId: String) {
        try {
            dataStore.edit { preferences ->
                preferences[completedUserIdsKey] = preferences[completedUserIdsKey].orEmpty() + userId
            }
        } catch (_: IOException) {
            // 완료를 기록하지 못하면 다음 실행에서 다시 시도한다.
        }
    }
}
