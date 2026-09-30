package com.algoprep.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.algoprep.app.domain.model.ReminderKind
import com.algoprep.app.domain.model.ReminderSettings
import com.algoprep.app.domain.model.ReminderSlot
import com.algoprep.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    private fun enabledKey(kind: ReminderKind) = booleanPreferencesKey("reminder_${kind.name.lowercase()}_enabled")
    private fun minuteKey(kind: ReminderKind) = intPreferencesKey("reminder_${kind.name.lowercase()}_minute_of_day")

    override fun observeReminders(): Flow<ReminderSettings> = store.data.map { prefs ->
        var result = ReminderSettings.DEFAULT
        for (kind in ReminderKind.entries) {
            val default = result[kind]
            val slot = ReminderSlot(
                enabled = prefs[enabledKey(kind)] ?: default.enabled,
                time = prefs[minuteKey(kind)]?.let { LocalTime.of(it / 60, it % 60) } ?: default.time,
            )
            result = result.withSlot(kind, slot)
        }
        result
    }

    override suspend fun saveReminders(settings: ReminderSettings) {
        store.edit { prefs ->
            for (kind in ReminderKind.entries) {
                val slot = settings[kind]
                prefs[enabledKey(kind)] = slot.enabled
                prefs[minuteKey(kind)] = slot.time.hour * 60 + slot.time.minute
            }
        }
    }
}
