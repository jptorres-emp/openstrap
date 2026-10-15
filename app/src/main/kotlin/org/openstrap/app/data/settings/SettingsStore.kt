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

/** The user's own Zepp credentials, used only for firmware update checks. Stored on this phone only. */
data class ZeppAccount(val appToken: String, val userId: String, val apiBase: String, val china: Boolean) {
    companion object {
        const val DEFAULT_API_BASE = "https://api-mifit.zepp.com/"
    }
}

/** What the user told us about themselves; every field is optional. Used only by on-phone calculations. */
data class UserProfile(
    val birthYear: Int? = null,
    val sex: Sex? = null,
    val weightKg: Float? = null,
    val heightCm: Int? = null,
    /** Show pounds and feet/inches; null follows the phone's region. Values are always stored metric. */
    val imperial: Boolean? = null,
) {
    enum class Sex { Female, Male }

    val usesImperial: Boolean
        get() = imperial ?: (java.util.Locale.getDefault().country in IMPERIAL_REGIONS)

    companion object {
        /** Regions that use pounds and feet day to day. */
        val IMPERIAL_REGIONS = setOf("US", "LR", "MM")
        const val KG_PER_LB = 0.45359237f
        const val CM_PER_INCH = 2.54f
    }

    fun age(today: java.time.LocalDate = java.time.LocalDate.now()): Int? = birthYear?.let { today.year - it }
    val isEmpty get() = birthYear == null && sex == null && weightKg == null && heightCm == null
}

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

    private val zeppToken = stringPreferencesKey("zepp_app_token")
    private val zeppUser = stringPreferencesKey("zepp_user_id")
    private val zeppApi = stringPreferencesKey("zepp_api_base")
    private val zeppChina = androidx.datastore.preferences.core.booleanPreferencesKey("zepp_china")

    val zeppAccount: Flow<ZeppAccount?> = context.dataStore.data.map { prefs ->
        val t = prefs[zeppToken] ?: return@map null
        ZeppAccount(t, prefs[zeppUser].orEmpty(), prefs[zeppApi] ?: ZeppAccount.DEFAULT_API_BASE, prefs[zeppChina] == true)
    }

    suspend fun saveZeppAccount(account: ZeppAccount) {
        context.dataStore.edit {
            it[zeppToken] = account.appToken
            it[zeppUser] = account.userId
            it[zeppApi] = account.apiBase
            it[zeppChina] = account.china
        }
    }

    suspend fun clearZeppAccount() {
        context.dataStore.edit {
            it.remove(zeppToken); it.remove(zeppUser); it.remove(zeppApi); it.remove(zeppChina)
        }
    }

    private val liveHr = androidx.datastore.preferences.core.booleanPreferencesKey("live_hr")

    /** Whether live heart rate starts automatically on connect. On by default. */
    val liveHeartRate: Flow<Boolean> = context.dataStore.data.map { it[liveHr] ?: true }

    suspend fun setLiveHeartRate(enabled: Boolean) {
        context.dataStore.edit { it[liveHr] = enabled }
    }

    private val birthYear = androidx.datastore.preferences.core.intPreferencesKey("profile_birth_year")
    private val sex = stringPreferencesKey("profile_sex")
    private val weight = androidx.datastore.preferences.core.floatPreferencesKey("profile_weight_kg")
    private val height = androidx.datastore.preferences.core.intPreferencesKey("profile_height_cm")
    private val imperial = androidx.datastore.preferences.core.booleanPreferencesKey("profile_imperial")

    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            prefs[birthYear],
            prefs[sex]?.let { v -> UserProfile.Sex.entries.firstOrNull { it.name == v } },
            prefs[weight],
            prefs[height],
            prefs[imperial],
        )
    }

    suspend fun saveProfile(p: UserProfile) {
        context.dataStore.edit {
            if (p.birthYear != null) it[birthYear] = p.birthYear else it.remove(birthYear)
            if (p.sex != null) it[sex] = p.sex.name else it.remove(sex)
            if (p.weightKg != null) it[weight] = p.weightKg else it.remove(weight)
            if (p.heightCm != null) it[height] = p.heightCm else it.remove(height)
            if (p.imperial != null) it[imperial] = p.imperial else it.remove(imperial)
        }
    }

    /** Forgets the strap and the Zepp sign-in; the user's profile stays. */
    suspend fun forgetDevice() {
        context.dataStore.edit { prefs ->
            val keep = setOf(birthYear, sex, weight, height, imperial)
            prefs.asMap().keys.filter { it !in keep }.forEach { prefs.remove(it) }
        }
    }

    /** Last synced timestamp (epoch seconds) for a history data type. */
    suspend fun lastSync(type: String): Long? =
        context.dataStore.data.first()[longPreferencesKey("last_sync_$type")]

    suspend fun setLastSync(type: String, epochSeconds: Long) {
        context.dataStore.edit { it[longPreferencesKey("last_sync_$type")] = epochSeconds }
    }
}
