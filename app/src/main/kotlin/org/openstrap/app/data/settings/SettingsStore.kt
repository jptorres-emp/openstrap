package org.openstrap.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class PairedDevice(val address: String, val authKeyHex: String, val name: String)

class SettingsStore(private val context: Context) {
    private val address = stringPreferencesKey("device_address")
    private val authKey = stringPreferencesKey("device_auth_key")
    private val name = stringPreferencesKey("device_name")

    val pairedDevice: Flow<PairedDevice?> = context.dataStore.data.map { prefs ->
        val a = prefs[address] ?: return@map null
        val k = prefs[authKey] ?: return@map null
        PairedDevice(a, k, prefs[name] ?: "Helio Strap")
    }

    suspend fun savePairedDevice(device: PairedDevice) {
        context.dataStore.edit {
            it[address] = device.address
            it[authKey] = device.authKeyHex
            it[name] = device.name
        }
    }

    suspend fun forgetDevice() {
        context.dataStore.edit { it.clear() }
    }

    /** Last synced timestamp (epoch seconds) for a history data type. */
    suspend fun lastSync(type: String): Long? =
        context.dataStore.data.first()[longPreferencesKey("last_sync_$type")]

    suspend fun setLastSync(type: String, epochSeconds: Long) {
        context.dataStore.edit { it[longPreferencesKey("last_sync_$type")] = epochSeconds }
    }
}
