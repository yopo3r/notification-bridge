/**
 * Pure helpers for the "recent transfers" list shown on the History screen.
 *
 * Privacy stance: only the source app name and a short, sanitized notification *title* are kept
 * (the same thing the receiver's file name is built from) - never the notification body - and
 * the list is bounded and held in memory only, so it disappears when the process dies or the
 * user taps "Clear history".
 */
package app.notificationbridge.queue

import app.notificationbridge.model.TransferRecord

object TransferHistory {
    const val MAX_ENTRIES = 50
    const val MAX_TITLE_CHARS = 40

    private val WHITESPACE = Regex("\\s+")

    /** Collapses whitespace, trims, and truncates to [MAX_TITLE_CHARS] without splitting an emoji. */
    fun summarizeTitle(title: String?): String? {
        val cleaned = title?.replace(WHITESPACE, " ")?.trim().orEmpty()
        if (cleaned.isEmpty()) return null
        if (cleaned.length <= MAX_TITLE_CHARS) return cleaned
        val cut = cleaned.take(MAX_TITLE_CHARS - 1)
        val safe = if (cut.isNotEmpty() && Character.isHighSurrogate(cut.last())) cut.dropLast(1) else cut
        return safe + "…"
    }

    /** Returns a new list with [record] first, capped at [MAX_ENTRIES] (see [trim]). */
    fun append(history: List<TransferRecord>, record: TransferRecord): List<TransferRecord> =
        trim(listOf(record) + history)

    /**
     * Replaces the record with the same [TransferRecord.id] in place (keeping its position), or
     * adds [record] first if there is none. Records with id `0` are never matched.
     */
    fun upsert(history: List<TransferRecord>, record: TransferRecord): List<TransferRecord> {
        val index = if (record.id == 0L) -1 else history.indexOfFirst { it.id == record.id }
        if (index < 0) return append(history, record)
        return history.toMutableList().also { it[index] = record }
    }

    /**
     * Enforces [MAX_ENTRIES]. Records that were never sent (dropped, rate-limited) go first,
     * oldest first, so a chatty filtered app can't push real transfers out of the list; only
     * when there are none does the oldest record go.
     */
    private fun trim(history: List<TransferRecord>): List<TransferRecord> {
        if (history.size <= MAX_ENTRIES) return history
        val result = history.toMutableList()
        while (result.size > MAX_ENTRIES) {
            val notForwarded = result.indexOfLast { it.status.isNotForwarded }
            result.removeAt(if (notForwarded >= 0) notForwarded else result.lastIndex)
        }
        return result
    }
}
