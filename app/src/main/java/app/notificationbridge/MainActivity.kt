package app.notificationbridge

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import app.notificationbridge.HomeRuntimeState
import app.notificationbridge.config.ConfigFile
import app.notificationbridge.config.ReceiverExport
import app.notificationbridge.data.SettingsRepository
import app.notificationbridge.model.BridgeSettings
import app.notificationbridge.model.BridgeUiState
import app.notificationbridge.model.FailureAction
import app.notificationbridge.model.PairedDevice
import app.notificationbridge.model.MessageFormat
import app.notificationbridge.model.TestSampleKind
import app.notificationbridge.model.ThemeMode
import app.notificationbridge.queue.FailureClassifier
import app.notificationbridge.readiness.ReadinessChecklist
import app.notificationbridge.readiness.ReadinessEntry
import app.notificationbridge.readiness.ReadinessItem
import app.notificationbridge.readiness.ReadinessStatus
import app.notificationbridge.ui.InsetSurface
import app.notificationbridge.ui.PageHeader
import app.notificationbridge.ui.SectionHeading
import app.notificationbridge.ui.StatusPill
import app.notificationbridge.ui.WarningDialog
import app.notificationbridge.ui.theme.NotificationBridgeTheme
import app.notificationbridge.ui.verticalScrollbar
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Below this width, the section bar is a [ScrollableTabRow] (natural on a phone, where six
 * labels don't fit). At or above it, the bar is a width-filling [TabRow] instead - otherwise a
 * tablet in landscape is left with a half-empty tab bar hugging the leading edge.
 */
private val WIDE_LAYOUT_BREAKPOINT = 600.dp
private const val APP_PAGE_SIZE = 30
private const val BLUETOOTH_DEVICE_PAGE_SIZE = 6

class MainActivity : AppCompatActivity() {

    private val vm by viewModels<MainViewModel>()

    /**
     * Notification access, Bluetooth and battery-optimization state are all changed in system
     * screens, so re-read them whenever the user comes back to the app.
     */
    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsState()
            NotificationBridgeTheme(
                themeMode = settings?.themeMode ?: ThemeMode.SYSTEM,
                customThemeSource = settings?.customThemeSource
            ) {
                val view = LocalView.current
                val window = (view.context as? Activity)?.window
                val secureScreen = settings?.secureScreen ?: false
                SideEffect {
                    // FLAG_SECURE blocks screenshots/screen recording and blanks the Recents thumbnail.
                    if (secureScreen) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                val systemBarColor = MaterialTheme.colorScheme.background
                val isLightSurface = systemBarColor.luminance() > 0.5f
                SideEffect {
                    window?.let {
                        WindowCompat.getInsetsController(it, view).apply {
                            isAppearanceLightStatusBars = isLightSurface
                            isAppearanceLightNavigationBars = isLightSurface
                        }
                    }
                }
                // A Surface is required here: without it, the window keeps its default
                // background (from the native Activity theme) instead of the Material 3
                // color scheme's background/content colors, which is what made dark mode
                // unreadable on screens rendered outside Scaffold (Scaffold supplies this
                // itself, but the loading state and any full-screen content before it does
                // not).
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    BridgeApp(vm)
                }
            }
        }
    }
}

/**
 * Root Compose UI. Six sections, swipeable via [HorizontalPager] and selectable via a tab bar
 * above it (a bottom navigation bar is limited to five destinations), all reading from
 * [MainViewModel]'s [kotlinx.coroutines.flow.StateFlow]s so the UI always reflects the live
 * [app.notificationbridge.queue.BridgeRuntime] state rather than a snapshot taken when the
 * screen opened:
 *
 * - **Inicio (Home)**: at-a-glance status (paired device, notification access, allowed-app
 *   count, queue depth) plus shortcuts to the two things a first-time setup needs.
 * - **Test**: pick a paired device and send a one-off file, independent of the notification
 *   pipeline - the fastest way to confirm Bluetooth/OBEX works before wiring up real
 *   notifications. Which fixed sample it sends is configured in Settings, not here.
 * - **History**: recent transfers with their status ([HistoryScreen]).
 * - **Settings**: the bridge master switch, dumbphone mode, all forwarding filters, theme and
 *   language pickers, and the per-app allow list.
 * - **Diagnostics**: technical status and a privacy-filtered copyable report
 *   ([DiagnosticsScreen]).
 * - **About**: version, purpose and license - a peer screen, not a dialog.
 *
 * The section bar is a [ScrollableTabRow] on narrow screens and a width-filling [TabRow] at or
 * above [WIDE_LAYOUT_BREAKPOINT] (see that constant). The pager and the tab row share one
 * selection, kept in sync in both directions: tapping a tab animates the pager to that page, and
 * swiping the pager updates which tab is highlighted.
 *
 * [MainViewModel.settings] is nullable: `null` means "not loaded from DataStore yet". This
 * function renders nothing until the first real value arrives, specifically so a returning
 * user is never shown a flash of the onboarding screen (which only belongs to
 * `onboardingCompleted == false`) while the persisted value is still in flight.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BridgeApp(vm: MainViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    val devices by vm.paired.collectAsState()
    val apps by vm.apps.collectAsState()
    // Saveable: importing a configuration can change the app language, which recreates the
    // Activity, and the confirmation message must survive that.
    var resultMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var showRestoreDefaultsDialog by rememberSaveable { mutableStateOf(false) }
    // Shown before every trip to the system's notification-access screen: says what is read and where it goes.
    var showAccessDisclosure by rememberSaveable { mutableStateOf(false) }
    var exportReceiver by rememberSaveable { mutableStateOf(ReceiverExport.NAME_ONLY) }
    val pendingConfig by vm.pendingConfig.collectAsState()
    var showOnboardingManually by rememberSaveable { mutableStateOf(false) }

    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val successText = stringResource(R.string.transfer_success)
    val errorTemplate = stringResource(R.string.transfer_error)
    val retryUnavailableText = stringResource(R.string.history_retry_unavailable)
    val customThemeImported = stringResource(R.string.settings_theme_imported)
    val customThemeImportError = stringResource(R.string.settings_theme_import_error)
    val customThemePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.importCustomTheme(uri) { result ->
                resultMessage = result.fold(
                    onSuccess = { customThemeImported.format(it) },
                    onFailure = { customThemeImportError.format(it.message ?: "Unknown error") }
                )
            }
        }
    }

    val configExported = stringResource(R.string.backup_exported)
    val configExportError = stringResource(R.string.backup_export_error)
    val configImported = stringResource(R.string.backup_imported)
    val configImportError = stringResource(R.string.backup_import_error)
    val receiverSelectedText = stringResource(R.string.backup_import_receiver_selected)
    val receiverMissingText = stringResource(R.string.backup_import_receiver_missing)
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val language = ConfigFile.languageValue(AppCompatDelegate.getApplicationLocales().get(0)?.language)
            vm.exportConfig(uri, exportReceiver, language) { result ->
                resultMessage = result.fold(
                    onSuccess = { configExported },
                    onFailure = { configExportError.format(it.message ?: "Unknown error") }
                )
            }
        }
    }
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            vm.readConfig(uri) { result ->
                result.fold(
                    onSuccess = { vm.pendingConfig.value = it },
                    onFailure = { resultMessage = configImportError.format(it.message ?: "Unknown error") }
                )
            }
        }
    }

    val btEnableLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.refresh() }

    fun requestBluetoothEnable() {
        val adapter = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        }.getOrNull()
        val isOff = adapter != null && runCatching { !adapter.isEnabled }.getOrDefault(false)
        if (isOff) btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    /** Some OEM builds lack a given settings screen; a missing one must not crash the app. */
    fun openSystemScreen(action: String) {
        runCatching { context.startActivity(Intent(action)) }
    }

    // "Grant permission" from a failed row. If the system will not ask again, nothing visible
    // would happen, so send the user to the app's permission page instead.
    val grantLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        vm.refresh()
        val refused = results.filterKeys { it.startsWith("android.permission.BLUETOOTH") }.values.any { !it }
        if (refused) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refresh(); requestBluetoothEnable() }

    fun requestPermissionsAndBluetooth() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 31) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (perms.isNotEmpty()) permissionLauncher.launch(perms.toTypedArray())
        else { vm.refresh(); requestBluetoothEnable() }
    }

    val tabLabels = listOf(
        stringResource(R.string.tab_home),
        stringResource(R.string.tab_test),
        stringResource(R.string.tab_history),
        stringResource(R.string.tab_settings),
        stringResource(R.string.tab_diagnostics),
        stringResource(R.string.tab_about)
    )

    // First run: the tutorial replaces the whole UI, and finishing it lands on Home (the pager
    // below is created fresh at page 0). A tutorial reopened from Settings is instead drawn as
    // an overlay on top of the live UI (see the end of the Scaffold), so the user comes back to
    // exactly the section, scroll position and expanded groups they left.
    if (!s.onboardingCompleted) {
        OnboardingScreen(onFinished = { vm.setOnboardingCompleted(true) })
        return
    }

    val pagerState = rememberPagerState(initialPage = 0) { tabLabels.size }
    val coroutineScope = rememberCoroutineScope()
    fun goToTab(index: Int) {
        coroutineScope.launch { pagerState.animateScrollToPage(index) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_name)) },
                        actions = { LinksMenu() }
                    )
                    BoxWithConstraints {
                        val tabRowContent: @Composable () -> Unit = {
                            tabLabels.forEachIndexed { index, label ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = { goToTab(index) },
                                    text = {
                                        Text(
                                            label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                )
                            }
                        }
                        if (maxWidth >= WIDE_LAYOUT_BREAKPOINT) {
                            PrimaryTabRow(selectedTabIndex = pagerState.currentPage, tabs = tabRowContent)
                        } else {
                            PrimaryScrollableTabRow(
                                selectedTabIndex = pagerState.currentPage,
                                edgePadding = 8.dp,
                                tabs = tabRowContent
                            )
                        }
                    }
                }
            }
        ) { padding ->
            HorizontalPager(state = pagerState, modifier = Modifier.padding(padding)) { page ->
                Box(Modifier.padding(16.dp)) {
                    when (page) {
                        0 -> Home(
                            s = s,
                            u = vm.homeState.collectAsState(initial = HomeRuntimeState(false, 0)).value,
                            readiness = vm.readiness.collectAsState().value,
                            onTest = { goToTab(1) },
                            onNotificationAccess = { showAccessDisclosure = true },
                            onSettings = { goToTab(3) },
                            onReadinessAction = { item ->
                                when (item) {
                                    ReadinessItem.NOTIFICATION_ACCESS -> showAccessDisclosure = true
                                    // Already granted: the fix is toggling it off and on, no disclosure needed.
                                    ReadinessItem.LISTENER_CONNECTED ->
                                        openSystemScreen(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                    ReadinessItem.BLUETOOTH_ENABLED -> requestPermissionsAndBluetooth()
                                    ReadinessItem.RECEIVER_SELECTED,
                                    ReadinessItem.RECEIVER_PAIRED,
                                    ReadinessItem.LAST_TRANSFER -> goToTab(1)
                                    ReadinessItem.BRIDGE_ENABLED -> goToTab(3)
                                    ReadinessItem.BATTERY_OPTIMIZATION ->
                                        openSystemScreen(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                }
                            }
                        )
                        1 -> TestScreen(
                            devices = devices,
                            selected = s.selectedAddress,
                            onLoadDevices = vm::loadPairedDevices,
                            onSelect = vm::selectDevice,
                            onTest = { device ->
                                vm.test(context, device, s.testSampleKind) { result ->
                                    resultMessage = result.fold(
                                        onSuccess = { successText },
                                        onFailure = { e ->
                                            errorTemplate.format(context.getString(FailureClassifier.classify(e).explanationRes()))
                                        }
                                    )
                                }
                            },
                            onRequestPermission = ::requestPermissionsAndBluetooth
                        )
                        2 -> HistoryScreen(
                            state = vm.historyState.collectAsState(initial = HistoryRuntimeState(emptyList(), false, false)).value,
                            bridgeEnabled = s.bridgeEnabled,
                            hasReceiver = s.selectedAddress != null,
                            onClear = vm::clearHistory,
                            onAction = { action, record ->
                                when (action) {
                                    FailureAction.OPEN_BLUETOOTH_SETTINGS -> openSystemScreen(Settings.ACTION_BLUETOOTH_SETTINGS)
                                    FailureAction.GRANT_PERMISSION ->
                                        if (Build.VERSION.SDK_INT >= 31) {
                                            grantLauncher.launch(
                                                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                                            )
                                        } else vm.refresh()
                                    FailureAction.TURN_ON_BLUETOOTH -> requestPermissionsAndBluetooth()
                                    FailureAction.CHOOSE_RECEIVER -> goToTab(1)
                                    FailureAction.SEND_COMPATIBILITY_TEST -> {
                                        val address = s.selectedAddress
                                        if (address == null) goToTab(1)
                                        else vm.compatibilityTest(context, address) { result ->
                                            resultMessage = result.fold(
                                                onSuccess = { successText },
                                                onFailure = { e ->
                                                    errorTemplate.format(
                                                        context.getString(FailureClassifier.classify(e).explanationRes())
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    FailureAction.RETRY_NOW ->
                                        if (record == null || !vm.retry(record.id)) resultMessage = retryUnavailableText
                                    FailureAction.OPEN_DIAGNOSTICS -> goToTab(4)
                                    FailureAction.OPEN_NOTIFICATION_ACCESS -> showAccessDisclosure = true
                                }
                            }
                        )
                        3 -> SettingsScreen(
                            s = s,
                            apps = apps,
                            vm = vm,
                            onLoadApps = vm::loadApps,
                            onShowTutorial = { showOnboardingManually = true },
                            onExportConfig = { showExportDialog = true },
                            onRestoreDefaults = { showRestoreDefaultsDialog = true },
                            onImportConfig = {
                                importPicker.launch(arrayOf("text/plain", "application/octet-stream"))
                            },
                            onImportCustomTheme = {
                                customThemePicker.launch(arrayOf("text/plain", "application/octet-stream"))
                            },
                            onResetCustomTheme = vm::clearCustomTheme
                        )
                        4 -> {
                            val diagnosticsState = vm.state.collectAsState(initial = BridgeUiState()).value
                            val report = remember(diagnosticsState, s) { vm.buildDiagnosticsReport(s, diagnosticsState) }
                            DiagnosticsScreen(report = report)
                        }
                        else -> AboutScreen()
                    }
                }
            }
        }

        if (showOnboardingManually) {
            BackHandler { showOnboardingManually = false }
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                OnboardingScreen(onFinished = { showOnboardingManually = false })
            }
        }
    }

    if (showAccessDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessDisclosure = false },
            title = { Text(stringResource(R.string.access_disclosure_title)) },
            text = {
                Text(
                    stringResource(R.string.access_disclosure_body),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showAccessDisclosure = false
                    openSystemScreen(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                }) { Text(stringResource(R.string.access_disclosure_continue)) }
            },
            dismissButton = {
                TextButton(onClick = { showAccessDisclosure = false }) {
                    Text(stringResource(R.string.access_disclosure_cancel))
                }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.backup_export_title)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.backup_export_receiver_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SettingsRadioOption(
                        exportReceiver == ReceiverExport.NAME_ONLY,
                        stringResource(R.string.backup_export_receiver_name)
                    ) { exportReceiver = ReceiverExport.NAME_ONLY }
                    SettingsRadioOption(
                        exportReceiver == ReceiverExport.NONE,
                        stringResource(R.string.backup_export_receiver_none)
                    ) { exportReceiver = ReceiverExport.NONE }
                    SettingsRadioOption(
                        exportReceiver == ReceiverExport.NAME_AND_ADDRESS,
                        stringResource(R.string.backup_export_receiver_address)
                    ) { exportReceiver = ReceiverExport.NAME_AND_ADDRESS }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportDialog = false
                    exportLauncher.launch("notification-bridge-config.txt")
                }) { Text(stringResource(R.string.backup_export_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    if (showRestoreDefaultsDialog) {
        val restoredText = stringResource(R.string.restore_defaults_done)
        val restoreErrorTemplate = stringResource(R.string.restore_defaults_error)
        WarningDialog(
            title = stringResource(R.string.restore_defaults_title),
            warning = stringResource(R.string.restore_defaults_warning),
            body = stringResource(R.string.restore_defaults_scope),
            confirmText = stringResource(R.string.restore_defaults_confirm),
            dismissText = stringResource(R.string.dialog_cancel),
            onDismiss = { showRestoreDefaultsDialog = false },
            onConfirm = {
                showRestoreDefaultsDialog = false
                vm.restoreDefaults { result ->
                    resultMessage = result.fold(
                        onSuccess = { restoredText },
                        onFailure = { restoreErrorTemplate.format(it.message ?: "Unknown error") }
                    )
                    // Last: a language change recreates the Activity.
                    if (result.isSuccess) {
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                    }
                }
            }
        )
    }

    pendingConfig?.let { config ->
        AlertDialog(
            onDismissRequest = { vm.pendingConfig.value = null },
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.backup_import_body))
                    Text(
                        pluralStringResource(
                            R.plurals.backup_import_apps,
                            config.allowedPackages.size,
                            config.allowedPackages.size
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                    if (config.allowedPackages.isNotEmpty()) {
                        val labels = apps.associate { it.packageName to it.label }
                        val shown = config.allowedPackages.map { labels[it] ?: it }.sorted()
                        Text(
                            shown.take(IMPORT_PREVIEW_APPS).joinToString(", ") +
                                if (shown.size > IMPORT_PREVIEW_APPS) "…" else "",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    config.receiverName?.let {
                        Text(
                            stringResource(R.string.backup_import_receiver, it),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.pendingConfig.value = null
                    vm.applyConfig(config) { result ->
                        result.fold(
                            onSuccess = { outcome ->
                                val lines = mutableListOf(configImported)
                                outcome.receiverName?.let { name ->
                                    lines += (if (outcome.receiverSelected) receiverSelectedText else receiverMissingText)
                                        .format(name)
                                }
                                resultMessage = lines.joinToString("\n\n")
                                // Last: a language change recreates the Activity.
                                val current = ConfigFile.languageValue(
                                    AppCompatDelegate.getApplicationLocales().get(0)?.language
                                )
                                if (config.language != current) {
                                    AppCompatDelegate.setApplicationLocales(
                                        if (config.language == ConfigFile.LANGUAGE_SYSTEM) {
                                            LocaleListCompat.getEmptyLocaleList()
                                        } else {
                                            LocaleListCompat.forLanguageTags(config.language)
                                        }
                                    )
                                }
                            },
                            onFailure = {
                                resultMessage = configImportError.format(it.message ?: "Unknown error")
                            }
                        )
                    }
                }) { Text(stringResource(R.string.backup_import_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.pendingConfig.value = null }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    resultMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            confirmButton = {
                TextButton(onClick = { resultMessage = null }) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
            title = { Text(stringResource(R.string.dialog_result_title)) },
            text = { Text(msg) }
        )
    }
}

@Composable
fun Home(
    s: BridgeSettings,
    u: HomeRuntimeState,
    readiness: List<ReadinessEntry>,
    onTest: () -> Unit,
    onNotificationAccess: () -> Unit,
    onSettings: () -> Unit,
    onReadinessAction: (ReadinessItem) -> Unit
) {
    val scrollState = rememberScrollState()
    val ready = s.bridgeEnabled && s.selectedAddress != null && u.notificationAccess
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScrollbar(scrollState)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        PageHeader(stringResource(R.string.tab_home))
        if (!ready) {
            val nextAction = when {
                !u.notificationAccess -> onNotificationAccess
                s.selectedAddress == null -> onTest
                else -> onSettings
            }
            val nextActionLabel = when {
                !u.notificationAccess -> stringResource(R.string.home_notification_access_button)
                s.selectedAddress == null -> stringResource(R.string.home_action_choose)
                else -> stringResource(R.string.home_settings_button)
            }
            InsetSurface {
                Column(
                    Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatusPill(
                        stringResource(R.string.home_setup_title),
                        MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        stringResource(R.string.home_setup_body),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = nextAction, modifier = Modifier.fillMaxWidth()) {
                        Text(nextActionLabel)
                    }
                    OutlinedButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.home_settings_button))
                    }
                }
            }
        }
        ReadinessPanel(readiness, onReadinessAction)
        InsetSurface {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                OverviewLine(
                    stringResource(R.string.home_bluetooth_label),
                    s.selectedName ?: stringResource(R.string.home_no_device)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                OverviewLine(
                    stringResource(R.string.home_notifications_label),
                    if (u.notificationAccess) stringResource(R.string.home_service_enabled)
                    else stringResource(R.string.home_access_not_granted)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                OverviewLine(
                    stringResource(R.string.home_apps_label),
                    pluralStringResource(
                        R.plurals.home_apps_selected,
                        s.allowedPackages.size,
                        s.allowedPackages.size
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                OverviewLine(
                    stringResource(R.string.home_queue_label),
                    pluralStringResource(R.plurals.home_queue_pending, u.queueCount, u.queueCount)
                )
            }
        }
    }
}

/**
 * Compact "is the bridge ready?" panel: one line per precondition (see [ReadinessChecklist]).
 * A row that needs attention is tappable and jumps to the place that fixes it; meaning is carried
 * by the Yes/No text as well as the dot color, so it doesn't rely on color alone.
 */
@Composable
private fun ReadinessPanel(entries: List<ReadinessEntry>, onAction: (ReadinessItem) -> Unit) {
    val attention = ReadinessChecklist.needsAttention(entries)
    InsetSurface {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.home_readiness_title),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium
                )
                if (attention == 0) {
                    StatusPill(stringResource(R.string.home_readiness_all_set), MaterialTheme.colorScheme.primary)
                } else {
                    StatusPill(
                        pluralStringResource(R.plurals.home_readiness_attention, attention, attention),
                        MaterialTheme.colorScheme.error
                    )
                }
            }
            entries.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                }
                ReadinessRow(entry, onAction)
            }
        }
    }
}

@Composable
private fun ReadinessRow(entry: ReadinessEntry, onAction: (ReadinessItem) -> Unit) {
    val context = LocalContext.current
    val actionable = entry.status == ReadinessStatus.ACTION
    val color = when (entry.status) {
        ReadinessStatus.OK -> MaterialTheme.colorScheme.primary
        ReadinessStatus.ACTION -> MaterialTheme.colorScheme.error
        ReadinessStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val detail = when {
        entry.item == ReadinessItem.LAST_TRANSFER && entry.timestamp != null ->
            remember(entry.timestamp) { formatTransferTime(context, entry.timestamp) }
        else -> stringResource(entry.detailRes())
    }
    val rowModifier = if (actionable) {
        Modifier.clickable(
            role = Role.Button,
            onClickLabel = stringResource(R.string.home_readiness_fix)
        ) { onAction(entry.item) }
    } else {
        Modifier.semantics(mergeDescendants = true) {}
    }
    Row(
        rowModifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(
            stringResource(entry.item.labelRes()),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            textAlign = TextAlign.End,
            textDecoration = if (actionable) TextDecoration.Underline else null
        )
    }
}

private fun ReadinessItem.labelRes() = when (this) {
    ReadinessItem.NOTIFICATION_ACCESS -> R.string.readiness_notification_access
    ReadinessItem.LISTENER_CONNECTED -> R.string.readiness_listener_connected
    ReadinessItem.BLUETOOTH_ENABLED -> R.string.readiness_bluetooth_enabled
    ReadinessItem.RECEIVER_SELECTED -> R.string.readiness_receiver_selected
    ReadinessItem.RECEIVER_PAIRED -> R.string.readiness_receiver_paired
    ReadinessItem.BRIDGE_ENABLED -> R.string.readiness_bridge_enabled
    ReadinessItem.BATTERY_OPTIMIZATION -> R.string.readiness_battery_optimization
    ReadinessItem.LAST_TRANSFER -> R.string.readiness_last_transfer
}

/** The value text for every state except a known last-transfer time (formatted separately). */
private fun ReadinessEntry.detailRes() = when {
    item == ReadinessItem.LAST_TRANSFER -> R.string.readiness_none_yet
    item == ReadinessItem.BATTERY_OPTIMIZATION && status == ReadinessStatus.OK -> R.string.readiness_battery_unrestricted
    item == ReadinessItem.BATTERY_OPTIMIZATION && status == ReadinessStatus.ACTION -> R.string.readiness_battery_optimized
    blockedByReceiver -> R.string.readiness_select_receiver_first
    status == ReadinessStatus.OK -> R.string.readiness_yes
    status == ReadinessStatus.ACTION -> R.string.readiness_no
    else -> R.string.readiness_unknown
}

/** Time of day for today's transfers; abbreviated date and time for older ones. */
private fun formatTransferTime(context: android.content.Context, timestamp: Long): String {
    val dateFlags = if (DateUtils.isToday(timestamp)) 0 else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH
    return DateUtils.formatDateTime(context, timestamp, DateUtils.FORMAT_SHOW_TIME or dateFlags)
}

@Composable
private fun OverviewLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun TestScreen(
    devices: List<PairedDevice>,
    selected: String?,
    onLoadDevices: () -> Unit,
    onSelect: (PairedDevice) -> Unit,
    onTest: (PairedDevice) -> Unit,
    onRequestPermission: () -> Unit
) {
    LaunchedEffect(Unit) { onLoadDevices() }
    val listState = rememberLazyListState()
    var visibleDeviceCount by rememberSaveable(devices.size) { mutableIntStateOf(BLUETOOTH_DEVICE_PAGE_SIZE) }
    val visibleDevices = remember(devices, visibleDeviceCount) { devices.take(visibleDeviceCount) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PageHeader(stringResource(R.string.test_title), subtitle = stringResource(R.string.test_description))
        OutlinedButton(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.test_refresh_devices))
        }
        if (devices.isEmpty()) {
            InsetSurface(Modifier.weight(1f)) {
                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.test_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).verticalScrollbar(listState),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleDevices, key = { it.address }) { device ->
                    val isSelected = device.address == selected
                    InsetSurface {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { onSelect(device) }
                                )
                                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(
                                        device.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        device.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                StatusPill(stringResource(R.string.test_selected), MaterialTheme.colorScheme.primary)
                            }
                            Button(onClick = { onTest(device) }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.test_send_button))
                            }
                        }
                    }
                }
                if (visibleDeviceCount < devices.size) {
                    item(key = "show_more_devices") {
                        OutlinedButton(
                            onClick = { visibleDeviceCount += BLUETOOTH_DEVICE_PAGE_SIZE },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.settings_apps_show_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    s: BridgeSettings,
    apps: List<AppChoice>,
    vm: MainViewModel,
    onLoadApps: () -> Unit,
    onShowTutorial: () -> Unit,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onRestoreDefaults: () -> Unit,
    onImportCustomTheme: () -> Unit,
    onResetCustomTheme: () -> Unit
) {
    LaunchedEffect(Unit) { onLoadApps() }
    val listState = rememberLazyListState()
    var visibleAppCount by rememberSaveable(apps.size) { mutableIntStateOf(APP_PAGE_SIZE) }
    val visibleApps = remember(apps, visibleAppCount) { apps.take(visibleAppCount) }
    // Which sections are open, one bit per SettingsSection. An Int because it survives rotation
    // and language changes via rememberSaveable. Everything starts collapsed.
    var openSections by rememberSaveable { mutableIntStateOf(0) }
    fun isOpen(section: SettingsSection) = openSections and (1 shl section.ordinal) != 0
    fun toggleSection(section: SettingsSection) { openSections = openSections xor (1 shl section.ordinal) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().verticalScrollbar(listState),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "settings_page_header") {
            PageHeader(stringResource(R.string.tab_settings), subtitle = stringResource(R.string.settings_intro))
        }
        settingsSection(SettingsSection.CONNECTION, isOpen(SettingsSection.CONNECTION), ::toggleSection) {
            item(key = "bridge_toggle") {
                Toggle(stringResource(R.string.settings_bridge_enabled), s.bridgeEnabled, vm::setBridge)
            }
            item(key = "reconnect_toggle") {
                Toggle(stringResource(R.string.settings_auto_reconnect), s.autoReconnect, vm::setAutoReconnect)
            }
        }
        settingsSection(SettingsSection.FORWARDING, isOpen(SettingsSection.FORWARDING), ::toggleSection) {
            item(key = "ignore_silent_toggle") {
                Toggle(stringResource(R.string.settings_ignore_silent), s.ignoreSilent, vm::setIgnoreSilent)
            }
            item(key = "ignore_ongoing_toggle") {
                Toggle(stringResource(R.string.settings_ignore_ongoing), s.ignoreOngoing, vm::setIgnoreOngoing)
            }
            item(key = "ignore_duplicates_toggle") {
                Toggle(stringResource(R.string.settings_ignore_duplicates), s.ignoreUpdates, vm::setIgnoreUpdates)
            }
            item(key = "notify_calls_toggle") {
                Toggle(stringResource(R.string.settings_notify_calls), s.notifyOnCalls, vm::setNotifyOnCalls)
            }
            item(key = "message_format_heading") {
                SectionHeading(stringResource(R.string.settings_message_format_title))
            }
            item(key = "message_format_text") {
                SettingsRadioOption(
                    s.messageFormat == MessageFormat.TEXT_FILE,
                    stringResource(R.string.settings_message_format_text)
                ) { vm.setMessageFormat(MessageFormat.TEXT_FILE) }
            }
            item(key = "message_format_vmessage") {
                SettingsRadioOption(
                    s.messageFormat == MessageFormat.VMESSAGE,
                    stringResource(R.string.settings_message_format_vmessage)
                ) { vm.setMessageFormat(MessageFormat.VMESSAGE) }
            }
            item(key = "message_format_note") {
                Text(
                    stringResource(R.string.settings_message_format_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item(key = "text_length_heading") {
                SectionHeading(stringResource(R.string.settings_text_length_title))
            }
            item(key = "text_length_200") {
                SettingsRadioOption(s.maxTextChars == 200, stringResource(R.string.settings_text_length_short)) {
                    vm.setMaxTextChars(200)
                }
            }
            item(key = "text_length_700") {
                SettingsRadioOption(s.maxTextChars == 700, stringResource(R.string.settings_text_length_medium)) {
                    vm.setMaxTextChars(700)
                }
            }
            item(key = "text_length_1500") {
                SettingsRadioOption(s.maxTextChars == 1500, stringResource(R.string.settings_text_length_long)) {
                    vm.setMaxTextChars(1500)
                }
            }
            item(key = "text_length_3000") {
                SettingsRadioOption(s.maxTextChars == 3000, stringResource(R.string.settings_text_length_xlong)) {
                    vm.setMaxTextChars(3000)
                }
            }
            item(key = "allowed_apps_heading") {
                SectionHeading(stringResource(R.string.settings_allowed_apps_title))
            }
            items(visibleApps, key = { it.packageName }) { app ->
                val allowed = app.packageName in s.allowedPackages
                Row(
                    Modifier.fillMaxWidth().toggleable(
                        value = allowed,
                        onValueChange = { vm.togglePackage(app.packageName, it) },
                        role = Role.Checkbox
                    ).defaultMinSize(minHeight = 56.dp).padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Checkbox(
                        checked = allowed,
                        onCheckedChange = null
                    )
                }
            }
            if (visibleAppCount < apps.size) {
                item(key = "apps_show_more") {
                    TextButton(
                        onClick = {
                            visibleAppCount = (visibleAppCount + APP_PAGE_SIZE).coerceAtMost(apps.size)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.settings_apps_show_more))
                    }
                }
            }
        }
        settingsSection(SettingsSection.PRIVACY, isOpen(SettingsSection.PRIVACY), ::toggleSection) {
            item(key = "secure_screen_toggle") {
                Toggle(
                    stringResource(R.string.settings_secure_screen),
                    s.secureScreen,
                    vm::setSecureScreen,
                    description = stringResource(R.string.settings_secure_screen_desc)
                )
            }
            item(key = "auto_clear_toggle") {
                Toggle(
                    stringResource(R.string.settings_auto_clear_enabled),
                    s.autoClearEnabled,
                    vm::setAutoClearEnabled,
                    description = stringResource(R.string.settings_auto_clear_desc)
                )
            }
            if (s.autoClearEnabled) {
                item(key = "auto_clear_1h") {
                    SettingsRadioOption(s.autoClearHours == 1, stringResource(R.string.settings_auto_clear_1h)) {
                        vm.setAutoClearHours(1)
                    }
                }
                item(key = "auto_clear_6h") {
                    SettingsRadioOption(s.autoClearHours == 6, stringResource(R.string.settings_auto_clear_6h)) {
                        vm.setAutoClearHours(6)
                    }
                }
                item(key = "auto_clear_24h") {
                    SettingsRadioOption(s.autoClearHours == 24, stringResource(R.string.settings_auto_clear_24h)) {
                        vm.setAutoClearHours(24)
                    }
                }
                item(key = "auto_clear_72h") {
                    SettingsRadioOption(s.autoClearHours == 72, stringResource(R.string.settings_auto_clear_72h)) {
                        vm.setAutoClearHours(72)
                    }
                }
                item(key = "auto_clear_168h") {
                    SettingsRadioOption(s.autoClearHours == 168, stringResource(R.string.settings_auto_clear_7d)) {
                        vm.setAutoClearHours(168)
                    }
                }
            }
        }
        settingsSection(SettingsSection.RELIABILITY, isOpen(SettingsSection.RELIABILITY), ::toggleSection) {
            item(key = "batching_toggle") {
                Toggle(
                    stringResource(R.string.settings_batching_enabled),
                    s.batchingEnabled,
                    vm::setBatchingEnabled,
                    description = stringResource(R.string.settings_batching_desc)
                )
            }
            if (s.batchingEnabled) {
                item(key = "batching_cooldown") {
                    CooldownSlider(s.batchingCooldownSeconds, vm::setBatchingCooldownSeconds)
                }
            }
            item(key = "dumbphone_toggle") {
                Toggle(
                    stringResource(R.string.settings_dumbphone_mode),
                    s.dumbphoneMode,
                    vm::setDumbphoneMode,
                    description = stringResource(R.string.settings_dumbphone_mode_desc)
                )
            }
        }
        settingsSection(SettingsSection.APPEARANCE, isOpen(SettingsSection.APPEARANCE), ::toggleSection) {
            item(key = "theme_heading") {
                SectionHeading(stringResource(R.string.settings_theme_title))
            }
            item(key = "theme_system") {
                SettingsRadioOption(s.themeMode == ThemeMode.SYSTEM, stringResource(R.string.settings_theme_system)) {
                    vm.setThemeMode(ThemeMode.SYSTEM)
                }
            }
            item(key = "theme_light") {
                SettingsRadioOption(s.themeMode == ThemeMode.LIGHT, stringResource(R.string.settings_theme_light)) {
                    vm.setThemeMode(ThemeMode.LIGHT)
                }
            }
            item(key = "theme_dark") {
                SettingsRadioOption(s.themeMode == ThemeMode.DARK, stringResource(R.string.settings_theme_dark)) {
                    vm.setThemeMode(ThemeMode.DARK)
                }
            }
            item(key = "custom_theme_info") {
                val customThemeName = s.customThemeSource?.let {
                    runCatching { app.notificationbridge.ui.theme.CustomThemeParser.parse(it).name }
                        .getOrNull()
                }
                Text(
                    if (customThemeName == null) stringResource(R.string.settings_theme_builtin_active)
                    else stringResource(R.string.settings_theme_custom_active, customThemeName),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item(key = "custom_theme_format_help") {
                Text(
                    stringResource(R.string.settings_theme_format_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item(key = "custom_theme_import") {
                OutlinedButton(onClick = onImportCustomTheme, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_theme_import))
                }
            }
            if (s.customThemeSource != null) {
                item(key = "custom_theme_reset") {
                    TextButton(onClick = onResetCustomTheme) {
                        Text(stringResource(R.string.settings_theme_reset))
                    }
                }
            }
            item(key = "language_heading") {
                SectionHeading(stringResource(R.string.settings_language_title))
            }
            val currentLanguageTag = AppCompatDelegate.getApplicationLocales().get(0)?.language
            item(key = "language_de") {
                SettingsRadioOption(currentLanguageTag == "de", stringResource(R.string.language_german)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("de"))
                }
            }
            item(key = "language_en") {
                SettingsRadioOption(currentLanguageTag == "en", stringResource(R.string.language_english)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
                }
            }
            item(key = "language_es") {
                SettingsRadioOption(currentLanguageTag == "es", stringResource(R.string.language_spanish)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("es"))
                }
            }
            item(key = "language_fr") {
                SettingsRadioOption(currentLanguageTag == "fr", stringResource(R.string.language_french)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
                }
            }
            item(key = "language_it") {
                SettingsRadioOption(currentLanguageTag == "it", stringResource(R.string.language_italian)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("it"))
                }
            }
            item(key = "language_nl") {
                SettingsRadioOption(currentLanguageTag == "nl", stringResource(R.string.language_dutch)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("nl"))
                }
            }
            item(key = "language_pt") {
                SettingsRadioOption(currentLanguageTag == "pt", stringResource(R.string.language_portuguese)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("pt"))
                }
            }
            item(key = "language_zh") {
                SettingsRadioOption(currentLanguageTag == "zh", stringResource(R.string.language_chinese_simplified)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-CN"))
                }
            }
            item(key = "language_ja") {
                SettingsRadioOption(currentLanguageTag == "ja", stringResource(R.string.language_japanese)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ja"))
                }
            }
            item(key = "language_ko") {
                SettingsRadioOption(currentLanguageTag == "ko", stringResource(R.string.language_korean)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ko"))
                }
            }
            item(key = "language_ru") {
                SettingsRadioOption(currentLanguageTag == "ru", stringResource(R.string.language_russian)) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ru"))
                }
            }
        }
        settingsSection(SettingsSection.ADVANCED, isOpen(SettingsSection.ADVANCED), ::toggleSection) {
            item(key = "test_kind_heading") {
                SectionHeading(stringResource(R.string.test_kind_title))
            }
            item(key = "test_kind_short") {
                SettingsRadioOption(s.testSampleKind == TestSampleKind.SHORT, stringResource(R.string.test_kind_short)) {
                    vm.setTestSampleKind(TestSampleKind.SHORT)
                }
            }
            item(key = "test_kind_long") {
                SettingsRadioOption(s.testSampleKind == TestSampleKind.LONG, stringResource(R.string.test_kind_long)) {
                    vm.setTestSampleKind(TestSampleKind.LONG)
                }
            }
            item(key = "test_kind_special") {
                SettingsRadioOption(
                    s.testSampleKind == TestSampleKind.SPECIAL_CHARS,
                    stringResource(R.string.test_kind_special)
                ) { vm.setTestSampleKind(TestSampleKind.SPECIAL_CHARS) }
            }
            item(key = "test_kind_emoji") {
                SettingsRadioOption(s.testSampleKind == TestSampleKind.EMOJI, stringResource(R.string.test_kind_emoji)) {
                    vm.setTestSampleKind(TestSampleKind.EMOJI)
                }
            }
            item(key = "backup_heading") {
                SectionHeading(stringResource(R.string.settings_backup_title))
            }
            item(key = "backup_intro") {
                Text(
                    stringResource(R.string.settings_backup_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item(key = "backup_export") {
                OutlinedButton(
                    onClick = onExportConfig,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text(stringResource(R.string.settings_backup_export)) }
            }
            item(key = "backup_import") {
                OutlinedButton(onClick = onImportConfig, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.settings_backup_import))
                }
            }
            item(key = "tutorial_button") {
                OutlinedButton(
                    onClick = onShowTutorial,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.settings_show_tutorial_button))
                }
            }
            item(key = "restore_defaults_button") {
                OutlinedButton(
                    onClick = onRestoreDefaults,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.settings_restore_defaults))
                }
            }
        }
    }
}

/** The six groups Settings is organised into, in display order. */
internal enum class SettingsSection(@param:StringRes val titleRes: Int, @param:StringRes val descriptionRes: Int) {
    CONNECTION(R.string.settings_section_connection, R.string.settings_section_connection_desc),
    FORWARDING(R.string.settings_group_forwarding, R.string.settings_section_forwarding_desc),
    PRIVACY(R.string.settings_section_privacy, R.string.settings_section_privacy_desc),
    RELIABILITY(R.string.settings_section_reliability, R.string.settings_section_reliability_desc),
    APPEARANCE(R.string.settings_section_appearance, R.string.settings_section_appearance_desc),
    ADVANCED(R.string.settings_section_advanced, R.string.settings_section_advanced_desc)
}

/** A tappable section header, followed by [content] only while the section is open. */
private fun LazyListScope.settingsSection(
    section: SettingsSection,
    expanded: Boolean,
    onToggle: (SettingsSection) -> Unit,
    content: LazyListScope.() -> Unit
) {
    item(key = "section_${section.name}") {
        SettingsSectionHeader(
            title = stringResource(section.titleRes),
            description = stringResource(section.descriptionRes),
            expanded = expanded,
            onClick = { onToggle(section) }
        )
    }
    if (expanded) {
        content()
        item(key = "section_${section.name}_end") { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    description: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "sectionChevron")
    val stateText = stringResource(
        if (expanded) R.string.settings_section_expanded else R.string.settings_section_collapsed
    )
    InsetSurface(Modifier.padding(top = 6.dp)) {
        Row(
            Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { stateDescription = stateText }
                .defaultMinSize(minHeight = 64.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "›",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
                    .clearAndSetSemantics {}
                    .graphicsLayer { rotationZ = rotation }
            )
        }
    }
}

@Composable
fun CooldownSlider(seconds: Int, onChange: (Int) -> Unit) {
    // Local float mirror so dragging feels continuous; onChange (and persistence) only fires
    // with the rounded integer, and only while actively dragging - not on every pixel of travel.
    var pending by remember(seconds) { mutableFloatStateOf(seconds.toFloat()) }
    Column {
        Text(
            stringResource(R.string.settings_batching_seconds, pending.roundToInt()),
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = pending,
            onValueChange = { pending = it },
            onValueChangeFinished = { onChange(pending.roundToInt()) },
            valueRange = SettingsRepository.MIN_BATCHING_COOLDOWN_SECONDS.toFloat()..
                SettingsRepository.MAX_BATCHING_COOLDOWN_SECONDS.toFloat()
        )
    }
}

@Composable
private fun SettingsRadioOption(
    selected: Boolean,
    label: String,
    alignLabelCenter: Boolean = true,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(end = 12.dp),
        verticalAlignment = if (alignLabelCenter) Alignment.CenterVertically else Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            label,
            Modifier.weight(1f).padding(
                start = 8.dp,
                top = if (alignLabelCenter) 0.dp else 12.dp
            ),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit, description: String? = null) {
    Row(
        Modifier.fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .toggleable(value = value, onValueChange = onChange, role = Role.Switch)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = value, onCheckedChange = null)
    }
}

private const val IMPORT_PREVIEW_APPS = 12
private const val URL_REPO = "https://github.com/yopo3r/notification-bridge"
private const val URL_ISSUES = "https://github.com/yopo3r/notification-bridge/issues"
private const val URL_THEMES = "https://github.com/yopo3r/notification-bridge-themes"

/** Overflow menu in the top bar with links to the repository, issue tracker and themes repo. */
@Composable
private fun LinksMenu() {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val moreLabel = stringResource(R.string.menu_more)

    /** A device without a browser must not crash the app. */
    fun open(url: String) {
        expanded = false
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = moreLabel }
        ) {
            // Drawn instead of a "\u22EE" glyph: font metrics leave a text glyph off-centre.
            val dotColor = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.size(24.dp).clearAndSetSemantics { }) {
                val radius = 2.dp.toPx()
                val gap = 6.dp.toPx()
                val cx = size.width / 2f
                val cy = size.height / 2f
                for (i in -1..1) {
                    drawCircle(dotColor, radius, Offset(cx, cy + i * gap))
                }
            }
        }
        // Colors and shape come from the app theme (and custom themes) instead of the
        // Material defaults, which ignore the palette.
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(18.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 6.dp
        ) {
            val itemColors = MenuDefaults.itemColors(
                textColor = MaterialTheme.colorScheme.onSurface
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_github_repo)) },
                colors = itemColors,
                onClick = { open(URL_REPO) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_report_issue)) },
                colors = itemColors,
                onClick = { open(URL_ISSUES) }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_get_themes)) },
                colors = itemColors,
                onClick = { open(URL_THEMES) }
            )
        }
    }
}
