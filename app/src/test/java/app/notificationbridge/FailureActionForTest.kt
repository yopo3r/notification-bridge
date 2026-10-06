package app.notificationbridge

import app.notificationbridge.model.FailureAction
import app.notificationbridge.model.FailureReason
import app.notificationbridge.model.TransferRecord
import app.notificationbridge.model.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FailureActionForTest {

    private fun failed(reason: FailureReason?, retryUntil: Long? = null) =
        TransferRecord("Chat", 0, TransferStatus.FAILED, "", failure = reason, retryUntil = retryUntil)

    @Test
    fun `only failed rows get an action`() {
        val ok = TransferRecord("Chat", 0, TransferStatus.TRANSFERRED, "")
        assertNull(failureActionFor(ok, true, 0))
    }

    @Test
    fun `retry is offered only while the notification is still held`() {
        val r = failed(FailureReason.TIMED_OUT, retryUntil = 1000)
        assertEquals(FailureAction.RETRY_NOW, failureActionFor(r, true, 999))
        assertNull(failureActionFor(r, true, 1000))
        assertNull(failureActionFor(failed(FailureReason.TIMED_OUT), true, 0))
    }

    @Test
    fun `compatibility test without a receiver becomes choose receiver`() {
        val r = failed(FailureReason.RECEIVER_REJECTED)
        assertEquals(FailureAction.SEND_COMPATIBILITY_TEST, failureActionFor(r, true, 0))
        assertEquals(FailureAction.CHOOSE_RECEIVER, failureActionFor(r, false, 0))
    }

    @Test
    fun `missing reason is treated as unknown`() {
        assertEquals(FailureAction.OPEN_DIAGNOSTICS, failureActionFor(failed(null), true, 0))
        assertEquals(FailureAction.OPEN_BLUETOOTH_SETTINGS, failureActionFor(failed(FailureReason.NOT_PAIRED), true, 0))
    }
}
