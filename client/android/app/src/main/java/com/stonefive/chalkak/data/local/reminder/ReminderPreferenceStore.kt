package com.stonefive.chalkak.data.local.reminder

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.stonefive.chalkak.domain.model.ReminderPreference
import com.stonefive.chalkak.domain.repository.ReminderPreferenceRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

private val Context.reminderDataStore by preferencesDataStore(name = "reminder_preference")
private val modeKey = stringPreferencesKey("mode")
private val hourKey = intPreferencesKey("hour")
private val minuteKey = intPreferencesKey("minute")

class ReminderPreferenceStore(
    context: Context,
    scope: CoroutineScope,
    private val alarmScheduler: AndroidReminderAlarmScheduler,
) : ReminderPreferenceRepository {
    private val dataStore = context.reminderDataStore
    private val mutablePreference = MutableStateFlow<ReminderPreference>(ReminderPreference.Loading)

    override val preference: StateFlow<ReminderPreference> = mutablePreference.asStateFlow()

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
                    mutablePreference.value = when (preferences[modeKey]) {
                        MODE_DISABLED -> ReminderPreference.Disabled

                        MODE_ENABLED -> {
                            val hour = preferences[hourKey]
                            val minute = preferences[minuteKey]
                            if (hour != null && minute != null) {
                                ReminderPreference.Enabled(hour, minute)
                            } else {
                                ReminderPreference.Unconfigured
                            }
                        }

                        else -> ReminderPreference.Unconfigured
                    }
                }
        }
    }

    override suspend fun enable(
        hour: Int,
        minute: Int,
    ) {
        require(hour in 0..23)
        require(minute in 0..59)
        dataStore.edit { preferences ->
            preferences[modeKey] = MODE_ENABLED
            preferences[hourKey] = hour
            preferences[minuteKey] = minute
        }
        alarmScheduler.schedule(hour, minute)
    }

    override suspend fun disable() {
        dataStore.edit { preferences ->
            preferences[modeKey] = MODE_DISABLED
            preferences.remove(hourKey)
            preferences.remove(minuteKey)
        }
        alarmScheduler.cancel()
    }
}

private const val MODE_ENABLED = "enabled"
private const val MODE_DISABLED = "disabled"
