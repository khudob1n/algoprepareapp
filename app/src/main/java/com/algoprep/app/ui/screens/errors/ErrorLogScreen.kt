package com.algoprep.app.ui.screens.errors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoprep.app.R
import com.algoprep.app.ui.screens.result.errorTypeLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ErrorLogScreen(onBack: () -> Unit, viewModel: ErrorLogViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.errors_title), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ErrorFilter.entries.forEach { f ->
                        FilterChip(
                            selected = state.filter == f,
                            onClick = { viewModel.setFilter(f) },
                            label = { Text(stringResource(filterLabel(f))) },
                        )
                    }
                }
            }
            if (state.openByType.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            stringResource(R.string.errors_open_summary, state.openCount),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            state.openByType.joinToString(" · ") { (type, n) ->
                                stringResource(errorTypeLabel(type)) + " — " + n
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (!state.loading && state.rows.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.errors_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
            items(state.rows, key = { it.error.id }) { row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            row.taskTitle ?: stringResource(R.string.today_item_removed_task),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            stringResource(errorTypeLabel(row.error.type)) + " · " +
                                row.error.createdAt.atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        row.error.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        if (!row.error.resolved) {
                            TextButton(onClick = { viewModel.resolve(row.error.id) }) {
                                Text(stringResource(R.string.errors_mark_resolved))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun filterLabel(f: ErrorFilter) = when (f) {
    ErrorFilter.OPEN -> R.string.errors_filter_open
    ErrorFilter.RESOLVED -> R.string.errors_filter_resolved
    ErrorFilter.ALL -> R.string.errors_filter_all
}
