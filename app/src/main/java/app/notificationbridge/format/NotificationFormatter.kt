/**
 * Pure, side-effect-free conversion from a [app.notificationbridge.model.NotificationData]
 * into the plain-text file that actually gets pushed to the receiving device.
 *
 * Why it exists: the receiving "dumbphone" has no app of its own to interpret notification
 * metadata, so a notification has to become a self-describing text file - app name, title,
 * body and a full date/time footer - readable on any device that can open a `.txt` file
 * received over Bluetooth OBEX Object Push.
 *
 * It also derives a short, filesystem-safe file name per notification (ASCII-folded,
 * length-capped, suffixed with an incrementing counter to avoid collisions between
 * notifications posted within the same millisecond).
 *
 * Assumptions: input strings may contain arbitrary Unicode (emoji, accents); output must
 * remain valid on very old/limited file systems, hence the ASCII-folding via
 * [java.text.Normalizer] and the character allow-list in the file name.
 *
 * Limitations: this module has no knowledge of Bluetooth, OBEX, or delivery status - it only
 * produces bytes and a name. It is intentionally free of Android framework dependencies
 * beyond the `NotificationData` model, which keeps it trivial to unit test.
 */
package app.notificationbridge.format

import app.notificationbridge.model.NotificationData
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationFormatter {

    /** Default cap on the notification body; overridable per transfer (see [BridgeSettings.maxTextChars]). */
    const val DEFAULT_MAX_TEXT_CHARS = 700
    /**
     * Stand-ins written into the file when a notification has no title or no visible text. They
     * go to the receiver, whose language the app can't know, so they are plain English and live
     * here, in one place, rather than in string resources (this class has no Android context).
     */
    const val NO_TITLE = "(no title)"
    const val NO_CONTENT = "(no visible content)"

    private const val MAX_APP_LENGTH = 6
    private const val MAX_TITLE_LENGTH = 12
    private const val MAX_FILE_NAME_LENGTH = 40

    // Room kept for a "_N" suffix (up to "_99") that tells apart notifications with the same name
    // in the same second.
    private const val SUFFIX_RESERVE = 3

    private var lastBaseName: String? = null
    private var repeatCount = 0

    fun notificationToText(
        notification: NotificationData,
        maxTextChars: Int = DEFAULT_MAX_TEXT_CHARS
    ): String {
        val app = notification.appName
            .uppercase(Locale.getDefault())
            .take(24)

        val title = notification.title
            ?.trim()
            .orEmpty()
            .ifBlank { NO_TITLE }
            .take(80)

        val text = notification.text
            ?.trim()
            .orEmpty()
            .ifBlank { NO_CONTENT }
            .take(maxTextChars.coerceAtLeast(1))

        val time = SimpleDateFormat(
            "dd-MM-yyyy HH:mm:ss",
            Locale.getDefault()
        ).format(Date(notification.timestamp))

        return buildString {
            append(app)
            append('\n')
            append(title)
            append("\n\n")
            append(text)
            append("\n\n")
            append(time)
            append('\n')
        }
    }

    fun notificationToFile(
        notification: NotificationData,
        maxTextChars: Int = DEFAULT_MAX_TEXT_CHARS
    ): ByteArray {
        return notificationToText(notification, maxTextChars)
            .toByteArray(Charsets.UTF_8)
    }

    /**
     * File name in the order name, date, time: `NAME_Title_20261111_091105.txt`. The name is the
     * app abbreviation plus a shortened title. A repeat of the same name within the same second
     * gets `_2`, `_3`, ... so a receiver never overwrites one file with another.
     */
    @Synchronized
    fun generateFileName(
        notification: NotificationData,
        extension: String = ".txt"
    ): String {
        val app = applicationAbbreviation(notification)

        val title = sanitizeComponent(
            value = notification.title
                ?.takeIf { it.isNotBlank() }
                ?: "Notice",
            maxLength = MAX_FILE_NAME_LENGTH
        )

        val stamp = Date(notification.timestamp)
        val date = SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(stamp)
        val time = SimpleDateFormat("HHmmss", Locale.ROOT).format(stamp)

        // app_ + title + _date_time + extension, with room kept for the repeat suffix.
        val fixedLength = app.length + date.length + time.length + extension.length + 3 + SUFFIX_RESERVE
        val availableTitleLength = (MAX_FILE_NAME_LENGTH - fixedLength).coerceAtLeast(1)
        val shortTitle = title.take(availableTitleLength)

        val base = "${app}_${shortTitle}_${date}_$time"
        if (base == lastBaseName) {
            repeatCount++
        } else {
            lastBaseName = base
            repeatCount = 1
        }
        val suffix = if (repeatCount > 1) "_$repeatCount" else ""
        return "$base$suffix$extension"
    }

    private fun applicationAbbreviation(
        notification: NotificationData
    ): String {
        val abbreviation = when {
            notification.packageName.contains(
                "whatsapp",
                ignoreCase = true
            ) -> "WA"

            notification.packageName.contains(
                "telegram",
                ignoreCase = true
            ) -> "TG"

            notification.packageName.contains(
                "gmail",
                ignoreCase = true
            ) -> "Gmail"

            notification.packageName.contains(
                "calendar",
                ignoreCase = true
            ) -> "CAL"

            else -> notification.appName
        }

        return sanitizeComponent(
            value = abbreviation,
            maxLength = MAX_APP_LENGTH
        ).ifBlank {
            "APP"
        }
    }

    private fun sanitizeComponent(
        value: String,
        maxLength: Int
    ): String {
        val withoutDiacritics = Normalizer
            .normalize(value, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")

        return withoutDiacritics
            .replace("[^A-Za-z0-9_-]".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .trim('_', '-')
            .take(maxLength)
            .ifBlank { "Notice" }
    }
}
