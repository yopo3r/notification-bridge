/**
 * Sliding-window limiter, one independent budget per key (the source app's package name).
 *
 * Why it exists: a chatty or misbehaving app (a group chat, a download-progress notification
 * that isn't flagged as ongoing) could queue hundreds of transfers, and transfers are strictly
 * sequential over a slow Bluetooth link - so one app could starve everything else. Once an app
 * exceeds [maxEvents] within [windowMs], further notifications from it are skipped until the
 * window slides forward.
 *
 * Limitation: skipped notifications are dropped, not deferred. The limit is deliberately
 * generous so normal conversations never hit it.
 */
package app.notificationbridge.queue

class RateLimiter(private val maxEvents: Int, private val windowMs: Long) {

    private val events = HashMap<String, ArrayDeque<Long>>()

    /** Returns true and records the event if [key] is within budget; false if it is over it. */
    @Synchronized
    fun tryAcquire(key: String, now: Long = System.currentTimeMillis()): Boolean {
        val queue = events.getOrPut(key) { ArrayDeque() }
        while (queue.isNotEmpty() && now - queue.first() >= windowMs) queue.removeFirst()
        if (queue.size >= maxEvents) return false
        queue.addLast(now)
        return true
    }
}
