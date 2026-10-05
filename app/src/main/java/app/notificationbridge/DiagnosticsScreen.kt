package app.notificationbridge

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.notificationbridge.ui.InsetSurface
import app.notificationbridge.ui.PageHeader
import app.notificationbridge.ui.verticalScrollbar

@Composable
fun DiagnosticsScreen(report: String) {
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.diagnostics_copied)
    val listState = rememberLazyListState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().verticalScrollbar(listState),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            PageHeader(stringResource(R.string.diagnostics_title), subtitle = stringResource(R.string.diagnostics_intro))
        }
        item {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("Notification Bridge diagnostics", report))
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.diagnostics_copy_button)) }
        }
        item {
            InsetSurface {
                Text(
                    report,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
