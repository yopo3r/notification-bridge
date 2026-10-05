package app.notificationbridge

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalView
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.core.os.LocaleListCompat
import app.notificationbridge.data.SettingsRepository
import app.notificationbridge.model.BridgeSettings
import app.notificationbridge.model.BridgeUiState
import app.notificationbridge.model.PairedDevice
import app.notificationbridge.model.TestSampleKind
import app.notificationbridge.model.ThemeMode
import app.notificationbridge.HomeRuntimeState
import app.notificationbridge.ui.theme.NotificationBridgeTheme
import app.notificationbridge.ui.InsetSurface
import app.notificationbridge.ui.PageHeader
import app.notificationbridge.ui.SectionHeading
import app.notificationbridge.ui.StatusPill
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
    var resultMessage by remember { mutableStateOf<String?>(null) }
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

    if (!s.onboardingCompleted || showOnboardingManually) {
        OnboardingScreen(onFinished = {
            if (!s.onboardingCompleted) vm.setOnboardingCompleted(true)
            showOnboardingManually = false
        })
        return
    }

    val pagerState = rememberPagerState(initialPage = 0) { tabLabels.size }
    val coroutineScope = rememberCoroutineScope()
    fun goToTab(index: Int) {
        coroutineScope.launch { pagerState.animateScrollToPage(index) }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text(stringResource(R.string.app_name)) })
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
                        onTest = { goToTab(1) },
                        onNotificationAccess = {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        },
                        onSettings = { goToTab(3) }
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
                                    onFailure = { e -> errorTemplate.format(e.message) }
                                )
                            }
                        },
                        onRequestPermission = ::requestPermissionsAndBluetooth
                    )
                    2 -> HistoryScreen(history = vm.history.collectAsState(initial = emptyList()).value, onClear = vm::clearHistory)
                    3 -> SettingsScreen(
                        s = s,
                        apps = apps,
                        vm = vm,
                        onLoadApps = vm::loadApps,
                        onShowTutorial = { showOnboardingManually = true },
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
    onTest: () -> Unit,
    onNotificationAccess: () -> Unit,
    onSettings: () -> Unit
) {
    val scrollState = rememberScrollState()
    val ready = s.bridgeEnabled && s.selectedAddress != null && u.notificationAccess
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(scrollState)
            .verticalScrollbar(scrollState),
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
    onImportCustomTheme: () -> Unit,
    onResetCustomTheme: () -> Unit
) {
    LaunchedEffect(Unit) { onLoadApps() }
    val listState = rememberLazyListState()
    var visibleAppCount by rememberSaveable(apps.size) { mutableIntStateOf(APP_PAGE_SIZE) }
    val visibleApps = remember(apps, visibleAppCount) { apps.take(visibleAppCount) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().verticalScrollbar(listState),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(key = "settings_page_header") {
            PageHeader(stringResource(R.string.tab_settings), subtitle = stringResource(R.string.settings_intro))
        }
        item(key = "forwarding_group") {
            SectionHeading(stringResource(R.string.settings_group_forwarding))
        }
        item(key = "bridge_toggle") {
            Toggle(stringResource(R.string.settings_bridge_enabled), s.bridgeEnabled, vm::setBridge)
        }
        item(key = "reconnect_toggle") {
            Toggle(stringResource(R.string.settings_auto_reconnect), s.autoReconnect, vm::setAutoReconnect)
        }
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
        item(key = "dumbphone_toggle") {
            Toggle(
                stringResource(R.string.settings_dumbphone_mode),
                s.dumbphoneMode,
                vm::setDumbphoneMode,
                description = stringResource(R.string.settings_dumbphone_mode_desc)
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
        item(key = "delivery_group") {
            SectionHeading(stringResource(R.string.settings_group_delivery))
        }
        item(key = "auto_clear_heading") {
            SectionHeading(stringResource(R.string.settings_auto_clear_title))
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
        item(key = "language_es") {
            SettingsRadioOption(currentLanguageTag == "es", stringResource(R.string.language_spanish)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("es"))
            }
        }
        item(key = "language_en") {
            SettingsRadioOption(currentLanguageTag == "en", stringResource(R.string.language_english)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            }
        }
        item(key = "language_fr") {
            SettingsRadioOption(currentLanguageTag == "fr", stringResource(R.string.language_french)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr"))
            }
        }
        item(key = "language_pt") {
            SettingsRadioOption(currentLanguageTag == "pt", stringResource(R.string.language_portuguese)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("pt"))
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
        item(key = "language_de") {
            SettingsRadioOption(currentLanguageTag == "de", stringResource(R.string.language_german)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("de"))
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
