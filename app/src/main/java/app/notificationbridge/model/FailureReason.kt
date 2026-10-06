/**
 * Why a transfer failed, in terms a user can act on, and the one thing the app offers to do about
 * it. Technical detail (exception text, OBEX codes) deliberately does not live here: History shows
 * only these categories, and Diagnostics reports their names.
 *
 * Pure JVM like the rest of the model package, so the policy below is unit tested.
 */
package app.notificationbridge.model

enum class FailureReason {
    /** No receiver has been chosen. */
    NO_RECEIVER,

    /** The phone reports no Bluetooth adapter. */
    BLUETOOTH_UNAVAILABLE,

    /** Bluetooth is switched off. */
    BLUETOOTH_OFF,

    /** The Bluetooth runtime permission (Android 12+) was refused or revoked. */
    PERMISSION_MISSING,

    /** The selected receiver is no longer bonded with this phone. */
    NOT_PAIRED,

    /** The connection could not be made, or dropped: receiver off, out of range, busy or not visible. */
    RECEIVER_UNAVAILABLE,

    /** The receiver did not answer within the time limit. */
    TIMED_OUT,

    /** The receiver answered but refused the file, or answered in a way the app cannot read. */
    RECEIVER_REJECTED,

    /** Not attempted: sending is paused for a few minutes after repeated failures. */
    PAUSED,

    /** Anything else. */
    UNKNOWN;

    /**
     * Retrying cannot help until the user changes something, so the app stops after the first
     * attempt instead of waiting through the backoff for two more that are certain to fail.
     */
    val needsUserAction: Boolean
        get() = when (this) {
            NO_RECEIVER, BLUETOOTH_UNAVAILABLE, BLUETOOTH_OFF, PERMISSION_MISSING, NOT_PAIRED -> true
            else -> false
        }

    /**
     * The notification is worth keeping briefly in memory so "Retry now" can resend it: the
     * failure is about the link, not about the user's setup or the file.
     */
    val retryable: Boolean
        get() = this == RECEIVER_UNAVAILABLE || this == TIMED_OUT || this == PAUSED

    /**
     * Counts towards the circuit breaker that pauses sending after repeated link failures. Setup
     * problems do not (fixing them must not be blocked by a pause), and neither does a transfer
     * that was never attempted because of the pause.
     */
    val countsTowardCircuitBreaker: Boolean get() = !needsUserAction && this != PAUSED

    /** The single action offered on a failed History row; `null` when there is nothing to do. */
    val action: FailureAction?
        get() = when (this) {
            NO_RECEIVER -> FailureAction.CHOOSE_RECEIVER
            BLUETOOTH_UNAVAILABLE -> null
            BLUETOOTH_OFF -> FailureAction.TURN_ON_BLUETOOTH
            PERMISSION_MISSING -> FailureAction.GRANT_PERMISSION
            NOT_PAIRED -> FailureAction.OPEN_BLUETOOTH_SETTINGS
            RECEIVER_UNAVAILABLE, TIMED_OUT, PAUSED -> FailureAction.RETRY_NOW
            RECEIVER_REJECTED -> FailureAction.SEND_COMPATIBILITY_TEST
            UNKNOWN -> FailureAction.OPEN_DIAGNOSTICS
        }
}

/** What a button on a failed row (or the listener notice) does; the UI decides how. */
enum class FailureAction {
    OPEN_BLUETOOTH_SETTINGS,
    GRANT_PERMISSION,
    TURN_ON_BLUETOOTH,
    CHOOSE_RECEIVER,
    SEND_COMPATIBILITY_TEST,
    RETRY_NOW,
    OPEN_DIAGNOSTICS,
    OPEN_NOTIFICATION_ACCESS
}
