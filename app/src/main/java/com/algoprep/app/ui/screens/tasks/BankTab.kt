package com.algoprep.app.ui.screens.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.Task
import com.algoprep.app.domain.model.TaskStatus
import com.algoprep.app.ui.components.difficultyLabel

@Composable
fun BankTab(onOpenTask: (Long) -> Unit, onGoToImport: () -> Unit, viewModel: BankViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }
        return
    }
    val topicTitles = state.topics.associate { it.id to it.title }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = state.filter.query,
                    onValueChange = viewModel::setQuery,
                    label = { Text(stringResource(R.string.bank_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.filter.importedOnly,
                        onClick = { viewModel.setImportedOnly(!state.filter.importedOnly) },
                        label = { Text(stringResource(R.string.bank_filter_imported)) },
                    )
                }
                items(TaskStatus.entries.toList(), key = { it.name }) { s ->
                    FilterChip(
                        selected = state.filter.status == s,
                        onClick = { viewModel.setStatus(if (state.filter.status == s) null else s) },
                        label = { Text(stringResource(statusLabel(s))) },
                    )
                }
            }
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(Difficulty.entries.toList(), key = { it.name }) { d ->
                    FilterChip(
                        selected = state.filter.difficulty == d,
                        onClick = { viewModel.setDifficulty(if (state.filter.difficulty == d) null else d) },
                        label = { Text(stringResource(difficultyLabel(d))) },
                    )
                }
                items(state.topics, key = { "topic-" + it.id }) { t ->
                    FilterChip(
                        selected = state.filter.topicId == t.id,
                        onClick = { viewModel.setTopic(if (state.filter.topicId == t.id) null else t.id) },
                        label = { Text(t.title) },
                    )
                }
            }
        }
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    stringResource(R.string.bank_count, state.tasks.size, state.totalCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.filter.isActive) {
                    TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.bank_clear_filters)) }
                }
            }
        }
        if (state.tasks.isEmpty()) {
            item {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(if (state.totalCount == 0) R.string.bank_empty else R.string.bank_no_matches),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.totalCount == 0) {
                        TextButton(onClick = onGoToImport) { Text(stringResource(R.string.bank_go_import)) }
                    }
                }
            }
        }
        items(state.tasks, key = { it.id }) { task ->
            TaskListItem(task, topicTitles, onClick = { onOpenTask(task.id) })
            HorizontalDivider()
        }
    }
}

@Composable
fun TaskListItem(task: Task, topicTitles: Map<String, String>, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(task.title) },
        supportingContent = {
            val parts = buildList {
                task.difficulty?.let { add(stringResource(difficultyLabel(it))) }
                val topics = task.topics.map { topicTitles[it] ?: it }.sorted()
                if (topics.isNotEmpty()) add(topics.joinToString(", "))
                add(stringResource(statusLabel(task.status)))
            }
            Text(parts.joinToString(" · "), maxLines = 2)
        },
        trailingContent = {
            if (task.mentions.isNotEmpty()) {
                Text(
                    stringResource(R.string.bank_mentions_badge, task.mentions.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

fun statusLabel(s: TaskStatus): Int = when (s) {
    TaskStatus.NEW -> R.string.status_new
    TaskStatus.LEARNING -> R.string.status_learning
    TaskStatus.REVIEW -> R.string.status_review
    TaskStatus.MASTERED -> R.string.status_mastered
    TaskStatus.FAILED_RECENTLY -> R.string.status_failed
}
