package app.notificationbridge.queue

import app.notificationbridge.model.TransferRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferHistoryTest {

    private fun record(i: Int) = TransferRecord("App", i.toLong(), true, "ok", "t$i")

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
}
