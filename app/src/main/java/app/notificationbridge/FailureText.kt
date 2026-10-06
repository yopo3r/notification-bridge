/**
 * String resources for the failure model. Lives outside `model` (which is Android-free) and is
 * shared by History (Compose) and the dumbphone alert ([notification.ErrorNotifier]) so both say
 * the same thing.
 */
package app.notificationbridge

import app.notificationbridge.model.FailureAction
import app.notificationbridge.model.FailureReason

/** A few words: "Receiver unavailable". Used next to "Attempt 2 of 3". */
fun FailureReason.shortLabelRes(): Int = when (this) {
    FailureReason.NO_RECEIVER -> R.string.failure_short_no_receiver
    FailureReason.BLUETOOTH_UNAVAILABLE -> R.string.failure_short_bluetooth_unavailable
    FailureReason.BLUETOOTH_OFF -> R.string.failure_short_bluetooth_off
    FailureReason.PERMISSION_MISSING -> R.string.failure_short_permission_missing
    FailureReason.NOT_PAIRED -> R.string.failure_short_not_paired
    FailureReason.RECEIVER_UNAVAILABLE -> R.string.failure_short_receiver_unavailable
    FailureReason.TIMED_OUT -> R.string.failure_short_timed_out
    FailureReason.RECEIVER_REJECTED -> R.string.failure_short_receiver_rejected
    FailureReason.PAUSED -> R.string.failure_short_paused
    FailureReason.UNKNOWN -> R.string.failure_short_unknown
}

/** One plain sentence saying what happened and, where useful, what to check. */
fun FailureReason.explanationRes(): Int = when (this) {
    FailureReason.NO_RECEIVER -> R.string.failure_explain_no_receiver
    FailureReason.BLUETOOTH_UNAVAILABLE -> R.string.failure_explain_bluetooth_unavailable
    FailureReason.BLUETOOTH_OFF -> R.string.failure_explain_bluetooth_off
    FailureReason.PERMISSION_MISSING -> R.string.failure_explain_permission_missing
    FailureReason.NOT_PAIRED -> R.string.failure_explain_not_paired
    FailureReason.RECEIVER_UNAVAILABLE -> R.string.failure_explain_receiver_unavailable
    FailureReason.TIMED_OUT -> R.string.failure_explain_timed_out
    FailureReason.RECEIVER_REJECTED -> R.string.failure_explain_receiver_rejected
    FailureReason.PAUSED -> R.string.failure_explain_paused
    FailureReason.UNKNOWN -> R.string.failure_explain_unknown
}

fun FailureAction.labelRes(): Int = when (this) {
    FailureAction.OPEN_BLUETOOTH_SETTINGS -> R.string.failure_action_open_bluetooth_settings
    FailureAction.GRANT_PERMISSION -> R.string.failure_action_grant_permission
    FailureAction.TURN_ON_BLUETOOTH -> R.string.failure_action_turn_on_bluetooth
    FailureAction.CHOOSE_RECEIVER -> R.string.home_action_choose
    FailureAction.SEND_COMPATIBILITY_TEST -> R.string.failure_action_compatibility_test
    FailureAction.RETRY_NOW -> R.string.failure_action_retry_now
    FailureAction.OPEN_DIAGNOSTICS -> R.string.failure_action_open_diagnostics
    FailureAction.OPEN_NOTIFICATION_ACCESS -> R.string.failure_action_open_notification_access
}
