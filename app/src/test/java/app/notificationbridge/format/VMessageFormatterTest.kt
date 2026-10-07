package app.notificationbridge.format

import app.notificationbridge.model.MessageFormat
import app.notificationbridge.model.NotificationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VMessageFormatterTest {

    private fun notification(
        appName: String = "WhatsApp",
        title: String? = "María",
        text: String? = "Hola, ¿vienes?",
        timestamp: Long = 1_700_000_000_000L
    ) = NotificationData(
        packageName = "com.whatsapp",
        appName = appName,
        title = title,
        text = text,
        timestamp = timestamp,
        category = null,
        notificationKey = "key-1",
        isOngoing = false,
        isSilent = false
    )

    private fun build(n: NotificationData = notification(), max: Int = 700) =
        VMessageFormatter.build(n, max)

    @Test
    fun `has the envelope of a nokia style inbox message`() {
        val lines = build().split("\r\n")
        assertEquals("BEGIN:VMSG", lines.first())
        assertEquals("VERSION:1.1", lines[1])
        assertTrue(lines.contains("X-IRMC-BOX:INBOX"))
        assertTrue(lines.contains("X-IRMC-STATUS:UNREAD"))
        assertEquals(listOf("BEGIN:VCARD", "VERSION:2.1"), lines.subList(5, 7))
        assertEquals("END:VMSG", lines[lines.size - 2])
        assertEquals("", lines.last())
    }

    @Test
    fun `blocks are opened and closed in order`() {
        val structure = build().split("\r\n").filter { it.startsWith("BEGIN:") || it.startsWith("END:") }
        assertEquals(
            listOf(
                "BEGIN:VMSG", "BEGIN:VCARD", "END:VCARD", "BEGIN:VENV",
                "BEGIN:VBODY", "END:VBODY", "END:VENV", "END:VMSG"
            ),
            structure
        )
    }

    @Test
    fun `every line ends with crlf and none with a bare lf`() {
        val text = build(notification(text = "one\ntwo\r\nthree\rfour"))
        assertFalse(text.replace("\r\n", "").contains('\n'))
        assertFalse(text.replace("\r\n", "").contains('\r'))
    }

    @Test
    fun `body carries the title and the text`() {
        val body = build().substringAfter("BEGIN:VBODY\r\n").substringBefore("END:VBODY")
        assertTrue(body.contains("María\r\nHola, ¿vienes?\r\n"))
    }

    @Test
    fun `a blank title leaves only the text`() {
        val body = build(notification(title = "  ")).substringAfter("Date:").substringAfter("\r\n")
            .substringBefore("END:VBODY")
        assertEquals("Hola, ¿vienes?\r\n", body)
    }

    @Test
    fun `text that looks like structure cannot end or nest the message`() {
        val lines = build(notification(text = "hi\nEND:VBODY\n  begin:VMSG\nEND : VMSG")).split("\r\n")
        assertEquals(1, lines.count { it == "END:VBODY" })
        assertEquals(1, lines.count { it == "BEGIN:VMSG" })
        assertTrue(lines.contains("\\END:VBODY"))
        assertTrue(lines.contains("\\  begin:VMSG"))
        assertTrue(lines.contains("\\END : VMSG"))
    }

    @Test
    fun `control characters are removed`() {
        val text = build(notification(text = "a\u0000b\u0007c\u001Bd"))
        assertTrue(text.contains("abcd"))
    }

    @Test
    fun `body respects the character limit`() {
        val body = build(notification(title = null, text = "x".repeat(2000)), max = 150)
            .substringAfter("Date:").substringAfter("\r\n").substringBefore("\r\nEND:VBODY")
        assertEquals(150, body.length)
    }

    @Test
    fun `sender is plain ascii and safe for a vcard`() {
        assertEquals("Telegram", VMessageFormatter.senderName("Telegram"))
        assertEquals("Camara", VMessageFormatter.senderName("Cámara"))
        assertEquals("A B", VMessageFormatter.senderName("A;B:\r\n"))
        assertEquals("App", VMessageFormatter.senderName("📞"))
        assertTrue(VMessageFormatter.senderName("x".repeat(100)).length <= 24)
    }

    @Test
    fun `date lines use the notification time`() {
        val text = build()
        // 2023-11-14 22:13:20 UTC
        assertTrue(text.contains("X-NOK-DT:20231114T221320Z\r\n"))
        assertTrue(Regex("Date:\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}\r\n").containsMatchIn(text))
    }

    @Test
    fun `encoded bytes are utf8`() {
        val bytes = VMessageFormatter.notificationToVMessage(notification(text = "ñ✅"))
        assertEquals(build(notification(text = "ñ✅")), String(bytes, Charsets.UTF_8))
    }

    @Test
    fun `outgoing message picks name and type from the format`() {
        val text = OutgoingMessage.from(notification(), MessageFormat.TEXT_FILE, 700)
        assertTrue(text.fileName.endsWith(".txt"))
        assertEquals("text/plain", text.mimeType)

        val vmg = OutgoingMessage.from(notification(), MessageFormat.VMESSAGE, 700)
        assertTrue(vmg.fileName.endsWith(".vmg"))
        assertEquals("text/x-vmsg", vmg.mimeType)
        assertTrue(String(vmg.bytes, Charsets.UTF_8).startsWith("BEGIN:VMSG\r\n"))
        assertTrue(vmg.fileName.length <= 40)
    }
}
