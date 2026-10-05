package app.notificationbridge.queue

import app.notificationbridge.model.TestSampleKind
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationSamplesTest {

    @Test
    fun `short sample is actually short`() {
        val sample = NotificationSamples.forKind(TestSampleKind.SHORT)
        assertTrue(sample.text!!.length <= 10)
    }

    @Test
    fun `long sample is long enough to exercise multi-packet OBEX PUT chunking`() {
        val sample = NotificationSamples.forKind(TestSampleKind.LONG)
        assertTrue(sample.text!!.length > 200)
    }

    @Test
    fun `special-characters sample has accents and symbols outside plain ASCII`() {
        val sample = NotificationSamples.forKind(TestSampleKind.SPECIAL_CHARS)
        val combined = sample.title + sample.text
        assertTrue(combined.any { it.code > 127 })
    }

    @Test
    fun `emoji sample contains an actual emoji`() {
        val sample = NotificationSamples.forKind(TestSampleKind.EMOJI)
        val combined = sample.title + sample.text
        assertTrue(combined.codePoints().anyMatch { it in 0x1F300..0x1FAFF })
    }

    @Test
    fun `every kind has a non-blank title and text`() {
        for (kind in TestSampleKind.entries) {
            val sample = NotificationSamples.forKind(kind)
            assertTrue("$kind title", !sample.title.isNullOrBlank())
            assertTrue("$kind text", !sample.text.isNullOrBlank())
        }
    }
}
