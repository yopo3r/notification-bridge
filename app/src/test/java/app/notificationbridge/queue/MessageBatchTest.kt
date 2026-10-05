package app.notificationbridge.queue

import app.notificationbridge.model.NotificationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageBatchTest {

    private fun notif(pkg: String, title: String?, text: String?, ts: Long) = NotificationData(
        packageName = pkg,
        appName = "App",
        title = title,
        text = text,
        timestamp = ts,
        category = null,
        notificationKey = "k-$ts",
        isOngoing = false,
        isSilent = false
    )

    @Test
    fun `same app and title share a group key regardless of body`() {
        val a = notif("com.chat", "Ana", "hola", 0)
        val b = notif("com.chat", "Ana", "chau", 1000)
        assertEquals(MessageBatch.groupKey(a), MessageBatch.groupKey(b))
    }

    @Test
    fun `different title is a different conversation`() {
        val a = notif("com.chat", "Ana", "hola", 0)
        val b = notif("com.chat", "Beto", "hola", 0)
        assertNotEquals(MessageBatch.groupKey(a), MessageBatch.groupKey(b))
    }

    @Test
    fun `different app is a different conversation even with the same title`() {
        val a = notif("com.chat", "Ana", "hola", 0)
        val b = notif("com.other", "Ana", "hola", 0)
        assertNotEquals(MessageBatch.groupKey(a), MessageBatch.groupKey(b))
    }

    @Test
    fun `combined text has one line per message, oldest first`() {
        val messages = listOf(
            notif("com.chat", "Ana", "segundo", 60_000),
            notif("com.chat", "Ana", "primero", 0)
        )
        val combined = MessageBatch.combinedText(messages)
        val firstIndex = combined.indexOf("primero")
        val secondIndex = combined.indexOf("segundo")
        assertTrue(firstIndex >= 0 && secondIndex > firstIndex)
    }

    @Test
    fun `a blank message body becomes a readable placeholder, not an empty line`() {
        val combined = MessageBatch.combinedText(listOf(notif("com.chat", "Ana", null, 0)))
        assertTrue(!combined.trim().endsWith("]"))
    }
}
