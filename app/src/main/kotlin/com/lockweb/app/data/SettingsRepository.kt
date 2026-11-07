package com.lockweb.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "lockweb_settings")

/**
 * Repository for user preferences and the permanent adult content blocker flag.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        val KEY_ADULT_BLOCKLIST_ENABLED = booleanPreferencesKey("adult_blocklist_enabled")
        val KEY_PRIMARY_DNS = stringPreferencesKey("primary_dns")
        val KEY_FALLBACK_DNS = stringPreferencesKey("fallback_dns")
        val KEY_APP_THEME = stringPreferencesKey("app_theme")
        val KEY_AUTO_LOCK_TIMEOUT = intPreferencesKey("auto_lock_timeout")
        val KEY_LAST_UPDATE_TIME = longPreferencesKey("last_blocklist_update_time")
        val KEY_LAST_UPDATE_COUNT = intPreferencesKey("last_blocklist_domain_count")

        const val DEFAULT_PRIMARY_DNS = "1.1.1.1"
        const val DEFAULT_FALLBACK_DNS = "1.0.0.1"
        const val DEFAULT_APP_THEME = "SYSTEM"
        const val DEFAULT_AUTO_LOCK_TIMEOUT = 0 // Immediate lock
    }

    /**
     * Observable stream indicating whether the adult content blocklist is permanently active.
     */
    val isAdultBlocklistEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_ADULT_BLOCKLIST_ENABLED] ?: false
    }.distinctUntilChanged()

    /**
     * PERMANENTLY enables the adult blocklist.
     *
     * In accordance with LockWeb's self-control mandate:
     * ONCE ENABLED, NO CODE PATH IN THIS REPOSITORY OR APP EVER SETS THIS FLAG TO FALSE.
     * There is no disable method, toggle-off, or reset for this flag.
     */
    suspend fun enableAdultBlocklistPermanently() {
        dataStore.edit { prefs ->
            prefs[KEY_ADULT_BLOCKLIST_ENABLED] = true
        }
    }

    val primaryDns: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_PRIMARY_DNS] ?: DEFAULT_PRIMARY_DNS
    }.distinctUntilChanged()

    val fallbackDns: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_FALLBACK_DNS] ?: DEFAULT_FALLBACK_DNS
    }.distinctUntilChanged()

    val appTheme: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_APP_THEME] ?: DEFAULT_APP_THEME
    }.distinctUntilChanged()

    val autoLockTimeoutSeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_AUTO_LOCK_TIMEOUT] ?: DEFAULT_AUTO_LOCK_TIMEOUT
    }.distinctUntilChanged()

    val lastBlocklistUpdateTime: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_UPDATE_TIME] ?: 0L
    }.distinctUntilChanged()

    val lastBlocklistDomainCount: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_UPDATE_COUNT] ?: 0
    }.distinctUntilChanged()

    suspend fun setUpstreamDns(primary: String, fallback: String) {
        dataStore.edit { prefs ->
            prefs[KEY_PRIMARY_DNS] = primary.trim()
            prefs[KEY_FALLBACK_DNS] = fallback.trim()
        }
    }

    suspend fun setAppTheme(theme: String) {
        dataStore.edit { prefs ->
            prefs[KEY_APP_THEME] = theme
        }
    }

    suspend fun setAutoLockTimeout(seconds: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_AUTO_LOCK_TIMEOUT] = seconds
        }
    }

    suspend fun recordBlocklistUpdate(timestamp: Long, domainCount: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_UPDATE_TIME] = timestamp
            prefs[KEY_LAST_UPDATE_COUNT] = domainCount
        }
    }
}
