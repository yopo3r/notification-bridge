package app.notificationbridge.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryBufferTest {

    @Test
    fun `stored item is returned once`() {
        val b = RetryBuffer<String>(5, 1000)
        val s = b.put(1, "a", 0)
        assertEquals(1000, s.expiresAtMs)
        assertEquals("a", b.take(1, 500))
        assertNull(b.take(1, 500))
    }

    @Test
    fun `expired item is not returned`() {
        val b = RetryBuffer<String>(5, 1000)
        b.put(1, "a", 0)
        assertNull(b.take(1, 1000))
    }

    @Test
    fun `oldest is evicted beyond the limit and reported`() {
        val b = RetryBuffer<String>(2, 1000)
        b.put(1, "a", 0)
        b.put(2, "b", 1)
        val s = b.put(3, "c", 2)
        assertEquals(listOf(1L), s.dropped)
        assertNull(b.take(1, 3))
        assertEquals("b", b.take(2, 3))
        assertEquals("c", b.take(3, 3))
    }

    @Test
    fun `expired entries are reported as dropped on the next put`() {
        val b = RetryBuffer<String>(5, 100)
        b.put(1, "a", 0)
        val s = b.put(2, "b", 200)
        assertEquals(listOf(1L), s.dropped)
    }

    @Test
    fun `putting the same id replaces it without dropping`() {
        val b = RetryBuffer<String>(5, 1000)
        b.put(1, "a", 0)
        val s = b.put(1, "b", 10)
        assertTrue(s.dropped.isEmpty())
        assertEquals("b", b.take(1, 20))
    }

    @Test
    fun `clear forgets everything and returns the ids`() {
        val b = RetryBuffer<String>(5, 1000)
        b.put(1, "a", 0)
        b.put(2, "b", 0)
        assertEquals(listOf(1L, 2L), b.clear())
        assertNull(b.take(1, 1))
    }
}
