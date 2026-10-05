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
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.notificationbridge.diagnostics.DiagnosticsEnvironment
import app.notificationbridge.diagnostics.DiagnosticsReport
import app.notificationbridge.model.BridgeSettings
import app.notificationbridge.model.BridgeUiState
import app.notificationbridge.model.PairedDevice
import app.notificationbridge.model.TestSampleKind
import app.notificationbridge.model.ThemeMode
import app.notificationbridge.queue.BridgeRuntime
import app.notificationbridge.ui.theme.CustomThemeParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppChoice(val label: String, val packageName: String)
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
    val history = state.map { it.history }.distinctUntilChanged()
    val paired = MutableStateFlow<List<PairedDevice>>(emptyList())
    val apps = MutableStateFlow<List<AppChoice>>(emptyList())

    private var devicesRequested = false
    private var appsRequested = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { BridgeRuntime.refreshAccess() }
        if (devicesRequested) loadPairedDevices(force = true)
    }

    /** Heavy platform lists are fetched only when their screen is first opened. */
    fun loadPairedDevices(force: Boolean = false) {
        if (devicesRequested && !force) return
        devicesRequested = true
        val context = getApplication<Application>()
        val canReadBonded = Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        viewModelScope.launch(Dispatchers.IO) {
            paired.value = if (canReadBonded) {
                runCatching { BridgeRuntime.pairedDevices() }
                    .onFailure { BridgeRuntime.log("Paired devices error: ${it.message}") }
                    .getOrDefault(emptyList())
            } else emptyList()
        }
    }

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
    fun setOnboardingCompleted(v: Boolean) = viewModelScope.launch { repo.setOnboardingCompleted(v) }
    fun setDumbphoneMode(v: Boolean) = viewModelScope.launch { repo.setDumbphoneMode(v) }
    fun setMaxTextChars(v: Int) = viewModelScope.launch { repo.setMaxTextChars(v) }
    fun setBatchingEnabled(v: Boolean) = viewModelScope.launch { repo.setBatchingEnabled(v) }
    fun setBatchingCooldownSeconds(v: Int) = viewModelScope.launch { repo.setBatchingCooldownSeconds(v) }
    fun setTestSampleKind(v: TestSampleKind) = viewModelScope.launch { repo.setTestSampleKind(v) }
    fun setAutoClearEnabled(v: Boolean) = viewModelScope.launch { repo.setAutoClearEnabled(v) }
    fun setAutoClearHours(v: Int) = viewModelScope.launch { repo.setAutoClearHours(v) }

    fun clearHistory() = BridgeRuntime.clearHistory()

    /**
     * Gathers the platform facts (Android version, Bluetooth state) and delegates the actual text
     * to [DiagnosticsReport], which decides what is safe to include. Every Bluetooth read is
     * wrapped: a missing permission simply yields "unknown" in the report instead of crashing.
     */
    @SuppressLint("MissingPermission")
    fun buildDiagnosticsReport(s: BridgeSettings, u: BridgeUiState): String {
        val adapter = runCatching {
            getApplication<Application>().getSystemService(BluetoothManager::class.java)?.adapter
        }.getOrNull()
        val bluetoothEnabled = runCatching { adapter?.isEnabled }.getOrNull()
        val receiverPaired = s.selectedAddress?.let { address ->
            runCatching {
                adapter?.getRemoteDevice(address)?.let { it.bondState == BluetoothDevice.BOND_BONDED }
            }.getOrNull()
        }
        return DiagnosticsReport.build(
            env = DiagnosticsEnvironment(
                appVersion = BuildConfig.VERSION_NAME,
                androidRelease = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}"
            ),
            settings = s,
            state = u,
            bluetoothEnabled = bluetoothEnabled,
            receiverPaired = receiverPaired
        )
    }
}
