package home.missommunluuri

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PrefsManager(private val context: Context) {

    companion object {
        val DEVICE_TOKEN = stringPreferencesKey("device_token")
        val DEVICE_SLUG = stringPreferencesKey("device_slug")
        val WAKE_ENABLED = booleanPreferencesKey("wake_enabled")
        val RINGTONE_URI = stringPreferencesKey("ringtone_uri")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val SNOOZE_MINUTES = intPreferencesKey("snooze_minutes")
        val LAST_ACCEPTED_TRIGGER = longPreferencesKey("last_accepted_trigger")
    }

    val deviceToken: Flow<String?> = context.dataStore.data.map { it[DEVICE_TOKEN] }
    val deviceSlug: Flow<String?> = context.dataStore.data.map { it[DEVICE_SLUG] }
    val wakeEnabled: Flow<Boolean> = context.dataStore.data.map { it[WAKE_ENABLED] ?: false }
    val ringtoneUri: Flow<String?> = context.dataStore.data.map { it[RINGTONE_URI] }
    val vibrationEnabled: Flow<Boolean> = context.dataStore.data.map { it[VIBRATION_ENABLED] ?: true }
    val snoozeMinutes: Flow<Int> = context.dataStore.data.map { it[SNOOZE_MINUTES] ?: 5 }
    val lastAcceptedTrigger: Flow<Long> = context.dataStore.data.map { it[LAST_ACCEPTED_TRIGGER] ?: 0L }

    suspend fun setDeviceToken(token: String) {
        context.dataStore.edit { it[DEVICE_TOKEN] = token }
    }

    suspend fun setDeviceSlug(slug: String) {
        context.dataStore.edit { it[DEVICE_SLUG] = slug }
    }

    suspend fun setWakeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[WAKE_ENABLED] = enabled }
    }

    suspend fun setRingtoneUri(uri: String) {
        context.dataStore.edit { it[RINGTONE_URI] = uri }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { it[VIBRATION_ENABLED] = enabled }
    }

    suspend fun setSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[SNOOZE_MINUTES] = minutes }
    }

    suspend fun setLastAcceptedTrigger(timestamp: Long) {
        context.dataStore.edit { it[LAST_ACCEPTED_TRIGGER] = timestamp }
    }
}
