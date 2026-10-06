package app.notificationbridge.queue

import app.notificationbridge.model.TransferRecord
import app.notificationbridge.model.TransferStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferHistoryTest {

    private fun record(i: Int) = TransferRecord("App", i.toLong(), TransferStatus.TRANSFERRED, "ok", "t$i")

    @Test
    fun `blank or null titles produce no summary`() {
        assertNull(TransferHistory.summarizeTitle(null))
        assertNull(TransferHistory.summarizeTitle("   \n\t "))
    }

    @Test
    fun `whitespace is collapsed and trimmed`() {
        assertEquals("Ana López", TransferHistory.summarizeTitle("  Ana \n  López  "))
    }

    @Test
    fun `short titles are kept as they are`() {
        assertEquals("Juan", TransferHistory.summarizeTitle("Juan"))
    }

    @Test
    fun `long titles are truncated to the limit with an ellipsis`() {
        val summary = TransferHistory.summarizeTitle("a".repeat(200))!!
        assertEquals(TransferHistory.MAX_TITLE_CHARS, summary.length)
        assertTrue(summary.endsWith("…"))
    }

    @Test
    fun `truncation never leaves half an emoji behind`() {
        // 38 letters + two surrogate pairs: the cut would land in the middle of the first emoji.
        val summary = TransferHistory.summarizeTitle("a".repeat(38) + "😀😀")!!
        assertTrue(summary.none { Character.isSurrogate(it) })
        assertTrue(summary.endsWith("…"))
    }

    @Test
    fun `new records go first`() {
        val history = TransferHistory.append(listOf(record(1)), record(2))
        assertEquals(listOf(2L, 1L), history.map { it.timestamp })
    }

    @Test
    fun `history is capped and drops the oldest entries`() {
        var history = emptyList<TransferRecord>()
        repeat(TransferHistory.MAX_ENTRIES + 10) { history = TransferHistory.append(history, record(it)) }
        assertEquals(TransferHistory.MAX_ENTRIES, history.size)
        assertEquals((TransferHistory.MAX_ENTRIES + 9).toLong(), history.first().timestamp)
        assertEquals(10L, history.last().timestamp)
    }

    private fun tracked(id: Long, status: TransferStatus, at: Long = id) =
        TransferRecord("App", at, status, "", "t$id", id)

    @Test
    fun `upsert updates a record in place and keeps its position`() {
        var history = emptyList<TransferRecord>()
        history = TransferHistory.upsert(history, tracked(1, TransferStatus.QUEUED))
        history = TransferHistory.upsert(history, tracked(2, TransferStatus.QUEUED))
        history = TransferHistory.upsert(history, tracked(1, TransferStatus.CONNECTING, at = 10))
        history = TransferHistory.upsert(history, tracked(1, TransferStatus.TRANSFERRED, at = 11))

        assertEquals(listOf(2L, 1L), history.map { it.id })
        assertEquals(TransferStatus.TRANSFERRED, history.last().status)
        assertEquals(11L, history.last().timestamp)
        assertEquals(2, history.size)
    }

    @Test
    fun `records without an id are always added, never merged`() {
        var history = emptyList<TransferRecord>()
        history = TransferHistory.upsert(history, record(1))
        history = TransferHistory.upsert(history, record(2))
        assertEquals(2, history.size)
    }

    @Test
    fun `a full history evicts never-sent records before real transfers`() {
        var history = emptyList<TransferRecord>()
        // Oldest first: one filtered row, then a full page of transfers.
        history = TransferHistory.upsert(history, tracked(1, TransferStatus.DROPPED))
        repeat(TransferHistory.MAX_ENTRIES) {
            history = TransferHistory.upsert(history, tracked(it + 2L, TransferStatus.TRANSFERRED))
        }
        assertEquals(TransferHistory.MAX_ENTRIES, history.size)
        assertFalse(history.any { it.status == TransferStatus.DROPPED })
        assertEquals(2L, history.last().id)
    }

    @Test
    fun `a flood of filtered notifications cannot push transfers out`() {
        var history = emptyList<TransferRecord>()
        repeat(10) { history = TransferHistory.upsert(history, tracked(it + 1L, TransferStatus.TRANSFERRED)) }
        repeat(TransferHistory.MAX_ENTRIES * 2) {
            history = TransferHistory.upsert(history, tracked(100L + it, TransferStatus.RATE_LIMITED))
        }
        assertEquals(TransferHistory.MAX_ENTRIES, history.size)
        assertEquals(10, history.count { it.status == TransferStatus.TRANSFERRED })
    }

    @Test
    fun `only transferred counts as success and only transferred or failed are outcomes`() {
        for (status in TransferStatus.values()) {
            val r = TransferRecord("App", 1, status, "")
            assertEquals(status == TransferStatus.TRANSFERRED, r.success)
            assertEquals(status == TransferStatus.TRANSFERRED || status == TransferStatus.FAILED, status.isOutcome)
        }
        assertEquals(
            setOf(TransferStatus.QUEUED, TransferStatus.CONNECTING, TransferStatus.BATCHED),
            TransferStatus.values().filter { it.isInFlight }.toSet()
        )
    }
}
