package app.notificationbridge.bluetooth

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class ObexProtocolTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private fun response(code: Int, vararg rest: Int): ObexProtocol.Response {
        val length = 3 + rest.size
        return ObexProtocol.Response(code, bytes(code, length shr 8, length and 255, *rest))
    }

    // A CONNECT response: version 1.0, no flags, 4096-byte packets, then optional headers.
    private fun connectResponse(vararg headers: Int) =
        response(0xA0, 0x10, 0x00, 0x10, 0x00, *headers)

    @Test
    fun `connect packet announces version 1_0 and the requested packet size`() {
        assertArrayEquals(bytes(0x80, 0, 7, 0x10, 0, 0x10, 0x00), ObexProtocol.connectPacket())
        assertArrayEquals(bytes(0x80, 0, 7, 0x10, 0, 0xFF, 0xFF), ObexProtocol.connectPacket(65535))
    }

    @Test
    fun `name header is UTF-16BE with a terminating null`() {
        // 3 bytes of header + "a" (00 61) + terminator (00 00)
        assertArrayEquals(bytes(0x01, 0, 7, 0x00, 0x61, 0x00, 0x00), ObexProtocol.nameHeader("a"))
    }

    @Test
    fun `type header is ASCII with a terminating null`() {
        val header = ObexProtocol.typeHeader("text/plain")
        assertEquals(0x42, header[0].toInt() and 255)
        assertEquals(3 + "text/plain".length + 1, ((header[1].toInt() and 255) shl 8) or (header[2].toInt() and 255))
        assertEquals(0, header.last().toInt())
        assertEquals("text/plain", String(header, 3, header.size - 4, Charsets.US_ASCII))
    }

    @Test
    fun `four byte headers are big endian`() {
        assertArrayEquals(bytes(0xC3, 0, 0, 0x01, 0x2C), ObexProtocol.lengthHeader(300))
        assertArrayEquals(bytes(0xCB, 0xFF, 0xFF, 0xFF, 0xFF), ObexProtocol.connectionHeader(0xFFFFFFFFL))
    }

    @Test
    fun `body header uses End-of-Body only for the final chunk`() {
        assertEquals(0x48, ObexProtocol.bodyHeader(bytes(1, 2), final = false)[0].toInt())
        assertEquals(0x49, ObexProtocol.bodyHeader(bytes(1, 2), final = true)[0].toInt())
        assertArrayEquals(bytes(0x49, 0, 5, 1, 2), ObexProtocol.bodyHeader(bytes(1, 2), final = true))
    }

    @Test
    fun `packet length counts the whole packet`() {
        val packet = ObexProtocol.packet(ObexProtocol.PUT_FINAL, bytes(0x49, 0, 5, 1, 2), bytes(0xCB, 0, 0, 0, 7))
        assertEquals(0x82, packet[0].toInt() and 255)
        assertEquals(13, ((packet[1].toInt() and 255) shl 8) or (packet[2].toInt() and 255))
        assertEquals(13, packet.size)
    }

    @Test
    fun `packet larger than 65535 bytes is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            ObexProtocol.packet(ObexProtocol.PUT, ByteArray(65533))
        }
        assertEquals(65535, ObexProtocol.packet(ObexProtocol.PUT, ByteArray(65532)).size)
    }

    @Test
    fun `disconnect packet carries the connection id only when there is one`() {
        assertArrayEquals(bytes(0x81, 0, 3), ObexProtocol.disconnectPacket(null))
        assertArrayEquals(bytes(0x81, 0, 8, 0xCB, 0, 0, 0, 42), ObexProtocol.disconnectPacket(42))
    }

    @Test
    fun `readResponse returns the code and the whole packet`() {
        val input = ByteArrayInputStream(bytes(0xA0, 0, 5, 0xAA, 0xBB, 0x99))
        val response = ObexProtocol.readResponse(input)
        assertEquals(0xA0, response.code)
        assertArrayEquals(bytes(0xA0, 0, 5, 0xAA, 0xBB), response.bytes)
        assertEquals("Success", response.label)
        // Whatever follows the packet is left unread.
        assertEquals(0x99, input.read())
    }

    @Test
    fun `readResponse rejects a length shorter than the prefix`() {
        assertThrows(IllegalArgumentException::class.java) {
            ObexProtocol.readResponse(ByteArrayInputStream(bytes(0xA0, 0, 2)))
        }
    }

    @Test
    fun `readResponse fails on a truncated packet`() {
        assertThrows(EOFException::class.java) {
            ObexProtocol.readResponse(ByteArrayInputStream(bytes(0xA0, 0, 9, 1, 2)))
        }
        assertThrows(EOFException::class.java) {
            ObexProtocol.readResponse(ByteArrayInputStream(bytes(0xA0)))
        }
    }

    @Test
    fun `serverMaxPacket reads the negotiated size`() {
        assertEquals(4096, ObexProtocol.serverMaxPacket(connectResponse()))
        assertEquals(0xFFFF, ObexProtocol.serverMaxPacket(response(0xA0, 0x10, 0, 0xFF, 0xFF)))
    }

    @Test
    fun `serverMaxPacket refuses a response too short to hold it`() {
        assertThrows(IllegalArgumentException::class.java) {
            ObexProtocol.serverMaxPacket(response(0xA0, 0x10))
        }
    }

    @Test
    fun `connectionId is found right after the CONNECT fields`() {
        val r = connectResponse(0xCB, 0, 0, 0, 42)
        assertEquals(42L, ObexProtocol.connectionId(r))
    }

    @Test
    fun `connectionId is unsigned`() {
        val r = connectResponse(0xCB, 0xFF, 0xFF, 0xFF, 0xFF)
        assertEquals(4294967295L, ObexProtocol.connectionId(r))
    }

    @Test
    fun `connectionId skips headers of every encoding that come first`() {
        val r = connectResponse(
            0x46, 0, 5, 0xAA, 0xBB, // Target: byte sequence, 5 bytes
            0x01, 0, 5, 0, 0x61,    // Name: text, 5 bytes
            0x94, 0x07,             // one-byte value
            0xC3, 0, 0, 0, 9,       // Length: four-byte value
            0xCB, 0, 0, 1, 0        // Connection Id = 256
        )
        assertEquals(256L, ObexProtocol.connectionId(r))
    }

    @Test
    fun `connectionId is null when the receiver sent none`() {
        assertNull(ObexProtocol.connectionId(connectResponse()))
        assertNull(ObexProtocol.connectionId(connectResponse(0xC3, 0, 0, 0, 9)))
    }

    @Test
    fun `connectionId is null rather than reading past a damaged header`() {
        // Declared length runs past the end of the packet.
        assertNull(ObexProtocol.connectionId(connectResponse(0x46, 0, 50, 1)))
        // Declared length smaller than the header itself would loop forever if it were trusted.
        assertNull(ObexProtocol.connectionId(connectResponse(0x46, 0, 1, 1)))
        // Four-byte value cut short.
        assertNull(ObexProtocol.connectionId(connectResponse(0xCB, 0, 0)))
        // Variable header with no room for its length.
        assertNull(ObexProtocol.connectionId(connectResponse(0x46)))
    }

    @Test
    fun `write sends exactly the given bytes`() {
        val out = ByteArrayOutputStream()
        ObexProtocol.write(out, bytes(1, 2, 3))
        assertArrayEquals(bytes(1, 2, 3), out.toByteArray())
    }

    @Test
    fun `response labels name the codes the app reports`() {
        assertEquals("Continue", ObexProtocol.responseLabel(0x90))
        assertEquals("Success", ObexProtocol.responseLabel(0xA0))
        assertEquals("Bad Request", ObexProtocol.responseLabel(0xC0))
        assertEquals("Forbidden", ObexProtocol.responseLabel(0xC3))
        assertEquals("OBEX response", ObexProtocol.responseLabel(0xD0))
    }
}
