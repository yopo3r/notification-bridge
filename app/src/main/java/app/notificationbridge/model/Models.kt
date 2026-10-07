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

/**
 * What a notification is turned into before it is pushed over OBEX.
 *
 * - [TEXT_FILE] a plain `.txt` file; works on any receiver that can open a received text file.
 * - [VMESSAGE] a vMessage (`.vmg`) text message, which receivers that understand the format file
 *   straight into the SMS inbox. Support and the exact dialect vary between devices.
 */
enum class MessageFormat { TEXT_FILE, VMESSAGE }

/** The four fixed samples the Receiver screen can send, covering the text shapes worth checking. */
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
 *   notifications and to the Receiver screen's sample sends alike.
 * @property batchingEnabled Cooldown/batching: instead of sending each message from the same
 *   conversation as its own file, wait [batchingCooldownSeconds] after the first one and send
 *   everything that arrived in that window as a single file. Calls are never batched.
 * @property batchingCooldownSeconds How long to wait, in seconds (clamped to 5-60 - see
 *   [app.notificationbridge.data.SettingsRepository]).
 * @property messageFormat Which file the bridge sends for each notification (see [MessageFormat]).
 *   Applies to real notifications and to the Receiver screen's test sends alike.
 * @property testSampleKind Which fixed sample the Receiver screen's "send test" button sends;
 *   configured in Settings rather than on the Receiver screen itself.
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
    val messageFormat: MessageFormat = MessageFormat.TEXT_FILE,
    val batchingEnabled: Boolean = false,
    val batchingCooldownSeconds: Int = 15,
    val testSampleKind: TestSampleKind = TestSampleKind.SHORT,
    val autoClearEnabled: Boolean = true,
    val autoClearHours: Int = 24,
    val secureScreen: Boolean = false,
    val allowedPackages: Set<String> = emptySet()
)

/**
 * Where one notification (or one batch of notifications) is in its journey. Deliberately worded
 * to claim no more than the app knows: [TRANSFERRED] means the receiving device's OBEX server
 * accepted the file, not that anyone saw the notification.
 *
 * - [QUEUED] accepted by the filters, waiting for its turn on the Bluetooth link.
 * - [CONNECTING] the worker is opening the connection and pushing the file (includes retries).
 * - [TRANSFERRED] the receiver accepted the file.
 * - [FAILED] every attempt failed, or the transfer could not be tried.
 * - [DROPPED] a user-configurable filter excluded it; see [DropReason].
 * - [RATE_LIMITED] the app exceeded its per-minute budget, so this notification was skipped.
 * - [BATCHED] held in a batching cooldown; it will be sent combined with others from the same
 *   conversation, and the same record then moves on to [QUEUED] and the later states.
 */
enum class TransferStatus {
    QUEUED, CONNECTING, TRANSFERRED, FAILED, DROPPED, RATE_LIMITED, BATCHED;

    /** The transfer was attempted and has a result. Only these update [BridgeUiState.lastTransfer]. */
    val isOutcome: Boolean get() = this == TRANSFERRED || this == FAILED

    /** Never sent because of a policy decision; the first thing to evict from a full history. */
    val isNotForwarded: Boolean get() = this == DROPPED || this == RATE_LIMITED

    /** Not final yet: the record will still change. */
    val isInFlight: Boolean get() = this == QUEUED || this == CONNECTING || this == BATCHED
}

/** Why a notification ended as [TransferStatus.DROPPED]. */
enum class DropReason { ONGOING, SILENT, CALLS_DISABLED }

/**
 * One row of the history: a single notification, or a batch sent as one file. The same record
 * (same [id]) is updated in place as it moves through [TransferStatus], with [timestamp] set to
 * the latest change.
 *
 * @property title Short summary of the notification title (see
 *   [app.notificationbridge.queue.TransferHistory.summarizeTitle]); the notification body is
 *   deliberately never stored here.
 * @property detail Technical error text for a [TransferStatus.FAILED] record; empty otherwise. It is
 *   never shown in History (which uses [failure]) nor copied into the diagnostics report.
 * @property id Identity used to update the record in place; `0` means "not tracked".
 * @property dropReason Only for [TransferStatus.DROPPED].
 * @property messageCount How many messages the record stands for: more than 1 only for a batch.
 * @property failure Why it failed ([TransferStatus.FAILED]), or, while [TransferStatus.CONNECTING]
 *   after a failed attempt, why the previous attempt failed.
 * @property attempt Which attempt the record is on (while connecting) or ended on (when failed);
 *   `0` when not applicable.
 * @property maxAttempts How many attempts this transfer may use; `0` when not applicable.
 * @property retryUntil While set and in the future, the notification is still held in memory and
 *   "Retry now" can resend it (see [app.notificationbridge.queue.RetryBuffer]).
 */
data class TransferRecord(
    val appName: String,
    val timestamp: Long,
    val status: TransferStatus,
    val detail: String,
    val title: String? = null,
    val id: Long = 0,
    val dropReason: DropReason? = null,
    val messageCount: Int = 1,
    val failure: FailureReason? = null,
    val attempt: Int = 0,
    val maxAttempts: Int = 0,
    val retryUntil: Long? = null
) {
    /** The receiver accepted the file. */
    val success: Boolean get() = status == TransferStatus.TRANSFERRED
}

enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/**
 * @property notificationAccess The permission is listed in system settings.
 * @property listenerConnected Android has actually bound the notification listener. Can be false
 *   even when [notificationAccess] is true (e.g. right after reinstalling a debug build).
 * @property lastTransfer The most recent transfer with a result (transferred or failed); in-flight
 *   and filtered records never replace it.
 * @property history Most recent records, newest first, bounded in size.
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
