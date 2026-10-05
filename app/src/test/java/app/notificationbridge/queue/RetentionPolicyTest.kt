package app.notificationbridge.queue

import org.junit.Assert.assertEquals
import org.junit.Test

class RetentionPolicyTest {

    @Test
    fun `cutoff is now minus the configured hours`() {
        assertEquals(1_000L - 3_600_000L, RetentionPolicy.cutoffFor(1, 1_000L))
        assertEquals(1_000L - 24 * 3_600_000L, RetentionPolicy.cutoffFor(24, 1_000L))
    }

    @Test
    fun `prunes items strictly older than the cutoff, keeping the boundary`() {
        val items = listOf(100L, 200L, 300L, 400L)
        val result = RetentionPolicy.pruneOlderThan(items, cutoffMillis = 200L) { it }
        assertEquals(listOf(200L, 300L, 400L), result)
    }

    @Test
    fun `empty list stays empty`() {
        val empty = emptyList<Long>()
        assertEquals(empty, RetentionPolicy.pruneOlderThan(empty, 0L) { it })
    }

    @Test
    fun `nothing is pruned when everything is newer than the cutoff`() {
        val items = listOf(500L, 600L)
        assertEquals(items, RetentionPolicy.pruneOlderThan(items, cutoffMillis = 0L) { it })
    }
}
