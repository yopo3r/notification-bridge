package app.notificationbridge

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.notificationbridge.config.ConfigFile
import app.notificationbridge.config.ConfigSnapshot
import app.notificationbridge.config.ReceiverExport
import app.notificationbridge.diagnostics.DiagnosticsEnvironment
import app.notificationbridge.diagnostics.DiagnosticsReport
import app.notificationbridge.model.BridgeSettings
import app.notificationbridge.model.BridgeUiState
import app.notificationbridge.model.PairedDevice
import app.notificationbridge.model.TestSampleKind
import app.notificationbridge.model.TransferRecord
import app.notificationbridge.model.ThemeMode
import app.notificationbridge.queue.BridgeRuntime
import app.notificationbridge.readiness.ReadinessChecklist
import app.notificationbridge.readiness.ReadinessFacts
import app.notificationbridge.ui.theme.CustomThemeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppChoice(val label: String, val packageName: String)
/**
 * What an import did, for the confirmation message.
 *
 * @property receiverName The receiver named in the file, if any.
 * @property receiverSelected Whether that receiver is now the selected one. `false` with a name
 *   means it could not be matched to a paired device (not paired yet, ambiguous name, or no
 *   Bluetooth permission to look), and the previous selection was kept.
 */
data class ImportOutcome(val appCount: Int, val receiverName: String?, val receiverSelected: Boolean)

/** What the History screen needs from the runtime: the rows, and whether notifications can arrive. */
data class HistoryRuntimeState(
    val history: List<TransferRecord>,
    val notificationAccess: Boolean,
    val listenerConnected: Boolean
)

data class HomeRuntimeState(val notificationAccess: Boolean, val queueCount: Int)

/**
 * Thin adapter between the Compose UI and the two real sources of truth:
 * [app.notificationbridge.data.SettingsRepository] (persisted config) and
 * [app.notificationbridge.queue.BridgeRuntime] (live bridge state). It holds no bridging logic
 * of its own - every setter here just forwards to the repository, and [state] is
 * `BridgeRuntime.state` directly.
 *
 * [settings] is `null` until DataStore has delivered its first value, which lets the UI avoid
 * acting on defaults (see [BridgeApp]). [paired] and [apps] are the two lists that require an
 * Android API call to populate (bonded Bluetooth devices, installed launcher apps) and are
 * refreshed on demand via [refresh] rather than kept as continuously-observed flows, since they
 * rarely change during a session. The app list is loaded off the main thread because resolving
 * every launcher label is slow on devices with many apps.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as BridgeApplication).settings

    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val state = BridgeRuntime.state
    val homeState = state.map { HomeRuntimeState(it.notificationAccess, it.queueCount) }
        .distinctUntilChanged()
    val historyState = state.map { HistoryRuntimeState(it.history, it.notificationAccess, it.listenerConnected) }
        .distinctUntilChanged()

    /** Bumped by [refresh] so the platform-derived readiness facts are re-read. */
    private val factsTick = MutableStateFlow(0)
    private val readinessFacts = combine(
        repo.settings.map { it.selectedAddress }.distinctUntilChanged(),
        factsTick
    ) { address, _ -> address }
        .map(::readReadinessFacts)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReadinessFacts())

    /** Rows of the Home readiness checklist; see [ReadinessChecklist]. */
    val readiness = combine(repo.settings, state, readinessFacts, ReadinessChecklist::build)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ReadinessChecklist.build(BridgeSettings(), BridgeUiState(), ReadinessFacts())
        )

    val paired = MutableStateFlow<List<PairedDevice>>(emptyList())

    /** A validated imported file waiting for the user's confirmation; survives rotation. */
    val pendingConfig = MutableStateFlow<ConfigSnapshot?>(null)
    val apps = MutableStateFlow<List<AppChoice>>(emptyList())

    private var devicesRequested = false
    private var appsRequested = false

    init {
        refresh()
    }

    /** Re-reads notification access and platform state; also called whenever the app resumes. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { BridgeRuntime.refreshAccess() }
        factsTick.update { it + 1 }
        if (devicesRequested) loadPairedDevices(force = true)
    }

    /** Heavy platform lists are fetched only when their screen is first opened. */
    fun loadPairedDevices(force: Boolean = false) {
        if (devicesRequested && !force) return
        devicesRequested = true
        val canReadBonded = canReadBondedDevices()
        viewModelScope.launch(Dispatchers.IO) {
            paired.value = if (canReadBonded) {
                runCatching { BridgeRuntime.pairedDevices() }
                    .onFailure { BridgeRuntime.log("Paired devices error: ${it.message}") }
                    .getOrDefault(emptyList())
            } else emptyList()
        }
    }

    private fun canReadBondedDevices(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(
                getApplication<Application>(), Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

    fun loadApps() {
        if (appsRequested) return
        appsRequested = true
        loadLaunchableAppsAsync()
    }

    private fun loadLaunchableAppsAsync() {
        viewModelScope.launch(Dispatchers.IO) { apps.value = loadLaunchableApps() }
    }

    private fun loadLaunchableApps(): List<AppChoice> {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) queryApi33(pm, intent) else queryLegacy(pm, intent)
        return resolved.asSequence()
            .filter { it.activityInfo?.packageName != context.packageName }
            // A package may expose more than one launcher activity. Keep its first label and
            // avoid the extra package-manager label lookup for duplicate activities.
            .distinctBy { it.activityInfo?.packageName }
            .mapNotNull { r ->
                r.activityInfo?.packageName?.let { AppChoice(r.loadLabel(pm).toString(), it) }
            }
            // This app never forwards its own notifications, so it isn't offered in the list.
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    @RequiresApi(33)
    private fun queryApi33(pm: PackageManager, intent: Intent) =
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))

    @Suppress("DEPRECATION")
    private fun queryLegacy(pm: PackageManager, intent: Intent) =
        pm.queryIntentActivities(intent, 0)

    fun selectDevice(d: PairedDevice) = viewModelScope.launch { repo.selectDevice(d.address, d.name) }

    fun test(context: Context, d: PairedDevice, kind: TestSampleKind, done: (Result<Unit>) -> Unit) {
        selectDevice(d)
        BridgeRuntime.testPush(context, d.address, kind, done)
    }

    fun togglePackage(packageName: String, allowed: Boolean) = viewModelScope.launch {
        val current = settings.value?.allowedPackages ?: emptySet()
        repo.setAllowedPackages(if (allowed) current + packageName else current - packageName)
    }

    fun setBridge(v: Boolean) = viewModelScope.launch { repo.setBridge(v) }
    fun setAutoConnect(v: Boolean) = viewModelScope.launch { repo.setAutoConnect(v) }
    fun setAutoReconnect(v: Boolean) = viewModelScope.launch { repo.setAutoReconnect(v) }
    fun setIgnoreSilent(v: Boolean) = viewModelScope.launch { repo.setIgnoreSilent(v) }
    fun setIgnoreOngoing(v: Boolean) = viewModelScope.launch { repo.setIgnoreOngoing(v) }
    fun setIgnoreUpdates(v: Boolean) = viewModelScope.launch { repo.setIgnoreUpdates(v) }
    fun setNotifyOnCalls(v: Boolean) = viewModelScope.launch { repo.setNotifyOnCalls(v) }
    fun setThemeMode(v: ThemeMode) = viewModelScope.launch { repo.setThemeMode(v) }
    fun clearCustomTheme() = viewModelScope.launch { repo.setCustomThemeSource(null) }

    fun importCustomTheme(uri: Uri, done: (Result<String>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val source = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Could not open theme file")
                    val theme = CustomThemeParser.parse(source)
                    repo.setCustomThemeSource(source)
                    theme.name
                }
            }
            done(result)
        }
    }

    /**
     * Writes the current configuration to [uri]. [language] is the file value for the app's
     * current language (see [ConfigFile.languageValue]); it lives in the platform's per-app
     * locale rather than in the settings store, so the caller supplies it.
     */
    fun exportConfig(uri: Uri, receiver: ReceiverExport, language: String, done: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val text = ConfigFile.export(repo.settings.first(), language, receiver, BuildConfig.VERSION_NAME)
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                        ?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                        ?: error("Could not open the file for writing")
                }
            }
            done(result)
        }
    }

    /** Reads and validates a configuration file without changing anything. */
    fun readConfig(uri: Uri, done: (Result<ConfigSnapshot>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val reader = getApplication<Application>().contentResolver
                        .openInputStream(uri)?.bufferedReader(Charsets.UTF_8)
                        ?: error("Could not open the file")
                    // Stop one character past the limit so a huge file is rejected, not loaded.
                    val text = reader.use {
                        val out = StringBuilder()
                        val buffer = CharArray(4096)
                        while (out.length <= ConfigFile.MAX_FILE_CHARS) {
                            val n = it.read(buffer)
                            if (n < 0) break
                            out.append(buffer, 0, n)
                        }
                        out.toString()
                    }
                    ConfigFile.parse(text)
                }
            }
            done(result)
        }
    }

    /**
     * Applies an already validated [config]. The receiver is taken from the file's address when
     * it has one; otherwise a paired device whose name matches exactly (ignoring case) is chosen,
     * but only if that match is unique. Anything else keeps the current receiver.
     */
    fun applyConfig(config: ConfigSnapshot, done: (Result<ImportOutcome>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val receiver = resolveReceiver(config)
                    repo.applyConfig(config, receiver)
                    ImportOutcome(config.allowedPackages.size, config.receiverName, receiver != null)
                }
            }
            if (result.isSuccess) refresh()
            done(result)
        }
    }

    private fun resolveReceiver(config: ConfigSnapshot): PairedDevice? {
        val name = config.receiverName ?: return null
        config.receiverAddress?.let { return PairedDevice(name, it) }
        if (!canReadBondedDevices()) return null
        val matches = runCatching { BridgeRuntime.pairedDevices() }.getOrDefault(emptyList())
            .filter { it.name.equals(name, ignoreCase = true) }
        return matches.singleOrNull()
    }

    /** Restores every option to its default (see [SettingsRepository.resetToDefaults]). */
    fun restoreDefaults(done: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { repo.resetToDefaults() } }
            if (result.isSuccess) refresh()
            done(result)
        }
    }

    fun setOnboardingCompleted(v: Boolean) = viewModelScope.launch { repo.setOnboardingCompleted(v) }
    fun setDumbphoneMode(v: Boolean) = viewModelScope.launch { repo.setDumbphoneMode(v) }
    fun setMaxTextChars(v: Int) = viewModelScope.launch { repo.setMaxTextChars(v) }
    fun setBatchingEnabled(v: Boolean) = viewModelScope.launch { repo.setBatchingEnabled(v) }
    fun setBatchingCooldownSeconds(v: Int) = viewModelScope.launch { repo.setBatchingCooldownSeconds(v) }
    fun setTestSampleKind(v: TestSampleKind) = viewModelScope.launch { repo.setTestSampleKind(v) }
    fun setAutoClearEnabled(v: Boolean) = viewModelScope.launch { repo.setAutoClearEnabled(v) }
    fun setAutoClearHours(v: Int) = viewModelScope.launch { repo.setAutoClearHours(v) }

    fun clearHistory() = BridgeRuntime.clearHistory()

    /** "Retry now": resends a recently failed notification. `false` if it is no longer held. */
    fun retry(recordId: Long): Boolean = BridgeRuntime.retry(recordId)

    /** Sends the shortest sample straight to the selected receiver, bypassing queue and filters. */
    fun compatibilityTest(context: Context, address: String, done: (Result<Unit>) -> Unit) =
        BridgeRuntime.testPush(context, address, TestSampleKind.SHORT, done)

    /**
     * Gathers the platform facts (Android version, Bluetooth state) and delegates the actual text
     * to [DiagnosticsReport], which decides what is safe to include. Every Bluetooth read is
     * wrapped: a missing permission simply yields "unknown" in the report instead of crashing.
     */
    @SuppressLint("MissingPermission")
    fun buildDiagnosticsReport(s: BridgeSettings, u: BridgeUiState): String {
        val facts = readReadinessFacts(s.selectedAddress)
        return DiagnosticsReport.build(
            env = DiagnosticsEnvironment(
                appVersion = BuildConfig.VERSION_NAME,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            ),
            settings = s,
            state = u,
            bluetoothEnabled = facts.bluetoothEnabled,
            receiverPaired = facts.receiverPaired
        )
    }

    /**
     * Reads the facts the runtime state doesn't carry. Every read is wrapped: a missing
     * permission (reading a bond state needs BLUETOOTH_CONNECT on API 31+) yields `null`
     * ("unknown") instead of crashing or guessing.
     */
    @SuppressLint("MissingPermission")
    private fun readReadinessFacts(selectedAddress: String?): ReadinessFacts {
        val context = getApplication<Application>()
        val adapter = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        }.getOrNull()
        val bluetoothEnabled = runCatching { adapter?.isEnabled }.getOrNull()
        val receiverPaired = selectedAddress?.let { address ->
            runCatching {
                adapter?.getRemoteDevice(address)?.let { it.bondState == BluetoothDevice.BOND_BONDED }
            }.getOrNull()
        }
        val batteryIgnored = runCatching {
            context.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrNull()
        return ReadinessFacts(bluetoothEnabled, receiverPaired, batteryIgnored)
    }
}
