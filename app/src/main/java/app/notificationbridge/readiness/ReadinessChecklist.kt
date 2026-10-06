/**
 * Builds the Home screen's readiness checklist: one row per precondition for the bridge to
 * actually forward a notification, in the order a first-time user should fix them.
 *
 * Pure JVM on purpose (no Android types), like [app.notificationbridge.diagnostics.DiagnosticsReport]:
 * the caller gathers the platform facts ([ReadinessFacts]) and this decides what each row says.
 * Nothing here carries the receiver's name/address or notification content.
 */
package app.notificationbridge.readiness

import app.notificationbridge.model.BridgeSettings
import app.notificationbridge.model.BridgeUiState
import app.notificationbridge.model.TransferRecord

enum class ReadinessItem {
    NOTIFICATION_ACCESS,
    LISTENER_CONNECTED,
    BLUETOOTH_ENABLED,
    RECEIVER_SELECTED,
    RECEIVER_PAIRED,
    BRIDGE_ENABLED,
    BATTERY_OPTIMIZATION,
    LAST_TRANSFER
}

/**
 * [OK] the row is satisfied; [ACTION] the user can and should fix it; [UNKNOWN] it can't be
 * judged (permission missing, depends on an earlier row) or is informational (no transfer yet).
 * Only [ACTION] rows count towards "needs attention".
 */
enum class ReadinessStatus { OK, ACTION, UNKNOWN }

/**
 * @property timestamp Only set for [ReadinessItem.LAST_TRANSFER] when a transfer succeeded.
 * @property blockedByReceiver Only for [ReadinessItem.RECEIVER_PAIRED]: it is [ReadinessStatus.UNKNOWN]
 *   because no receiver has been selected yet, which the UI words differently from a failed lookup.
 */
data class ReadinessEntry(
    val item: ReadinessItem,
    val status: ReadinessStatus,
    val timestamp: Long? = null,
    val blockedByReceiver: Boolean = false
)

/** Platform facts the runtime state doesn't carry; `null` means it could not be determined. */
data class ReadinessFacts(
    val bluetoothEnabled: Boolean? = null,
    val receiverPaired: Boolean? = null,
    val batteryOptimizationIgnored: Boolean? = null
)

object ReadinessChecklist {

    fun build(settings: BridgeSettings, state: BridgeUiState, facts: ReadinessFacts): List<ReadinessEntry> {
        val receiverSelected = settings.selectedAddress != null
        val lastSuccess = lastSuccessfulTransfer(state.history)
        return listOf(
            ReadinessEntry(ReadinessItem.NOTIFICATION_ACCESS, flag(state.notificationAccess)),
            ReadinessEntry(ReadinessItem.LISTENER_CONNECTED, flag(state.listenerConnected)),
            ReadinessEntry(ReadinessItem.BLUETOOTH_ENABLED, tri(facts.bluetoothEnabled)),
            ReadinessEntry(ReadinessItem.RECEIVER_SELECTED, flag(receiverSelected)),
            if (receiverSelected) {
                ReadinessEntry(ReadinessItem.RECEIVER_PAIRED, tri(facts.receiverPaired))
            } else {
                ReadinessEntry(ReadinessItem.RECEIVER_PAIRED, ReadinessStatus.UNKNOWN, blockedByReceiver = true)
            },
            ReadinessEntry(ReadinessItem.BRIDGE_ENABLED, flag(settings.bridgeEnabled)),
            ReadinessEntry(ReadinessItem.BATTERY_OPTIMIZATION, tri(facts.batteryOptimizationIgnored)),
            ReadinessEntry(
                ReadinessItem.LAST_TRANSFER,
                if (lastSuccess != null) ReadinessStatus.OK else ReadinessStatus.UNKNOWN,
                timestamp = lastSuccess?.timestamp
            )
        )
    }

    fun needsAttention(entries: List<ReadinessEntry>): Int = entries.count { it.status == ReadinessStatus.ACTION }

    /**
     * By timestamp, not list position: a record keeps its place while it is updated, so a batch
     * that was created early can finish after later single messages.
     */
    fun lastSuccessfulTransfer(history: List<TransferRecord>): TransferRecord? =
        history.filter { it.success }.maxByOrNull { it.timestamp }

    private fun flag(value: Boolean) = if (value) ReadinessStatus.OK else ReadinessStatus.ACTION

    private fun tri(value: Boolean?) = when (value) {
        true -> ReadinessStatus.OK
        false -> ReadinessStatus.ACTION
        null -> ReadinessStatus.UNKNOWN
    }
}
