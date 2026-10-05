/**
 * Pure helpers behind message batching (see [app.notificationbridge.queue.BridgeRuntime]'s
 * cooldown logic): what makes two notifications "the same conversation", and how a batch of
 * them becomes one combined body.
 */
package app.notificationbridge.queue

import app.notificationbridge.model.NotificationData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessageBatch {

    /**
     * Notifications with the same app and the same title are treated as the same conversation.
     * `title` is what a messaging app puts the contact or group name in; `text` (the message
     * body) is deliberately excluded, since it's expected to differ between messages that should
     * still be batched together.
     */
    fun groupKey(n: NotificationData): String = "${n.packageName}|${n.title}"

    /**
     * One line per message, timestamped, oldest first. A single-message "batch" still goes
     * through this (called only when there's more than one, in practice) so the format only
     * needs to be defined once.
     */
    fun combinedText(messages: List<NotificationData>): String {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        return messages
            .sortedBy { it.timestamp }
            .joinToString("\n\n") { m ->
                val time = timeFormat.format(Date(m.timestamp))
                val text = m.text?.trim().orEmpty().ifBlank { "(sin texto)" }
                "[$time] $text"
            }
    }
}
