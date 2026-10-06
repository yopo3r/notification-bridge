package app.notificationbridge.readiness

import app.notificationbridge.model.BridgeSettings
import app.notificationbridge.model.BridgeUiState
import app.notificationbridge.model.TransferRecord
import app.notificationbridge.model.TransferStatus
import app.notificationbridge.readiness.ReadinessItem.*
import app.notificationbridge.readiness.ReadinessStatus.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadinessChecklistTest {

    private val readySettings = BridgeSettings(
        selectedAddress = "AA:BB:CC:DD:EE:FF",
        selectedName = "Receiver",
        bridgeEnabled = true
    )
    private val readyState = BridgeUiState(
        notificationAccess = true,
        listenerConnected = true,
        history = listOf(TransferRecord("Chat", 2_000, TransferStatus.TRANSFERRED, "ok"))
    )
    private val readyFacts = ReadinessFacts(true, true, true)

    private fun build(
        settings: BridgeSettings = readySettings,
        state: BridgeUiState = readyState,
        facts: ReadinessFacts = readyFacts
    ) = ReadinessChecklist.build(settings, state, facts).associateBy { it.item }

    @Test
    fun `lists every row in onboarding order`() {
        val items = ReadinessChecklist.build(readySettings, readyState, readyFacts).map { it.item }
        assertEquals(
            listOf(
                NOTIFICATION_ACCESS, LISTENER_CONNECTED, BLUETOOTH_ENABLED, RECEIVER_SELECTED,
                RECEIVER_PAIRED, BRIDGE_ENABLED, BATTERY_OPTIMIZATION, LAST_TRANSFER
            ),
            items
        )
    }

    @Test
    fun `a fully configured bridge has nothing needing attention`() {
        val entries = ReadinessChecklist.build(readySettings, readyState, readyFacts)
        assertTrue(entries.all { it.status == OK })
        assertEquals(0, ReadinessChecklist.needsAttention(entries))
    }

    @Test
    fun `a fresh install flags each missing precondition`() {
        val entries = ReadinessChecklist.build(
            BridgeSettings(),
            BridgeUiState(),
            ReadinessFacts(bluetoothEnabled = false, batteryOptimizationIgnored = false)
        )
        val byItem = entries.associateBy { it.item }
        assertEquals(ACTION, byItem.getValue(NOTIFICATION_ACCESS).status)
        assertEquals(ACTION, byItem.getValue(LISTENER_CONNECTED).status)
        assertEquals(ACTION, byItem.getValue(BLUETOOTH_ENABLED).status)
        assertEquals(ACTION, byItem.getValue(RECEIVER_SELECTED).status)
        assertEquals(ACTION, byItem.getValue(BRIDGE_ENABLED).status)
        assertEquals(ACTION, byItem.getValue(BATTERY_OPTIMIZATION).status)
        assertEquals(6, ReadinessChecklist.needsAttention(entries))
    }

    @Test
    fun `listener can be disconnected while access is granted`() {
        val byItem = build(state = readyState.copy(listenerConnected = false))
        assertEquals(OK, byItem.getValue(NOTIFICATION_ACCESS).status)
        assertEquals(ACTION, byItem.getValue(LISTENER_CONNECTED).status)
    }

    @Test
    fun `pairing is not judged until a receiver is selected`() {
        val byItem = build(settings = readySettings.copy(selectedAddress = null, selectedName = null))
        val paired = byItem.getValue(RECEIVER_PAIRED)
        assertEquals(UNKNOWN, paired.status)
        assertTrue(paired.blockedByReceiver)
        assertEquals(ACTION, byItem.getValue(RECEIVER_SELECTED).status)
    }

    @Test
    fun `a selected receiver that is no longer bonded needs attention`() {
        val byItem = build(facts = readyFacts.copy(receiverPaired = false))
        val paired = byItem.getValue(RECEIVER_PAIRED)
        assertEquals(ACTION, paired.status)
        assertEquals(false, paired.blockedByReceiver)
    }

    @Test
    fun `undeterminable platform facts stay unknown instead of guessed`() {
        val byItem = build(facts = ReadinessFacts())
        assertEquals(UNKNOWN, byItem.getValue(BLUETOOTH_ENABLED).status)
        assertEquals(UNKNOWN, byItem.getValue(RECEIVER_PAIRED).status)
        assertEquals(UNKNOWN, byItem.getValue(BATTERY_OPTIMIZATION).status)
        assertEquals(0, ReadinessChecklist.needsAttention(ReadinessChecklist.build(readySettings, readyState, ReadinessFacts())))
    }

    @Test
    fun `last transfer is the newest success even when newer attempts failed`() {
        val history = listOf(
            TransferRecord("Chat", 3_000, TransferStatus.FAILED, "boom"),
            TransferRecord("Chat", 2_000, TransferStatus.TRANSFERRED, "ok"),
            TransferRecord("Chat", 1_000, TransferStatus.TRANSFERRED, "ok")
        )
        val entry = build(state = readyState.copy(history = history)).getValue(LAST_TRANSFER)
        assertEquals(OK, entry.status)
        assertEquals(2_000L, entry.timestamp)
    }

    @Test
    fun `no successful transfer is informational, not a problem`() {
        val failedOnly = readyState.copy(history = listOf(TransferRecord("Chat", 3_000, TransferStatus.FAILED, "boom")))
        for (state in listOf(readyState.copy(history = emptyList()), failedOnly)) {
            val entry = build(state = state).getValue(LAST_TRANSFER)
            assertEquals(UNKNOWN, entry.status)
            assertNull(entry.timestamp)
        }
    }
}
