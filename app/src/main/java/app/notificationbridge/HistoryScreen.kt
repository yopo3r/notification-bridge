package app.notificationbridge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.notificationbridge.model.DropReason
import app.notificationbridge.model.FailureAction
import app.notificationbridge.model.FailureReason
import app.notificationbridge.model.TransferRecord
import app.notificationbridge.model.TransferStatus
import app.notificationbridge.ui.InsetSurface
import app.notificationbridge.ui.PageHeader
import app.notificationbridge.ui.StatusPill
import app.notificationbridge.ui.verticalScrollbar
import java.text.DateFormat
import java.util.Date

private const val PAGE_SIZE = 15

@Composable
fun HistoryScreen(
    state: HistoryRuntimeState,
    bridgeEnabled: Boolean,
    hasReceiver: Boolean,
    onClear: () -> Unit,
    onAction: (FailureAction, TransferRecord?) -> Unit
) {
    val history = state.history
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    var visibleCount by rememberSaveable { mutableIntStateOf(PAGE_SIZE) }
    val visible = remember(history, visibleCount) { history.take(visibleCount) }
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            PageHeader(stringResource(R.string.history_title), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onClear, enabled = history.isNotEmpty()) {
                Text(stringResource(R.string.history_clear))
            }
        }
        if (bridgeEnabled && !state.listenerConnected) {
            // Not a transfer failure, but the commonest reason nothing shows up below.
            InsetSurface {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        stringResource(
                            if (state.notificationAccess) R.string.history_listener_disconnected
                            else R.string.history_listener_no_access
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = { onAction(FailureAction.OPEN_NOTIFICATION_ACCESS, null) }) {
                        Text(stringResource(FailureAction.OPEN_NOTIFICATION_ACCESS.labelRes()))
                    }
                }
            }
        }
        Text(
            stringResource(R.string.history_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            stringResource(R.string.history_status_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (history.isEmpty()) {
            InsetSurface(Modifier.padding(top = 8.dp)) {
                Text(
                    stringResource(R.string.history_empty),
                    modifier = Modifier.padding(20.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).verticalScrollbar(listState),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visible) { record -> HistoryRow(record, dateFormat, hasReceiver, onAction) }
                if (visibleCount < history.size) {
                    item {
                        TextButton(onClick = { visibleCount += PAGE_SIZE }) {
                            val remaining = history.size - visibleCount
                            Text(pluralStringResource(R.plurals.action_show_more, remaining, remaining))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    record: TransferRecord,
    dateFormat: DateFormat,
    hasReceiver: Boolean,
    onAction: (FailureAction, TransferRecord?) -> Unit
) {
    val statusColor = when (record.status) {
        TransferStatus.TRANSFERRED -> MaterialTheme.colorScheme.primary
        TransferStatus.FAILED -> MaterialTheme.colorScheme.error
        TransferStatus.QUEUED, TransferStatus.CONNECTING, TransferStatus.BATCHED ->
            MaterialTheme.colorScheme.secondary
        TransferStatus.DROPPED, TransferStatus.RATE_LIMITED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusText = stringResource(record.status.labelRes())
    val count = record.messageCount
    val combined = if (count > 1) pluralStringResource(R.plurals.history_detail_combined, count, count) else null
    val detail: String? = when (record.status) {
        TransferStatus.FAILED -> stringResource((record.failure ?: FailureReason.UNKNOWN).explanationRes())
        TransferStatus.CONNECTING -> when {
            record.maxAttempts <= 1 || record.attempt <= 0 -> combined
            record.failure != null -> stringResource(
                R.string.history_attempt_reason, record.attempt, record.maxAttempts,
                stringResource(record.failure.shortLabelRes())
            )
            else -> stringResource(R.string.history_attempt, record.attempt, record.maxAttempts)
        }
        TransferStatus.DROPPED -> record.dropReason?.let { stringResource(it.labelRes()) }
        TransferStatus.RATE_LIMITED -> stringResource(R.string.history_detail_rate_limited)
        TransferStatus.BATCHED -> pluralStringResource(R.plurals.history_detail_batched, count, count)
        else -> combined
    }

    val action = failureActionFor(record, hasReceiver, System.currentTimeMillis())

    InsetSurface(Modifier.semantics(mergeDescendants = true) {}) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                listOfNotNull(record.appName, record.title).joinToString(" — "),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                StatusPill(statusText, statusColor)
                Text(
                    dateFormat.format(Date(record.timestamp)),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!detail.isNullOrEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.status == TransferStatus.FAILED) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (action != null) {
                // Outside the merged row semantics' reach would be better for a11y, but the
                // button stays a single, focusable control either way.
                OutlinedButton(onClick = { onAction(action, record) }) {
                    Text(stringResource(action.labelRes()))
                }
            }
        }
    }
}

private fun TransferStatus.labelRes() = when (this) {
    TransferStatus.QUEUED -> R.string.history_status_queued
    TransferStatus.CONNECTING -> R.string.history_status_connecting
    TransferStatus.TRANSFERRED -> R.string.history_status_transferred
    TransferStatus.FAILED -> R.string.history_status_failed
    TransferStatus.DROPPED -> R.string.history_status_dropped
    TransferStatus.RATE_LIMITED -> R.string.history_status_rate_limited
    TransferStatus.BATCHED -> R.string.history_status_batched
}

private fun DropReason.labelRes() = when (this) {
    DropReason.ONGOING -> R.string.history_drop_ongoing
    DropReason.SILENT -> R.string.history_drop_silent
    DropReason.CALLS_DISABLED -> R.string.history_drop_calls_disabled
}

/**
 * The one button a failed row offers, or `null`. "Retry now" disappears once the notification is
 * no longer held; "Send compatibility test" needs a receiver, so without one the useful step is
 * choosing it.
 */
internal fun failureActionFor(record: TransferRecord, hasReceiver: Boolean, nowMs: Long): FailureAction? {
    if (record.status != TransferStatus.FAILED) return null
    val action = (record.failure ?: FailureReason.UNKNOWN).action ?: return null
    return when {
        action == FailureAction.RETRY_NOW && (record.retryUntil ?: 0L) <= nowMs -> null
        action == FailureAction.SEND_COMPATIBILITY_TEST && !hasReceiver -> FailureAction.CHOOSE_RECEIVER
        else -> action
    }
}
