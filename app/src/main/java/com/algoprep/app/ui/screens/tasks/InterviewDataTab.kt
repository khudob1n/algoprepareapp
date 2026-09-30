package com.algoprep.app.ui.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import com.algoprep.app.domain.stats.CountRow
import com.algoprep.app.domain.stats.InterviewData

/** INTERVIEW DATA: counts over the user's own imported material, with an explicit "not a forecast" note. */
@Composable
fun InterviewDataTab(
    onOpenTask: (Long) -> Unit,
    onGoToImport: () -> Unit,
    viewModel: InterviewDataViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val data = state.data
    if (state.loading || data == null) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.data_title), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    stringResource(R.string.data_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        if (data.totalRecords == 0) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.data_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onGoToImport) { Text(stringResource(R.string.bank_go_import)) }
                }
            }
        } else {
            item { Totals(data) }
            item { CountSection(stringResource(R.string.data_topics), data.topics, data.unclassifiedRecords) }
            if (data.patterns.isNotEmpty()) {
                item { CountSection(stringResource(R.string.data_patterns), data.patterns, 0) }
            }
            if (data.companies.isNotEmpty()) {
                item { CountSection(stringResource(R.string.data_companies), data.companies.take(COMPANY_LIMIT), 0) }
            }
            item {
                Text(stringResource(R.string.data_top_problems), style = MaterialTheme.typography.titleMedium)
            }
            if (data.topProblems.isEmpty()) {
                item {
                    Text(stringResource(R.string.data_no_repeats), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(data.topProblems.size) { i ->
                val p = data.topProblems[i]
                val unknown = stringResource(R.string.data_unknown_source)
                Card(modifier = Modifier.fillMaxWidth().clickable { onOpenTask(p.taskId) }) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.data_mentions, p.mentions),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            p.sources.map { it ?: unknown }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val COMPANY_LIMIT = 10

@Composable
private fun Totals(data: InterviewData) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(data.totalRecords.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.data_total_records), style = MaterialTheme.typography.labelMedium)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(data.uniqueTasks.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.data_unique_tasks), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CountSection(title: String, rows: List<CountRow>, unclassified: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        val max = rows.maxOfOrNull { it.records }?.coerceAtLeast(1) ?: 1
        rows.forEach { row ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.data_count_row, row.records, row.uniqueTasks),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LinearProgressIndicator(progress = { row.records.toFloat() / max }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (unclassified > 0) {
            Text(
                stringResource(R.string.data_unclassified, unclassified),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
