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

    /** Returns a new list with [record] first, capped at [MAX_ENTRIES] (oldest entries drop off). */
    fun append(history: List<TransferRecord>, record: TransferRecord): List<TransferRecord> =
        (listOf(record) + history).take(MAX_ENTRIES)
}
