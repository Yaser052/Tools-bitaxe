package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AnalysisProfile
import com.example.domain.model.AppLanguage
import com.example.domain.model.SafetyThresholdConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bitaxe_ai_settings")

data class AppSettings(
    val lastConnectedIp: String = "192.168.1.100",
    val savedIps: List<String> = listOf("192.168.1.100"),
    val language: AppLanguage = AppLanguage.EN,
    val pollingIntervalSeconds: Int = 4,
    val autoReconnect: Boolean = true,
    val defaultProfile: AnalysisProfile = AnalysisProfile.BALANCED,
    val safetyThresholds: SafetyThresholdConfig = SafetyThresholdConfig()
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val LAST_IP = stringPreferencesKey("last_connected_ip")
        val SAVED_IPS = stringPreferencesKey("saved_ips_csv")
        val LANGUAGE = stringPreferencesKey("app_language")
        val POLLING_SECONDS = intPreferencesKey("polling_interval_seconds")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val DEFAULT_PROFILE = stringPreferencesKey("default_profile")
        val MAX_ASIC_TEMP = doublePreferencesKey("max_asic_temp_c")
        val MAX_VR_TEMP = doublePreferencesKey("max_vr_temp_c")
        val MAX_ERROR_PCT = doublePreferencesKey("max_asic_error_pct")
        val MIN_OBS_SECONDS = longPreferencesKey("min_observation_seconds")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val langCode = prefs[Keys.LANGUAGE] ?: AppLanguage.EN.code
        val language = AppLanguage.entries.firstOrNull { it.code == langCode } ?: AppLanguage.EN
        val profileName = prefs[Keys.DEFAULT_PROFILE] ?: AnalysisProfile.BALANCED.name
        val profile = AnalysisProfile.entries.firstOrNull { it.name == profileName } ?: AnalysisProfile.BALANCED
        val lastIp = prefs[Keys.LAST_IP] ?: "192.168.1.100"
        val savedCsv = prefs[Keys.SAVED_IPS] ?: lastIp
        val savedList = savedCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val polling = (prefs[Keys.POLLING_SECONDS] ?: 4).coerceIn(3, 5)

        val maxAsicHard = prefs[Keys.MAX_ASIC_TEMP] ?: 70.0
        val maxVrHard = prefs[Keys.MAX_VR_TEMP] ?: 85.0
        val maxErr = prefs[Keys.MAX_ERROR_PCT] ?: 3.0
        val minObs = prefs[Keys.MIN_OBS_SECONDS] ?: 60L

        AppSettings(
            lastConnectedIp = lastIp,
            savedIps = if (savedList.isEmpty()) listOf(lastIp) else savedList,
            language = language,
            pollingIntervalSeconds = polling,
            autoReconnect = prefs[Keys.AUTO_RECONNECT] ?: true,
            defaultProfile = profile,
            safetyThresholds = SafetyThresholdConfig(
                maxAsicTempWarnC = (maxAsicHard - 5.0).coerceAtLeast(50.0),
                maxAsicTempHardLimitC = maxAsicHard,
                maxVrTempWarnC = (maxVrHard - 7.0).coerceAtLeast(60.0),
                maxVrTempHardLimitC = maxVrHard,
                maxAsicErrorPct = maxErr,
                minObservationSeconds = minObs
            )
        )
    }

    suspend fun saveConnectedIp(ip: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_IP] = ip
            val existing = (prefs[Keys.SAVED_IPS] ?: "")
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val updated = (listOf(ip) + existing).distinct().take(8)
            prefs[Keys.SAVED_IPS] = updated.joinToString(",")
        }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.code
        }
    }

    suspend fun setPollingIntervalSeconds(seconds: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.POLLING_SECONDS] = seconds.coerceIn(3, 5)
        }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_RECONNECT] = enabled
        }
    }

    suspend fun setDefaultProfile(profile: AnalysisProfile) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_PROFILE] = profile.name
        }
    }

    suspend fun updateSafetyThresholds(
        maxAsicTempC: Double,
        maxVrTempC: Double,
        maxErrorPct: Double,
        minObservationSeconds: Long
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MAX_ASIC_TEMP] = maxAsicTempC.coerceIn(55.0, 80.0)
            prefs[Keys.MAX_VR_TEMP] = maxVrTempC.coerceIn(65.0, 95.0)
            prefs[Keys.MAX_ERROR_PCT] = maxErrorPct.coerceIn(0.5, 10.0)
            prefs[Keys.MIN_OBS_SECONDS] = minObservationSeconds.coerceIn(30L, 600L)
        }
    }
}
