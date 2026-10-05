/**
 * Pure age-based pruning for transfer history (the "auto-clear" setting).
 *
 * Deliberately generic and dependency-free so it can be unit-tested directly: given a list and a
 * way to read each item's timestamp, it returns only the items at or after a cutoff. The caller
 * ([app.notificationbridge.queue.BridgeRuntime]) decides what the cutoff is and which lists to
 * prune.
 */
package app.notificationbridge.queue

object RetentionPolicy {

    /** Keeps only items whose timestamp is at or after [cutoffMillis]. */
    fun <T> pruneOlderThan(items: List<T>, cutoffMillis: Long, timestampOf: (T) -> Long): List<T> =
        items.filter { timestampOf(it) >= cutoffMillis }

    /** The oldest timestamp still kept when pruning anything older than [hours] hours. */
    fun cutoffFor(hours: Int, nowMillis: Long): Long = nowMillis - hours * 3_600_000L
}
