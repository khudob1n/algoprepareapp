package com.algoprep.app.ui.screens.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoprep.app.R
import com.algoprep.app.domain.model.BatchStatus
import com.algoprep.app.domain.model.ImportBatch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Step 1 of Import -> Parse -> Review -> Save: choose a file or paste text. */
@Composable
fun ImportTab(onOpenReview: () -> Unit, viewModel: ImportHomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { viewModel.onFilePicked(it) }
    LaunchedEffect(viewModel) { viewModel.toReview.collect { onOpenReview() } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.import_title), style = MaterialTheme.typography.headlineSmall) }
        item {
            Text(
                stringResource(R.string.import_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.parsing) {
                    Text(stringResource(R.string.import_choose_file))
                }
                state.fileName?.let { name ->
                    Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                    TextButton(onClick = viewModel::clearFile) { Text(stringResource(R.string.import_remove_file)) }
                }
            }
        }
        item {
            OutlinedTextField(
                value = state.pastedText,
                onValueChange = viewModel::setPastedText,
                label = { Text(stringResource(R.string.import_paste)) },
                supportingText = { Text(stringResource(R.string.import_formats)) },
                enabled = state.fileText.isNullOrBlank() && !state.parsing,
                minLines = 5,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        state.error?.let { error ->
            item { Text(stringResource(errorText(error)), color = MaterialTheme.colorScheme.error) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::parse, enabled = state.hasInput && !state.parsing) {
                    Text(stringResource(R.string.import_parse))
                }
                if (state.parsing) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text(stringResource(R.string.import_parsing))
                }
            }
        }
        if (state.history.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.import_history),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(state.history, key = { it.id }) { batch ->
                BatchRow(batch, onUndo = { viewModel.rollback(batch.id) })
            }
        }
    }
}

@Composable
private fun BatchRow(batch: ImportBatch, onUndo: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(batch.sourceName.ifBlank { stringResource(R.string.import_pasted_name) }, style = MaterialTheme.typography.titleSmall)
            Text(
                batch.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.import_batch_counts, batch.saved, batch.merged, batch.skipped), style = MaterialTheme.typography.bodyMedium)
            if (batch.status == BatchStatus.SAVED) {
                TextButton(onClick = onUndo) { Text(stringResource(R.string.import_undo)) }
            } else {
                Text(stringResource(R.string.import_undone), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

private fun errorText(e: ImportError) = when (e) {
    ImportError.EMPTY_INPUT -> R.string.import_error_empty
    ImportError.UNREADABLE_FILE -> R.string.import_error_unreadable
    ImportError.TOO_LARGE -> R.string.import_error_too_large
    ImportError.NOTHING_FOUND -> R.string.import_error_nothing
    ImportError.FAILED -> R.string.import_error_failed
}
