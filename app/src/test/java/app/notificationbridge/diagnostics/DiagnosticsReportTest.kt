package app.notificationbridge.diagnostics

import app.notificationbridge.model.BridgeSettings
import app.notificationbridge.model.BridgeUiState
import app.notificationbridge.model.TransferRecord
import app.notificationbridge.model.TransferStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The report is pasted into public GitHub Issues, so most of these tests are negative: they
 * plant recognizable "private" values everywhere the app could leak them and assert none show up.
 */
class DiagnosticsReportTest {

    private val env = DiagnosticsEnvironment("0.10.0", "14", 34, "Xiaomi Redmi 9")

    private val settings = BridgeSettings(
        selectedAddress = "AA:BB:CC:DD:EE:FF",
        selectedName = "Alices Private Phone",
        bridgeEnabled = true,
        dumbphoneMode = true,
        allowedPackages = setOf("com.secret.chat", "com.whatsapp")
    )

    private val state = BridgeUiState(
        notificationAccess = true,
        listenerConnected = true,
        queueCount = 2,
        lastTransfer = TransferRecord("Chat", 1_700_000_000_000, TransferStatus.FAILED, "boom", "Secret Person"),
        history = listOf(
            TransferRecord("Chat", 1_700_000_000_000, TransferStatus.FAILED, "failed to connect to AA:BB:CC:DD:EE:FF", "Secret Person"),
            TransferRecord("Chat", 1_699_999_999_000, TransferStatus.TRANSFERRED, "0xA0 Success", "Another Contact")
        ),
        lastObexResponse = "OBEX PUT response received: 0xA0 Success"
    )

    private fun report(bluetooth: Boolean? = true, paired: Boolean? = true) =
        DiagnosticsReport.build(env, settings, state, bluetooth, paired)

    @Test
    fun `never includes the receiver name or address`() {
        val text = report()
        assertFalse(text.contains("Alices Private Phone"))
        assertFalse(text.contains("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `never includes notification titles or log lines`() {
        val text = report()
        assertFalse(text.contains("Secret Person"))
        assertFalse(text.contains("Another Contact"))
        assertFalse(text.contains("WA_Secret_Person"))
    }

    @Test
    fun `reports how many apps are allowed but not which`() {
        val text = report()
        assertTrue(text.contains("Allowed apps: 2"))
        assertFalse(text.contains("com.secret.chat"))
        assertFalse(text.contains("com.whatsapp"))
    }

    @Test
    fun `omits arbitrary error details from the shareable report`() {
        val text = report()
        assertTrue(text.contains("Transfer errors recorded: yes (details omitted)"))
        assertFalse(text.contains("failed to connect"))
        assertFalse(text.contains("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `includes version, platform and the state that helps debugging`() {
        val text = report()
        assertTrue(text.contains("Version: 0.10.0"))
        assertTrue(text.contains("Android: 14 (API 34)"))
        assertTrue(text.contains("Model: Xiaomi Redmi 9"))
        assertTrue(text.contains("Notification access granted: yes"))
        assertTrue(text.contains("Listener connected: yes"))
        assertTrue(text.contains("Pending: 2"))
        assertTrue(text.contains("Dumbphone mode: yes"))
        assertTrue(text.contains("OBEX last response: OBEX PUT response received: 0xA0 Success"))
    }

    @Test
    fun `reports batching state and the count of messages waiting in cooldown`() {
        val batchingState = state.copy(batchedMessageCount = 3)
        val batchingSettings = settings.copy(batchingEnabled = true, batchingCooldownSeconds = 20)
        val text = DiagnosticsReport.build(env, batchingSettings, batchingState, true, true)
        assertTrue(text.contains("Batched (waiting for cooldown): 3"))
        assertTrue(text.contains("Batching enabled: yes (cooldown: 20s)"))
    }

    @Test
    fun `unknown platform facts are reported as unknown instead of guessed`() {
        val text = report(bluetooth = null, paired = null)
        assertTrue(text.contains("Adapter: unknown"))
        assertTrue(text.contains("Receiver paired: unknown"))
    }

    @Test
    fun `an empty state says none rather than crashing`() {
        val text = DiagnosticsReport.build(env, BridgeSettings(), BridgeUiState(), true, null)
        assertTrue(text.contains("Last transfer: none"))
        assertTrue(text.contains("Transfer errors recorded: no"))
        assertTrue(text.contains("Receiver selected: no"))
    }

    @Test
    fun `reports the auto-clear setting`() {
        val text = report()
        assertTrue(text.contains("Auto-clear enabled: yes (after: 24h)"))
    }

    @Test
    fun `sanitize flattens lines and caps the length`() {
        val cleaned = DiagnosticsReport.sanitize("line one\nline two " + "x".repeat(500))
        assertFalse(cleaned.contains("\n"))
        assertTrue(cleaned.length <= 200)
    }

    @Test
    fun `reports failure reasons by name and count without any detail text`() {
        val failing = state.copy(
            lastTransfer = TransferRecord(
                "Chat", 1, TransferStatus.FAILED, "failed to connect to AA:BB:CC:DD:EE:FF",
                failure = app.notificationbridge.model.FailureReason.TIMED_OUT, attempt = 3, maxAttempts = 3
            ),
            history = listOf(
                TransferRecord("Chat", 1, TransferStatus.FAILED, "x", failure = app.notificationbridge.model.FailureReason.TIMED_OUT),
                TransferRecord("Chat", 2, TransferStatus.FAILED, "y", failure = app.notificationbridge.model.FailureReason.NOT_PAIRED),
                TransferRecord("Chat", 3, TransferStatus.TRANSFERRED, "ok")
            )
        )
        val text = DiagnosticsReport.build(env, settings, failing, true, true)
        assertTrue(text.contains("Last failure: timed_out (attempt 3 of 3)"))
        assertTrue(text.contains("Failures by reason: timed_out=1, not_paired=1"))
        assertFalse(text.contains("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `failure lines say none when nothing failed`() {
        val ok = state.copy(lastTransfer = null, history = emptyList())
        val text = DiagnosticsReport.build(env, settings, ok, true, true)
        assertTrue(text.contains("Last failure: none"))
        assertTrue(text.contains("Failures by reason: none"))
    }
}
