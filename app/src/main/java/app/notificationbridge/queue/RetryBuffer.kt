/**
 * Holds the few most recent failed items so "Retry now" can resend them, and nothing longer.
 *
 * Privacy: the item is a whole notification, body included, which the History deliberately never
 * keeps. So this is small ([maxItems]), short-lived ([windowMs]), memory-only, emptied by "Clear
 * history", and an item leaves it the moment it is retried. Whoever stores something here learns
 * which ids were dropped (expired or pushed out) so the History row stops offering a retry that
 * can no longer happen.
 *
 * Generic and clock-free (the caller passes `nowMs`) so the rules are unit tested.
 */
package app.notificationbridge.queue

class RetryBuffer<T>(private val maxItems: Int, private val windowMs: Long) {

    private class Entry<T>(val value: T, val expiresAtMs: Long)

    /** [expiresAtMs] is when the stored item stops being retryable; [dropped] are ids that no longer are. */
    data class Stored(val expiresAtMs: Long, val dropped: List<Long>)

    private val entries = LinkedHashMap<Long, Entry<T>>()

    @Synchronized
    fun put(id: Long, value: T, nowMs: Long): Stored {
        val dropped = mutableListOf<Long>()
        val expired = entries.filterValues { it.expiresAtMs <= nowMs }.keys
        expired.forEach { entries.remove(it); dropped += it }
        entries.remove(id)
        val expiresAt = nowMs + windowMs
        entries[id] = Entry(value, expiresAt)
        while (entries.size > maxItems) {
            val oldest = entries.keys.first()
            entries.remove(oldest)
            dropped += oldest
        }
        return Stored(expiresAt, dropped)
    }

    /** Removes and returns the item, or `null` if it is unknown or has expired. */
    @Synchronized
    fun take(id: Long, nowMs: Long): T? {
        val entry = entries.remove(id) ?: return null
        return if (entry.expiresAtMs > nowMs) entry.value else null
    }

    /** Forgets everything; returns the ids that were retryable. */
    @Synchronized
    fun clear(): List<Long> = entries.keys.toList().also { entries.clear() }
}
