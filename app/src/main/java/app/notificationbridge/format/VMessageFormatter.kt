/**
 * Builds a vMessage (`.vmg`) text message from a notification, for receivers that file a pushed
 * vMessage straight into their SMS inbox instead of leaving it as a loose document.
 *
 * vMessage is the old IrMC / Nokia container for one SMS: an envelope with the sender as a
 * vCard and the text in a body block. Several dialects exist; this one follows the form used by
 * Nokia Series 40 phones (version 1.1, an inbox message flagged as unread), which other
 * feature-phone makers generally accept too. Whether a given receiver does is something only a
 * test on that device can tell, which is why [app.notificationbridge.model.MessageFormat.TEXT_FILE]
 * stays the default.
 *
 * Like [NotificationFormatter], this is pure JVM: it produces bytes and nothing else.
 *
 * Choices worth knowing about:
 * - Lines are CRLF-terminated, as the format requires.
 * - A body line that would read as a structural keyword (`BEGIN:` / `END:`) is prefixed with a
 *   backslash, as the specification says, so notification text can never end the message early
 *   or start a nested one.
 * - The sender is the app's name, folded to plain ASCII, because vCard 2.1 has no portable way
 *   to say which charset a name is in. The body is UTF-8.
 */
package app.notificationbridge.format

import app.notificationbridge.model.NotificationData
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object VMessageFormatter {

    const val MIME_TYPE = "text/x-vmsg"
    const val FILE_EXTENSION = ".vmg"

    private const val CRLF = "\r\n"
    private const val MAX_SENDER_LENGTH = 24
    private val STRUCTURAL_LINE = Regex("(?i)^\\s*(BEGIN|END)\\s*:")
    private val CONTROL_CHARS = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]")

    fun notificationToVMessage(
        notification: NotificationData,
        maxTextChars: Int = NotificationFormatter.DEFAULT_MAX_TEXT_CHARS
    ): ByteArray = build(notification, maxTextChars).toByteArray(Charsets.UTF_8)

    internal fun build(
        notification: NotificationData,
        maxTextChars: Int
    ): String {
        val sender = senderName(notification.appName)
        val date = Date(notification.timestamp)
        val utc = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(date)
        val local = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ROOT).format(date)

        val lines = listOf(
            "BEGIN:VMSG",
            "VERSION:1.1",
            "X-IRMC-STATUS:UNREAD",
            "X-IRMC-BOX:INBOX",
            "X-NOK-DT:$utc",
            "BEGIN:VCARD",
            "VERSION:2.1",
            "N:$sender",
            "TEL:$sender",
            "END:VCARD",
            "BEGIN:VENV",
            "BEGIN:VBODY",
            "Date:$local"
        ) + bodyLines(notification, maxTextChars) + listOf(
            "END:VBODY",
            "END:VENV",
            "END:VMSG"
        )
        return lines.joinToString(CRLF, postfix = CRLF)
    }

    /** Title (if any) on the first line, then the text, capped to [maxTextChars] like a text file. */
    private fun bodyLines(notification: NotificationData, maxTextChars: Int): List<String> {
        val title = notification.title?.trim().orEmpty().take(80)
        val text = notification.text?.trim().orEmpty()
            .ifBlank { "(Sin contenido visible)" }
            .take(maxTextChars.coerceAtLeast(1))
        val body = if (title.isBlank()) text else "$title\n$text"
        return body
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(CONTROL_CHARS, "")
            .split('\n')
            .map { if (STRUCTURAL_LINE.containsMatchIn(it)) "\\$it" else it }
    }

    /** ASCII, single line, no characters that mean something in a vCard. */
    internal fun senderName(appName: String): String {
        val folded = Normalizer.normalize(appName, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
            .replace("[^A-Za-z0-9 ._-]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
            .take(MAX_SENDER_LENGTH)
            .trim()
        return folded.ifBlank { "App" }
    }
}
