/**
 * Builds the plain-text report behind the Diagnostics screen's "Copy diagnostic info" button.
 *
 * The output is an allow-list: it reads only specific diagnostic fields and never includes
 * notification titles or bodies, the receiver's name or Bluetooth address, allowed package
 * names, exception details, or exact event times. It does include the source phone model and
 * configuration state, so users should review it before sharing.
 *
 * The last OBEX response is a protocol response line, passed through [sanitize] to flatten,
 * redact MAC-shaped tokens, and cap its length. Arbitrary exception messages are never copied.
 *
 * Pure JVM on purpose (no Android types): the caller gathers the platform facts.
 */
package app.notificationbridge.diagnostics

import app.notificationbridge.model.BridgeSettings
import app.notificationbridge.model.BridgeUiState

data class DiagnosticsEnvironment(
    val appVersion: String,
    val androidRelease: String,
    val sdkInt: Int,
    val deviceModel: String
)

object DiagnosticsReport {

    const val OBEX_PUSH_UUID = "00001105-0000-1000-8000-00805F9B34FB"
    private const val MAX_FIELD_CHARS = 200
    private val MAC_ADDRESS = Regex("(?i)\\b(?:[0-9a-f]{2}:){5}[0-9a-f]{2}\\b")
    private val WHITESPACE = Regex("\\s+")

    /**
     * @param bluetoothEnabled `null` when it could not be determined (e.g. missing permission).
     * @param receiverPaired `null` when it could not be determined.
     */
    fun build(
        env: DiagnosticsEnvironment,
        settings: BridgeSettings,
        state: BridgeUiState,
        bluetoothEnabled: Boolean?,
        receiverPaired: Boolean?
    ): String {
        // Exception text can contain notification-derived names or other user data. Report only
        // whether a failure exists; arbitrary exception details are deliberately excluded.
        val hasError = state.history.any { !it.success }
        val lastTransfer = state.lastTransfer?.let { if (it.success) "success" else "failed" }
        return listOf(
            "Notification Bridge diagnostics",
            "",
            "[App]",
            "Version: ${sanitize(env.appVersion)}",
            "",
            "[Device]",
            "Android: ${sanitize(env.androidRelease)} (API ${env.sdkInt})",
            "Model: ${sanitize(env.deviceModel)}",
            "",
            "[Bluetooth]",
            "Adapter: ${tri(bluetoothEnabled, "enabled", "disabled")}",
            "Receiver selected: ${yesNo(settings.selectedAddress != null)}",
            "Receiver paired: ${tri(receiverPaired, "yes", "no")}",
            "RFCOMM channel: resolved by SDP lookup of OBEX Object Push UUID $OBEX_PUSH_UUID " +
                "(the channel number is not exposed by the Android API)",
            "OBEX last response: ${state.lastObexResponse?.let(::sanitize) ?: "none"}",
            "Connection state: ${state.connection}",
            "",
            "[Notifications]",
            "Notification access granted: ${yesNo(state.notificationAccess)}",
            "Listener connected: ${yesNo(state.listenerConnected)}",
            "",
            "[Queue]",
            "Pending: ${state.queueCount}",
            "Batched (waiting for cooldown): ${state.batchedMessageCount}",
            "Transfers in history: ${state.history.size}",
            "Last transfer: ${lastTransfer ?: "none"}",
            "Transfer errors recorded: ${if (hasError) "yes (details omitted)" else "no"}",
            "",
            "[Settings]",
            "Bridge enabled: ${yesNo(settings.bridgeEnabled)}",
            "Auto-reconnect: ${yesNo(settings.autoReconnect)}",
            "Dumbphone mode: ${yesNo(settings.dumbphoneMode)}",
            "Batching enabled: ${yesNo(settings.batchingEnabled)} (cooldown: ${settings.batchingCooldownSeconds}s)",
            "Notify calls: ${yesNo(settings.notifyOnCalls)}",
            "Ignore silent: ${yesNo(settings.ignoreSilent)}",
            "Ignore ongoing: ${yesNo(settings.ignoreOngoing)}",
            "Ignore duplicates: ${yesNo(settings.ignoreUpdates)}",
            "Auto-clear enabled: ${yesNo(settings.autoClearEnabled)} (after: ${settings.autoClearHours}h)",
            "Allowed apps: ${settings.allowedPackages.size}"
        ).joinToString("\n")
    }

    /** Single line, MAC-shaped tokens redacted, length-capped. */
    fun sanitize(text: String): String {
        val oneLine = text.replace(WHITESPACE, " ").trim()
        val redacted = MAC_ADDRESS.replace(oneLine, "XX:XX:XX:XX:XX:XX")
        return if (redacted.length <= MAX_FIELD_CHARS) redacted else redacted.take(MAX_FIELD_CHARS - 1) + "…"
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    private fun tri(value: Boolean?, ifTrue: String, ifFalse: String) =
        when (value) {
            true -> ifTrue
            false -> ifFalse
            null -> "unknown"
        }

}
