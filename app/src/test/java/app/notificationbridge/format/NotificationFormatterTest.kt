package app.notificationbridge.format

import app.notificationbridge.model.NotificationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NotificationFormatter] has no Android framework dependency, so these run as plain JVM unit
 * tests (no emulator/Robolectric needed). They cover the two properties that matter most for a
 * receiver with a very limited file system: the generated file name must always be short and
 * use only safe ASCII characters, and the text body must survive whatever Unicode a real
 * notification throws at it.
 */
class NotificationFormatterTest {

    private fun notification(
        packageName: String = "com.example.app",
        appName: String = "Example",
        title: String? = "Title",
        text: String? = "Body",
        timestamp: Long = 1_700_000_000_000L
    ) = NotificationData(
        packageName = packageName,
        appName = appName,
        title = title,
        text = text,
        timestamp = timestamp,
        category = null,
        notificationKey = "key-1",
        isOngoing = false,
        isSilent = false
    )

    @Test
    fun `file name only contains safe ASCII characters`() {
        val name = NotificationFormatter.generateFileName(
            notification(title = "Reunión de la tarde \u00f1 café ✅")
        )
        assertTrue(name.matches(Regex("[A-Za-z0-9_.-]+")))
        assertTrue(name.endsWith(".txt"))
    }

    @Test
    fun `file name stays within the length budget for a long title`() {
        val name = NotificationFormatter.generateFileName(
            notification(title = "A".repeat(200))
        )
        assertTrue("expected short file name, got ${name.length} chars: $name", name.length <= 40)
    }

    @Test
    fun `blank title falls back to a placeholder instead of an empty component`() {
        val name = NotificationFormatter.generateFileName(notification(title = ""))
        assertFalse(name.contains("__"))
    }

    @Test
    fun `known apps get a short recognizable abbreviation`() {
        val name = NotificationFormatter.generateFileName(
            notification(packageName = "com.whatsapp", appName = "WhatsApp")
        )
        assertTrue(name.startsWith("WA_"))
    }

    @Test
    fun `two notifications posted in the same millisecond get different file names`() {
        val n = notification()
        val first = NotificationFormatter.generateFileName(n)
        val second = NotificationFormatter.generateFileName(n)
        assertFalse(first == second)
    }

    @Test
    fun `text body preserves unicode content`() {
        val body = NotificationFormatter.notificationToText(
            notification(title = "Café con Ñandú", text = "Reunión a las 3pm ✅ 你好")
        )
        assertTrue(body.contains("Café con Ñandú"))
        assertTrue(body.contains("Reunión a las 3pm ✅ 你好"))
    }

    @Test
    fun `missing title and text fall back to readable placeholders`() {
        val body = NotificationFormatter.notificationToText(
            notification(title = null, text = null)
        )
        assertTrue(body.contains("(Sin titulo)"))
        assertTrue(body.contains("(Sin contenido visible)"))
    }

    @Test
    fun `very long body text is capped, not rejected`() {
        val body = NotificationFormatter.notificationToText(
            notification(text = "x".repeat(5000))
        )
        assertTrue(body.length < 5000)
    }

    @Test
    fun `a custom max length overrides the default`() {
        val body = NotificationFormatter.notificationToText(
            notification(text = "x".repeat(5000)),
            maxTextChars = 50
        )
        // Template is "app\ntitle\n\ntext\n\ntime\n", so the text segment is the middle part.
        val textSegment = body.split("\n\n")[1]
        assertEquals(50, textSegment.length)
    }

    @Test
    fun `file bytes round-trip as UTF-8 text`() {
        val n = notification(title = "Título", text = "Contenido")
        val bytes = NotificationFormatter.notificationToFile(n)
        val decoded = String(bytes, Charsets.UTF_8)
        assertEquals(NotificationFormatter.notificationToText(n), decoded)
    }
}
