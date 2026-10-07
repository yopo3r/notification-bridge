/**
 * The file one notification becomes on the wire: a name, an OBEX type and the bytes. This is the
 * single place that decides how [MessageFormat] maps to those three things, so the queue worker
 * and the Receiver screen's test send can never disagree.
 */
package app.notificationbridge.format

import app.notificationbridge.model.MessageFormat
import app.notificationbridge.model.NotificationData

class OutgoingMessage(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray
) {
    companion object {
        fun from(
            notification: NotificationData,
            format: MessageFormat,
            maxTextChars: Int
        ): OutgoingMessage = when (format) {
            MessageFormat.TEXT_FILE -> OutgoingMessage(
                NotificationFormatter.generateFileName(notification),
                "text/plain",
                NotificationFormatter.notificationToFile(notification, maxTextChars)
            )
            MessageFormat.VMESSAGE -> OutgoingMessage(
                NotificationFormatter.generateFileName(notification, VMessageFormatter.FILE_EXTENSION),
                VMessageFormatter.MIME_TYPE,
                VMessageFormatter.notificationToVMessage(notification, maxTextChars)
            )
        }
    }
}
