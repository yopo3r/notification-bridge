package app.notificationbridge.queue

import app.notificationbridge.bluetooth.ObexException
import app.notificationbridge.model.FailureReason
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class FailureClassifierTest {

    @Test
    fun `a tagged OBEX failure keeps its reason`() {
        val e = ObexException("anything", reason = FailureReason.TIMED_OUT)
        assertEquals(FailureReason.TIMED_OUT, FailureClassifier.classify(e))
    }

    @Test
    fun `message text is never inspected`() {
        val e = ObexException("not paired timed out permission", reason = FailureReason.RECEIVER_REJECTED)
        assertEquals(FailureReason.RECEIVER_REJECTED, FailureClassifier.classify(e))
    }

    @Test
    fun `untagged OBEX failure is unknown`() {
        assertEquals(FailureReason.UNKNOWN, FailureClassifier.classify(ObexException("x")))
    }

    @Test
    fun `security exceptions mean the permission is missing`() {
        assertEquals(FailureReason.PERMISSION_MISSING, FailureClassifier.classify(SecurityException("x")))
    }

    @Test
    fun `raw IO errors mean the receiver is unavailable`() {
        assertEquals(FailureReason.RECEIVER_UNAVAILABLE, FailureClassifier.classify(IOException("read failed")))
    }

    @Test
    fun `anything else, including null, is unknown`() {
        assertEquals(FailureReason.UNKNOWN, FailureClassifier.classify(IllegalStateException()))
        assertEquals(FailureReason.UNKNOWN, FailureClassifier.classify(null))
    }
}
