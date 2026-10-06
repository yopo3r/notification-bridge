package app.notificationbridge.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailureReasonTest {

    @Test
    fun `the five requested failures map to their action`() {
        assertEquals(FailureAction.OPEN_BLUETOOTH_SETTINGS, FailureReason.NOT_PAIRED.action)
        assertEquals(FailureAction.GRANT_PERMISSION, FailureReason.PERMISSION_MISSING.action)
        assertEquals(FailureAction.SEND_COMPATIBILITY_TEST, FailureReason.RECEIVER_REJECTED.action)
        assertEquals(FailureAction.RETRY_NOW, FailureReason.TIMED_OUT.action)
        assertEquals(FailureAction.RETRY_NOW, FailureReason.RECEIVER_UNAVAILABLE.action)
    }

    @Test
    fun `every reason except no adapter offers an action`() {
        FailureReason.values().filter { it != FailureReason.BLUETOOTH_UNAVAILABLE }
            .forEach { assertNotNull(it.name, it.action) }
        assertNull(FailureReason.BLUETOOTH_UNAVAILABLE.action)
    }

    @Test
    fun `setup problems stop after one attempt and are not retryable`() {
        listOf(
            FailureReason.NO_RECEIVER, FailureReason.BLUETOOTH_UNAVAILABLE, FailureReason.BLUETOOTH_OFF,
            FailureReason.PERMISSION_MISSING, FailureReason.NOT_PAIRED
        ).forEach {
            assertTrue(it.name, it.needsUserAction)
            assertFalse(it.name, it.retryable)
            assertFalse(it.name, it.countsTowardCircuitBreaker)
        }
    }

    @Test
    fun `link failures are retryable and the pause itself does not trip the breaker`() {
        assertTrue(FailureReason.RECEIVER_UNAVAILABLE.retryable)
        assertTrue(FailureReason.TIMED_OUT.retryable)
        assertTrue(FailureReason.PAUSED.retryable)
        assertTrue(FailureReason.RECEIVER_UNAVAILABLE.countsTowardCircuitBreaker)
        assertTrue(FailureReason.TIMED_OUT.countsTowardCircuitBreaker)
        assertFalse(FailureReason.PAUSED.countsTowardCircuitBreaker)
    }

    @Test
    fun `a rejected file is not retried automatically`() {
        assertFalse(FailureReason.RECEIVER_REJECTED.retryable)
        assertFalse(FailureReason.RECEIVER_REJECTED.needsUserAction)
    }
}
