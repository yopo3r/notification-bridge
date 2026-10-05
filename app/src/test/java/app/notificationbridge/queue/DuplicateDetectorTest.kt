package app.notificationbridge.queue

import app.notificationbridge.model.NotificationData
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    private fun notification(
        title: String? = "Ana",
        text: String? = "hola",
        key: String = "k1",
        pkg: String = "com.example.chat"
    ) = NotificationData(
        packageName = pkg,
        appName = "Chat",
        title = title,
        text = text,
        timestamp = 0,
        category = null,
        notificationKey = key,
        isOngoing = false,
        isSilent = false
    )

    @Test
    fun `first sighting is never a duplicate`() {
        assertFalse(DuplicateDetector().isDuplicate(notification(), windowMs = 5_000, now = 0))
    }

    @Test
    fun `identical repost inside the window is a duplicate`() {
        val detector = DuplicateDetector()
        detector.isDuplicate(notification(), 5_000, now = 0)
        assertTrue(detector.isDuplicate(notification(), 5_000, now = 2_000))
    }

    @Test
    fun `identical message after the window is forwarded again`() {
        val detector = DuplicateDetector()
        detector.isDuplicate(notification(), 5_000, now = 0)
        assertFalse(detector.isDuplicate(notification(), 5_000, now = 5_001))
    }

    @Test
    fun `different text or a different app is not a duplicate`() {
        val detector = DuplicateDetector()
        detector.isDuplicate(notification(text = "hola"), 5_000, now = 0)
        assertFalse(detector.isDuplicate(notification(text = "chao"), 5_000, now = 1))
        assertFalse(detector.isDuplicate(notification(pkg = "com.other"), 5_000, now = 2))
    }

    @Test
    fun `a long window is not shortened by a check with a short window in between`() {
        // Regression: purging with the *caller's* window used to forget a call's fingerprint as
        // soon as any message was checked with the shorter window.
        val detector = DuplicateDetector(retentionMs = 20_000)
        val call = notification(title = "Mom", text = "Incoming call", key = "call")
        detector.isDuplicate(call, windowMs = 20_000, now = 0)
        detector.isDuplicate(notification(), windowMs = 5_000, now = 6_000)
        assertTrue(detector.isDuplicate(call, windowMs = 20_000, now = 10_000))
    }
}
