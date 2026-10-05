/**
 * Plain data classes shared across the app - the vocabulary every other component speaks.
 * Kept dependency-free (no Android framework types) on purpose so they stay trivial to
 * construct in unit tests.
 *
 * - [NotificationData]: normalized shape of a system notification, already stripped of
 *   Android-specific types by the time it leaves
 *   [app.notificationbridge.notification.NotificationBridgeService].
 * - [BridgeSettings]: the full persisted configuration (see
 *   [app.notificationbridge.data.SettingsRepository]).
 * - [TransferRecord] / [BridgeUiState]: read-only state the UI renders; produced by
 *   [app.notificationbridge.queue.BridgeRuntime], never constructed by the UI itself. Nothing
 *   in here is persisted to disk: transfer history lives in memory only.
 */
package app.notificationbridge.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** The four fixed samples the Test screen can send, covering the text shapes worth checking. */
enum class TestSampleKind { SHORT, LONG, SPECIAL_CHARS, EMOJI }

data class NotificationData(
    val packageName: String,
    val appName: String,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    val category: String?,
    val notificationKey: String,
    val isOngoing: Boolean,
    val isSilent: Boolean
)

data class PairedDevice(val name: String, val address: String)

/**
 * @property autoConnect Reserved. Connections are opened per transfer, so nothing consults this
 *   flag today; the key is kept so a stored value isn't lost if a persistent connection is added.
 * @property customThemeSource Versioned theme text imported by the user. Null selects the
 *   built-in light/dark schemes.
 * @property dumbphoneMode Unattended operation: minimal status notification, forced automatic
 *   reconnection, and a user-visible alert only when a transfer ultimately fails.
 * @property maxTextChars Cap on a forwarded notification's body, in characters. Applies to real
 *   notifications and to the Test screen's sample sends alike.
 * @property batchingEnabled Cooldown/batching: instead of sending each message from the same
 *   conversation as its own file, wait [batchingCooldownSeconds] after the first one and send
 *   everything that arrived in that window as a single file. Calls are never batched.
 * @property batchingCooldownSeconds How long to wait, in seconds (clamped to 5-60 - see
 *   [app.notificationbridge.data.SettingsRepository]).
 * @property testSampleKind Which fixed sample the Test screen's "send test" button sends;
 *   configured in Settings rather than on the Test screen itself.
 * @property autoClearEnabled Whether transfer history is pruned by age
 *   automatically (see [app.notificationbridge.queue.RetentionPolicy]).
 * @property autoClearHours How old an entry must be before it is pruned, in hours (clamped to
 *   1-168 - see [app.notificationbridge.data.SettingsRepository]). Defaults to 24.
 */
data class BridgeSettings(
    val selectedAddress: String? = null,
    val selectedName: String? = null,
    val bridgeEnabled: Boolean = false,
    val autoConnect: Boolean = true,
    val autoReconnect: Boolean = true,
    val ignoreSilent: Boolean = true,
    val ignoreOngoing: Boolean = true,
    val ignoreUpdates: Boolean = true,
    val notifyOnCalls: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val customThemeSource: String? = null,
    val onboardingCompleted: Boolean = false,
    val dumbphoneMode: Boolean = false,
    val maxTextChars: Int = app.notificationbridge.format.NotificationFormatter.DEFAULT_MAX_TEXT_CHARS,
    val batchingEnabled: Boolean = false,
    val batchingCooldownSeconds: Int = 15,
    val testSampleKind: TestSampleKind = TestSampleKind.SHORT,
    val autoClearEnabled: Boolean = true,
    val autoClearHours: Int = 24,
    val allowedPackages: Set<String> = emptySet()
)

/**
 * One finished transfer attempt (after all retries).
 *
 * @property title Short summary of the notification title (see
 *   [app.notificationbridge.queue.TransferHistory.summarizeTitle]); the notification body is
 *   deliberately never stored here.
 * @property detail Error message for a failed transfer, or a generic success marker.
 */
data class TransferRecord(
    val appName: String,
    val timestamp: Long,
    val success: Boolean,
    val detail: String,
    val title: String? = null
)

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * @property notificationAccess The permission is listed in system settings.
 * @property listenerConnected Android has actually bound the notification listener. Can be false
 *   even when [notificationAccess] is true (e.g. right after reinstalling a debug build).
 * @property history Most recent transfers, newest first, bounded in size.
 * @property batchedMessageCount Messages currently held in a batching cooldown, not yet sent as
 *   a file. Only non-zero while [app.notificationbridge.model.BridgeSettings.batchingEnabled].
 */
data class BridgeUiState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val notificationAccess: Boolean = false,
    val listenerConnected: Boolean = false,
    val queueCount: Int = 0,
    val lastTransfer: TransferRecord? = null,
    val history: List<TransferRecord> = emptyList(),
    val lastObexResponse: String? = null,
    val batchedMessageCount: Int = 0
)
