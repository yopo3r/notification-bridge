/**
 * Detects the same logical notification being posted again within a short window.
 *
 * Android and some apps re-post an identical notification within milliseconds of the original
 * (updates, re-alerts, grouping). Forwarding each repost would send the receiver the same file
 * several times. The caller picks the window per notification (short for messages, longer for
 * ringing calls); this class only keeps the fingerprints.
 *
 * Fingerprints are SHA-256 hashes, so raw notification text is never retained here.
 *
 * Assumption: [retentionMs] is at least as large as the largest window a caller will use;
 * otherwise a fingerprint could be forgotten before its window elapses.
 */
package app.notificationbridge.queue

import app.notificationbridge.model.NotificationData
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class DuplicateDetector(private val retentionMs: Long = 20_000L) {

    private val lastSeen = ConcurrentHashMap<String, Long>()

    /**
     * Records [n] and returns true if the same notification was seen within the last [windowMs].
     * Every sighting refreshes the timestamp, so a notification that keeps re-posting stays
     * suppressed for as long as the reposts arrive faster than [windowMs].
     */
    fun isDuplicate(n: NotificationData, windowMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        val purgeBefore = maxOf(retentionMs, windowMs)
        lastSeen.entries.removeIf { now - it.value > purgeBefore }
        val previous = lastSeen.put(fingerprint(n), now)
        return previous != null && now - previous <= windowMs
    }

    private fun fingerprint(n: NotificationData): String {
        val raw = "${n.packageName}|${n.notificationKey}|${n.title}|${n.text}"
        return MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
