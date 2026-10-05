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
import app.notificationbridge.model.TransferRecord
import app.notificationbridge.ui.InsetSurface
import app.notificationbridge.ui.PageHeader
import app.notificationbridge.ui.StatusPill
import app.notificationbridge.ui.verticalScrollbar
import java.text.DateFormat
import java.util.Date

private const val PAGE_SIZE = 15

@Composable
fun HistoryScreen(history: List<TransferRecord>, onClear: () -> Unit) {
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
        Text(
            stringResource(R.string.history_privacy_note),
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
                items(visible) { record -> HistoryRow(record, dateFormat) }
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
private fun HistoryRow(record: TransferRecord, dateFormat: DateFormat) {
    val statusColor = if (record.success) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error
    val statusText = if (record.success) stringResource(R.string.history_status_sent)
    else stringResource(R.string.history_status_failed)

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
            if (!record.success) {
                Text(
                    record.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
