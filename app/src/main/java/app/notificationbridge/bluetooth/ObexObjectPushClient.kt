/**
 * Bluetooth Classic transport: opens an RFCOMM socket to the OBEX Object Push service (UUID
 * `00001105-...`) on a paired device and runs the OBEX CONNECT/PUT/DISCONNECT exchange defined
 * in [ObexProtocol].
 *
 * Why OBEX Object Push specifically: it is the one Bluetooth file-transfer profile that legacy
 * "dumbphones" - this project was built and tested against an Alcatel 3080A - are able to
 * receive without any companion app, since it has been part of Bluetooth Classic since long
 * before BLE and modern transfer profiles existed.
 *
 * Reliability details worth knowing when touching this file:
 * - Blocking socket I/O (`connect()`, `InputStream.read()`) does not respond to coroutine
 *   cancellation. A watchdog coroutine force-closes the socket after [timeoutMillis], which is
 *   what turns a stuck read into a catchable [ObexException] instead of hanging the worker
 *   forever.
 * - Success is determined by the PUT FINAL response, not by a clean DISCONNECT. If the file was
 *   already accepted by the receiver but the teardown afterwards fails or times out, that is
 *   logged and swallowed rather than treated as a failed transfer - otherwise the caller would
 *   retry and the receiver would get the same file twice.
 *
 * Limitations: transfers are strictly sequential (no pipelining), and there is no support for
 * OBEX profiles other than Object Push (no OPP business-card exchange, no MAP/PBAP, etc.) -
 * this project only ever needed to push a text file.
 */
package app.notificationbridge.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import app.notificationbridge.R
import app.notificationbridge.model.FailureReason
import app.notificationbridge.model.PairedDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class ObexException(
    message: String,
    val responseCode: Int? = null,
    cause: Throwable? = null,
    val reason: FailureReason = FailureReason.UNKNOWN
) : IOException(message, cause)

class ObexObjectPushClient(
    private val context: Context,
    private val log: (String) -> Unit
) {

    companion object {
        val OPP_UUID: UUID = UUID.fromString("00001105-0000-1000-8000-00805F9B34FB")
        const val DEFAULT_TIMEOUT_MS: Long = 15000L
    }

    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    fun pairedDevices(): List<PairedDevice> {
        val unnamed = context.getString(R.string.device_unnamed)
        return adapter?.bondedDevices.orEmpty()
            .map { PairedDevice(it.name ?: unnamed, it.address) }
            .sortedBy { it.name.lowercase() }
    }

    /**
     * Sends one object to the paired device at [address]. Returns normally only when the
     * receiver acknowledged the PUT; every failure is an [ObexException] carrying a
     * [FailureReason].
     */
    @SuppressLint("MissingPermission")
    suspend fun push(
        address: String,
        fileName: String,
        mimeType: String,
        content: ByteArray,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS
    ) = withContext(Dispatchers.IO) {
        val bluetooth = adapter
            ?: throw ObexException("Bluetooth is not available", reason = FailureReason.BLUETOOTH_UNAVAILABLE)
        if (!bluetooth.isEnabled) {
            throw ObexException("Bluetooth is turned off", reason = FailureReason.BLUETOOTH_OFF)
        }
        val device = bluetooth.getRemoteDevice(address)
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            throw ObexException("Device is not paired", reason = FailureReason.NOT_PAIRED)
        }
        log("Bluetooth device found: ${device.address}")
        log("OBEX object name: $fileName")
        log("OBEX object size: ${content.size} bytes")
        bluetooth.cancelDiscovery()

        val socket = device.createRfcommSocketToServiceRecord(OPP_UUID)
        // Written by the watchdog coroutine and read on the IO thread, hence atomic.
        val timedOut = AtomicBoolean(false)
        // Watchdog: blocking socket I/O below doesn't respond to coroutine cancellation,
        // so we force-close the socket after timeoutMillis to unblock any stuck connect()/read().
        val watchdog = launch {
            delay(timeoutMillis)
            timedOut.set(true)
            log("OBEX transfer timed out after ${timeoutMillis}ms, forcing socket close")
            runCatching { socket.close() }
        }
        try {
            log("OBEX service lookup requested: $OPP_UUID")
            socket.connect()
            log("RFCOMM connection established")
            val input = socket.inputStream
            val output = socket.outputStream

            ObexProtocol.write(output, ObexProtocol.connectPacket())
            log("OBEX CONNECT sent")
            val connectResponse = ObexProtocol.readResponse(input)
            log("OBEX CONNECT response received: 0x%02X %s".format(connectResponse.code, connectResponse.label))
            if (connectResponse.code != ObexProtocol.SUCCESS) {
                throw ObexException(
                    "CONNECT rejected",
                    connectResponse.code,
                    reason = FailureReason.RECEIVER_REJECTED
                )
            }
            val maxPacket = ObexProtocol.serverMaxPacket(connectResponse).coerceIn(255, 65535)
            val connectionId = ObexProtocol.connectionId(connectResponse)
            sendPut(output, input, maxPacket, connectionId, fileName, mimeType, content)

            // The file is fully delivered and acknowledged by the receiver at this point.
            // A flaky/slow teardown from here on must NOT be treated as a failed transfer:
            // doing so used to trigger a retry that resent the whole file, causing the
            // receiver to see the same notification twice even though it already arrived.
            runCatching {
                ObexProtocol.write(output, ObexProtocol.disconnectPacket(connectionId))
                log("OBEX DISCONNECT sent")
                val disconnectResponse = ObexProtocol.readResponse(input)
                log("OBEX DISCONNECT response: 0x%02X %s".format(disconnectResponse.code, disconnectResponse.label))
                if (disconnectResponse.code != ObexProtocol.SUCCESS) {
                    log("DISCONNECT rejected (0x%02X), ignoring - file already delivered".format(disconnectResponse.code))
                }
            }.onFailure {
                log("DISCONNECT teardown failed, ignoring - file already delivered: ${it.message}")
            }
        } catch (e: ObexException) {
            throw e
        } catch (e: Exception) {
            if (timedOut.get()) {
                throw ObexException(
                    "Bluetooth/OBEX timeout after ${timeoutMillis}ms",
                    null,
                    e,
                    FailureReason.TIMED_OUT
                )
            }
            // Classified here, from the exception type only, so the rest of the app never parses messages.
            val reason = when (e) {
                is SecurityException -> FailureReason.PERMISSION_MISSING
                is IOException -> FailureReason.RECEIVER_UNAVAILABLE
                // A reply the parser cannot read (ObexProtocol uses require()): an incompatible receiver.
                is IllegalArgumentException -> FailureReason.RECEIVER_REJECTED
                else -> FailureReason.UNKNOWN
            }
            throw ObexException("Bluetooth/OBEX failure: ${e.message}", null, e, reason)
        } finally {
            watchdog.cancel()
            try {
                socket.close()
            } catch (_: Exception) {
            }
            log("BluetoothSocket closed")
        }
    }

    /**
     * Sends the object as one PUT, or as several packets when it doesn't fit in the receiver's
     * packet size. Only the first packet carries the name, type and length headers; every packet
     * repeats the Connection Id when the receiver gave one. The last packet is PUT-Final.
     */
    private fun sendPut(
        output: OutputStream,
        input: InputStream,
        maxPacket: Int,
        connectionId: Long?,
        name: String,
        type: String,
        content: ByteArray
    ) {
        val firstPacketHeaders = buildList {
            connectionId?.let { add(ObexProtocol.connectionHeader(it)) }
            add(ObexProtocol.nameHeader(name))
            add(ObexProtocol.typeHeader(type))
            add(ObexProtocol.lengthHeader(content.size))
        }
        var offset = 0
        var first = true
        do {
            // Room left for body bytes: packet size minus the 3-byte prefix, the headers that
            // go in this packet, and the 3-byte body header itself.
            val fixed = if (first) {
                firstPacketHeaders.sumOf { it.size }
            } else if (connectionId != null) {
                5
            } else {
                0
            }
            val available = maxPacket - 3 - fixed - 3
            if (available <= 0) {
                throw ObexException("MTU too small", reason = FailureReason.RECEIVER_REJECTED)
            }
            val count = min(available, content.size - offset)
            val isFinal = offset + count >= content.size

            val headers = mutableListOf<ByteArray>()
            if (first) {
                headers.addAll(firstPacketHeaders)
            } else if (connectionId != null) {
                headers.add(ObexProtocol.connectionHeader(connectionId))
            }
            headers.add(ObexProtocol.bodyHeader(content.copyOfRange(offset, offset + count), isFinal))

            val opcode = if (isFinal) ObexProtocol.PUT_FINAL else ObexProtocol.PUT
            ObexProtocol.write(output, ObexProtocol.packet(opcode, *headers.toTypedArray()))
            log("OBEX PUT${if (isFinal) " FINAL" else ""} sent: $count bytes, file=$name")

            val response = ObexProtocol.readResponse(input)
            log("OBEX PUT response received: 0x%02X %s".format(response.code, response.label))
            val expected = if (isFinal) ObexProtocol.SUCCESS else ObexProtocol.CONTINUE
            if (response.code != expected) {
                throw ObexException(
                    "Unexpected PUT response",
                    response.code,
                    reason = FailureReason.RECEIVER_REJECTED
                )
            }
            offset += count
            first = false
        } while (offset < content.size || first)
        log("Transfer completed: $name (${content.size} bytes)")
    }
}
