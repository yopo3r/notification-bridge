/**
 * Persistent app configuration, backed by Jetpack DataStore (Preferences). Everything the user
 * configures in the Settings screen - the paired receiver, whether the bridge is active, which
 * apps are allowed, the notification filters, the call-forwarding toggle - lives here as a
 * single [Flow] of [app.notificationbridge.model.BridgeSettings], read once per notification by
 * [app.notificationbridge.queue.BridgeRuntime] and observed continuously by the UI.
 *
 * Why DataStore over SharedPreferences: it exposes settings as a cold [Flow], which is what
 * both the always-running listener/worker and the Compose UI need, without manual listener
 * plumbing.
 *
 * Assumptions: settings changes take effect on the *next* notification/transfer, not
 * retroactively on ones already queued - acceptable since settings changes are rare compared to
 * notification volume.
 */
package app.notificationbridge.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.notificationbridge.config.ConfigSnapshot
import app.notificationbridge.format.NotificationFormatter
import app.notificationbridge.model.BridgeSettings
import app.notificationbridge.model.PairedDevice
import app.notificationbridge.model.TestSampleKind
import app.notificationbridge.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("bridge_settings")

class SettingsRepository(context: Context) {

    // This repository is application-scoped; retaining the application context is safe.
    private val appContext = context.applicationContext

    companion object {
        const val MIN_TEXT_CHARS = 100
        const val MAX_TEXT_CHARS = 5000
        const val MIN_BATCHING_COOLDOWN_SECONDS = 5
        const val MAX_BATCHING_COOLDOWN_SECONDS = 60
        const val DEFAULT_BATCHING_COOLDOWN_SECONDS = 15
        const val MIN_AUTO_CLEAR_HOURS = 1
        const val MAX_AUTO_CLEAR_HOURS = 168
        const val DEFAULT_AUTO_CLEAR_HOURS = 24
    }

    private object K {
        val address = stringPreferencesKey("device_address")
        val name = stringPreferencesKey("device_name")
        val enabled = booleanPreferencesKey("bridge_enabled")
        val autoConnect = booleanPreferencesKey("auto_connect")
        val autoReconnect = booleanPreferencesKey("auto_reconnect")
        val ignoreSilent = booleanPreferencesKey("ignore_silent")
        val ignoreOngoing = booleanPreferencesKey("ignore_ongoing")
        val ignoreUpdates = booleanPreferencesKey("ignore_updates")
        val notifyOnCalls = booleanPreferencesKey("notify_on_calls")
        val themeMode = stringPreferencesKey("theme_mode")
        val customThemeSource = stringPreferencesKey("custom_theme_source")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val dumbphoneMode = booleanPreferencesKey("dumbphone_mode")
        val maxTextChars = intPreferencesKey("max_text_chars")
        val batchingEnabled = booleanPreferencesKey("batching_enabled")
        val batchingCooldownSeconds = intPreferencesKey("batching_cooldown_seconds")
        val testSampleKind = stringPreferencesKey("test_sample_kind")
        val autoClearEnabled = booleanPreferencesKey("auto_clear_enabled")
        val autoClearHours = intPreferencesKey("auto_clear_hours")
        val packages = stringSetPreferencesKey("allowed_packages")
    }

    /** Defaults live here: a key that was never written reads as its default, never as absent. */
    val settings: Flow<BridgeSettings> = appContext.dataStore.data.map { p ->
        BridgeSettings(
            selectedAddress = p[K.address],
            selectedName = p[K.name],
            bridgeEnabled = p[K.enabled] ?: false,
            autoConnect = p[K.autoConnect] ?: true,
            autoReconnect = p[K.autoReconnect] ?: true,
            ignoreSilent = p[K.ignoreSilent] ?: true,
            ignoreOngoing = p[K.ignoreOngoing] ?: true,
            ignoreUpdates = p[K.ignoreUpdates] ?: true,
            notifyOnCalls = p[K.notifyOnCalls] ?: true,
            themeMode = runCatching { ThemeMode.valueOf(p[K.themeMode] ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM),
            customThemeSource = p[K.customThemeSource],
            onboardingCompleted = p[K.onboardingCompleted] ?: false,
            dumbphoneMode = p[K.dumbphoneMode] ?: false,
            maxTextChars = (p[K.maxTextChars] ?: NotificationFormatter.DEFAULT_MAX_TEXT_CHARS)
                .coerceIn(MIN_TEXT_CHARS, MAX_TEXT_CHARS),
            batchingEnabled = p[K.batchingEnabled] ?: false,
            batchingCooldownSeconds = (p[K.batchingCooldownSeconds] ?: DEFAULT_BATCHING_COOLDOWN_SECONDS)
                .coerceIn(MIN_BATCHING_COOLDOWN_SECONDS, MAX_BATCHING_COOLDOWN_SECONDS),
            testSampleKind = runCatching { TestSampleKind.valueOf(p[K.testSampleKind] ?: "SHORT") }
                .getOrDefault(TestSampleKind.SHORT),
            autoClearEnabled = p[K.autoClearEnabled] ?: true,
            autoClearHours = (p[K.autoClearHours] ?: DEFAULT_AUTO_CLEAR_HOURS)
                .coerceIn(MIN_AUTO_CLEAR_HOURS, MAX_AUTO_CLEAR_HOURS),
            allowedPackages = p[K.packages] ?: emptySet()
        )
    }

    suspend fun selectDevice(address: String, name: String) {
        appContext.dataStore.edit { it[K.address] = address; it[K.name] = name }
    }

    suspend fun setBridge(v: Boolean) = edit(K.enabled, v)
    suspend fun setAutoConnect(v: Boolean) = edit(K.autoConnect, v)
    suspend fun setAutoReconnect(v: Boolean) = edit(K.autoReconnect, v)
    suspend fun setIgnoreSilent(v: Boolean) = edit(K.ignoreSilent, v)
    suspend fun setIgnoreOngoing(v: Boolean) = edit(K.ignoreOngoing, v)
    suspend fun setIgnoreUpdates(v: Boolean) = edit(K.ignoreUpdates, v)
    suspend fun setNotifyOnCalls(v: Boolean) = edit(K.notifyOnCalls, v)
    suspend fun setOnboardingCompleted(v: Boolean) = edit(K.onboardingCompleted, v)
    suspend fun setDumbphoneMode(v: Boolean) = edit(K.dumbphoneMode, v)

    suspend fun setMaxTextChars(v: Int) {
        appContext.dataStore.edit { it[K.maxTextChars] = v.coerceIn(MIN_TEXT_CHARS, MAX_TEXT_CHARS) }
    }

    suspend fun setBatchingEnabled(v: Boolean) = edit(K.batchingEnabled, v)

    suspend fun setBatchingCooldownSeconds(v: Int) {
        appContext.dataStore.edit {
            it[K.batchingCooldownSeconds] = v.coerceIn(MIN_BATCHING_COOLDOWN_SECONDS, MAX_BATCHING_COOLDOWN_SECONDS)
        }
    }

    suspend fun setThemeMode(v: ThemeMode) {
        appContext.dataStore.edit { it[K.themeMode] = v.name }
    }

    suspend fun setCustomThemeSource(source: String?) {
        appContext.dataStore.edit { preferences ->
            if (source == null) preferences.remove(K.customThemeSource)
            else preferences[K.customThemeSource] = source
        }
    }

    suspend fun setTestSampleKind(v: TestSampleKind) {
        appContext.dataStore.edit { it[K.testSampleKind] = v.name }
    }

    suspend fun setAutoClearEnabled(v: Boolean) = edit(K.autoClearEnabled, v)

    suspend fun setAutoClearHours(v: Int) {
        appContext.dataStore.edit { it[K.autoClearHours] = v.coerceIn(MIN_AUTO_CLEAR_HOURS, MAX_AUTO_CLEAR_HOURS) }
    }

    suspend fun setAllowedPackages(v: Set<String>) {
        appContext.dataStore.edit { it[K.packages] = v }
    }

    /**
     * Applies an imported configuration in a single DataStore transaction, so a reader never
     * sees half of it. Deliberately leaves the bridge switch, onboarding state, auto-reconnect,
     * auto-clear and the Test sample alone (see [app.notificationbridge.config.ConfigFile]).
     * A `null` [receiver] keeps the current selection; a non-null one replaces it.
     */
    suspend fun applyConfig(config: ConfigSnapshot, receiver: PairedDevice?) {
        appContext.dataStore.edit { p ->
            p[K.packages] = config.allowedPackages
            p[K.ignoreSilent] = config.ignoreSilent
            p[K.ignoreOngoing] = config.ignoreOngoing
            p[K.ignoreUpdates] = config.ignoreUpdates
            p[K.notifyOnCalls] = config.notifyOnCalls
            p[K.batchingEnabled] = config.batchingEnabled
            p[K.batchingCooldownSeconds] = config.batchingCooldownSeconds
                .coerceIn(MIN_BATCHING_COOLDOWN_SECONDS, MAX_BATCHING_COOLDOWN_SECONDS)
            p[K.maxTextChars] = config.maxTextChars.coerceIn(MIN_TEXT_CHARS, MAX_TEXT_CHARS)
            p[K.themeMode] = config.themeMode.name
            val theme = config.customThemeSource
            if (theme == null) p.remove(K.customThemeSource) else p[K.customThemeSource] = theme
            p[K.dumbphoneMode] = config.dumbphoneMode
            if (receiver != null) {
                p[K.address] = receiver.address
                p[K.name] = receiver.name
            }
        }
    }

    /**
     * Erases every stored option so each one reads as its default again (see [settings]): the
     * receiver, bridge switch, allowed apps, filters, batching, text length, theme and custom
     * theme, dumbphone mode, auto-clear and the Test sample. Only the "onboarding completed"
     * flag is kept, so a reset does not send the user back through the first-run tutorial.
     * One DataStore transaction, so a reader never sees a half-reset state.
     */
    suspend fun resetToDefaults() {
        appContext.dataStore.edit { p ->
            val onboarding = p[K.onboardingCompleted]
            p.clear()
            if (onboarding != null) p[K.onboardingCompleted] = onboarding
        }
    }

    private suspend fun edit(key: Preferences.Key<Boolean>, value: Boolean) {
        appContext.dataStore.edit { it[key] = value }
    }
}
