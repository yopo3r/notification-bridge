/**
 * Maps whatever a transfer threw to a [FailureReason]. The transport tags the failures it
 * understands ([app.notificationbridge.bluetooth.ObexException.reason]); this covers the rest.
 * Message text is never inspected: it is localized, version-dependent and not a contract.
 */
package app.notificationbridge.queue

import app.notificationbridge.bluetooth.ObexException
import app.notificationbridge.model.FailureReason
import java.io.IOException

object FailureClassifier {

    fun classify(error: Throwable?): FailureReason = when (error) {
        is ObexException -> error.reason
        // Reading a bond state or opening a socket without BLUETOOTH_CONNECT.
        is SecurityException -> FailureReason.PERMISSION_MISSING
        // Raw socket trouble that was not wrapped: could not reach, or lost, the receiver.
        is IOException -> FailureReason.RECEIVER_UNAVAILABLE
        else -> FailureReason.UNKNOWN
    }
}
