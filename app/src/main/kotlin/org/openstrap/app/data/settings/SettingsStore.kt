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

/** What the user told us about themselves; every field is optional. Used by on-phone calculations and sent to the strap. */
data class UserProfile(
    val birthYear: Int? = null,
    val sex: Sex? = null,
    val weightKg: Float? = null,
    val heightCm: Int? = null,
    /** Show pounds and feet/inches; null follows the phone's region. Values are always stored metric. */
    val imperial: Boolean? = null,
    /** The user's own sleep need, 420-540 min; null uses the default for their age. */
    val sleepNeedMinutes: Int? = null,
    /** A maximum heart rate from a test; overrides the age estimate. */
    val maxHeartRate: Int? = null,
    /** Usual physical activity, level 1-5 as index 0-4 (Jurca 2005's question), and when it was last answered. */
    val activityLevel: Int? = null,
    val activityAnsweredAt: Long? = null,
    /** When weight and height were last confirmed (epoch seconds); the VO2max estimate wants them under 90 days old. */
    val bodyMeasuredAt: Long? = null,
    /** Resting heart rate doesn't reflect fitness on beta-blockers or in pregnancy, so VO2max is withheld. */
    val betaBlocker: Boolean = false,
    val pregnant: Boolean = false,
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

/** Notification buzz patterns: alternating vibrate and pause milliseconds for the strap's motor. */
enum class BuzzPattern(val label: String, val millis: List<Int>) {
    ONE_SHORT("One short", listOf(200)),
    TWO_SHORT("Two short", listOf(150, 150, 150)),
    THREE_SHORT("Three short", listOf(120, 120, 120, 120, 120)),
    ONE_LONG("One long", listOf(600)),
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
    private val sleepNeed = androidx.datastore.preferences.core.intPreferencesKey("profile_sleep_need_minutes")
    private val maxHr = androidx.datastore.preferences.core.intPreferencesKey("profile_max_hr")
    private val activityLevel = androidx.datastore.preferences.core.intPreferencesKey("profile_activity_level")
    private val activityAt = longPreferencesKey("profile_activity_answered_at")
    private val bodyAt = longPreferencesKey("profile_body_measured_at")
    private val betaBlocker = androidx.datastore.preferences.core.booleanPreferencesKey("profile_beta_blocker")
    private val pregnant = androidx.datastore.preferences.core.booleanPreferencesKey("profile_pregnant")

    val profile: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            prefs[birthYear],
            prefs[sex]?.let { v -> UserProfile.Sex.entries.firstOrNull { it.name == v } },
            prefs[weight],
            prefs[height],
            prefs[imperial],
            prefs[sleepNeed],
            prefs[maxHr],
            prefs[activityLevel],
            prefs[activityAt],
            prefs[bodyAt],
            prefs[betaBlocker] == true,
            prefs[pregnant] == true,
        )
    }

    suspend fun saveProfile(p: UserProfile) {
        context.dataStore.edit {
            if (p.birthYear != null) it[birthYear] = p.birthYear else it.remove(birthYear)
            if (p.sex != null) it[sex] = p.sex.name else it.remove(sex)
            if (p.weightKg != null) it[weight] = p.weightKg else it.remove(weight)
            if (p.heightCm != null) it[height] = p.heightCm else it.remove(height)
            if (p.imperial != null) it[imperial] = p.imperial else it.remove(imperial)
            if (p.sleepNeedMinutes != null) it[sleepNeed] = p.sleepNeedMinutes else it.remove(sleepNeed)
            if (p.maxHeartRate != null) it[maxHr] = p.maxHeartRate else it.remove(maxHr)
            if (p.activityLevel != null) it[activityLevel] = p.activityLevel else it.remove(activityLevel)
            if (p.activityAnsweredAt != null) it[activityAt] = p.activityAnsweredAt else it.remove(activityAt)
            if (p.bodyMeasuredAt != null) it[bodyAt] = p.bodyMeasuredAt else it.remove(bodyAt)
            it[betaBlocker] = p.betaBlocker
            it[pregnant] = p.pregnant
        }
    }

    private val stepGoal = androidx.datastore.preferences.core.intPreferencesKey("goal_steps")
    private val sleepGoal = androidx.datastore.preferences.core.intPreferencesKey("goal_sleep_minutes")
    private val activeGoal = androidx.datastore.preferences.core.intPreferencesKey("goal_active_hours")

    val goals: Flow<Goals> = context.dataStore.data.map { prefs ->
        Goals(
            prefs[stepGoal] ?: Goals.DEFAULT_STEPS,
            prefs[sleepGoal] ?: Goals.DEFAULT_SLEEP_MINUTES,
            prefs[activeGoal] ?: Goals.DEFAULT_ACTIVE_HOURS,
        )
    }

    suspend fun saveGoals(g: Goals) {
        context.dataStore.edit {
            it[stepGoal] = g.steps
            it[sleepGoal] = g.sleepMinutes
            it[activeGoal] = g.activeHours
        }
    }

    private val todayTiles = stringPreferencesKey("today_tiles")

    /** The metrics Today's What changed shows, by name; null until the user picks (then it shows them all). */
    val todayTileNames: Flow<List<String>?> = context.dataStore.data.map { prefs ->
        prefs[todayTiles]?.split(',')?.filter { it.isNotEmpty() }
    }

    suspend fun saveTodayTiles(names: List<String>) {
        context.dataStore.edit { it[todayTiles] = names.joinToString(",") }
    }

    /** Forgets the strap and the Zepp sign-in; the user's profile and goals stay. */
    suspend fun forgetDevice() {
        context.dataStore.edit { prefs ->
            val keep = setOf(birthYear, sex, weight, height, imperial, sleepNeed, maxHr, activityLevel, activityAt, bodyAt, betaBlocker, pregnant, stepGoal, sleepGoal, activeGoal, todayTiles, buzzCalls, buzzNotifications, buzzPattern)
            prefs.asMap().keys.filter { it !in keep }.forEach { prefs.remove(it) }
        }
    }

    private val buzzCalls = androidx.datastore.preferences.core.booleanPreferencesKey("buzz_calls")
    private val buzzNotifications = androidx.datastore.preferences.core.booleanPreferencesKey("buzz_notifications")

    /** Buzz the strap while a phone call rings. Off by default; it needs the phone-state permission. */
    val buzzOnCalls: Flow<Boolean> = context.dataStore.data.map { it[buzzCalls] == true }

    /** Buzz the strap once for each app notification that would make a sound. Off by default; it needs notification access. */
    val buzzOnNotifications: Flow<Boolean> = context.dataStore.data.map { it[buzzNotifications] == true }

    private val buzzPattern = stringPreferencesKey("buzz_pattern")

    /** How the strap buzzes for a notification; two short buzzes by default. */
    val notificationPattern: Flow<BuzzPattern> = context.dataStore.data.map { prefs ->
        BuzzPattern.entries.firstOrNull { it.name == prefs[buzzPattern] } ?: BuzzPattern.TWO_SHORT
    }

    suspend fun setNotificationPattern(pattern: BuzzPattern) {
        context.dataStore.edit { it[buzzPattern] = pattern.name }
    }

    suspend fun setBuzzOnCalls(enabled: Boolean) {
        context.dataStore.edit { it[buzzCalls] = enabled }
    }

    suspend fun setBuzzOnNotifications(enabled: Boolean) {
        context.dataStore.edit { it[buzzNotifications] = enabled }
    }

    /** Last synced timestamp (epoch seconds) for a history data type. */
    suspend fun lastSync(type: String): Long? =
        context.dataStore.data.first()[longPreferencesKey("last_sync_$type")]

    suspend fun setLastSync(type: String, epochSeconds: Long) {
        context.dataStore.edit { it[longPreferencesKey("last_sync_$type")] = epochSeconds }
    }
}
