package com.algoprep.app.ui.screens.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoprep.app.R
import com.algoprep.app.domain.model.Mention
import com.algoprep.app.domain.model.Task
import com.algoprep.app.ui.components.difficultyLabel
import com.algoprep.app.ui.components.formatDuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TaskDetailScreen(onBack: () -> Unit, onSolve: (Long) -> Unit, viewModel: TaskDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val task = state.task
    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        if (state.loading || task == null) {
            if (state.loading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
            }
            return@Column
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(task.title, style = MaterialTheme.typography.headlineSmall)
            ProgressLine(task)
            MetadataCard(task, state.topicTitles, state.patternTitles)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.originalText, style = MaterialTheme.typography.bodyLarge)
                    if (task.constraints.isNotEmpty()) {
                        Text(stringResource(R.string.session_constraints), style = MaterialTheme.typography.titleSmall)
                        task.constraints.forEach { Text("• $it") }
                    }
                    task.examples.forEach { ex ->
                        Text(stringResource(R.string.session_example_input, ex.input), fontFamily = FontFamily.Monospace)
                        Text(stringResource(R.string.session_example_output, ex.output), fontFamily = FontFamily.Monospace)
                    }
                }
            }
            SolutionIdea(task)
            MentionsSection(task.mentions)
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.detail_notes)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { onSolve(task.id) }, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Text(stringResource(R.string.detail_solve_now))
            }
        }
    }
}

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun Instant.toLocalDateText(): String = atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormat)
private fun LocalDate.toText(): String = format(dateFormat)

@Composable
private fun ProgressLine(task: Task) {
    val parts = buildList {
        add(stringResource(statusLabel(task.status)))
        add(stringResource(R.string.detail_solved_times, task.timesSolved))
        task.lastSolvedAt?.let { add(stringResource(R.string.detail_last_solved, it.toLocalDateText())) }
        task.nextReviewAt?.let { add(stringResource(R.string.detail_next_review, it.toLocalDateText())) }
    }
    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MetadataCard(task: Task, topicTitles: Map<String, String>, patternTitles: Map<String, String>) {
    val unknown = stringResource(R.string.detail_unknown)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MetaRow(stringResource(R.string.detail_difficulty), task.difficulty?.let { stringResource(difficultyLabel(it)) } ?: unknown)
            MetaRow(stringResource(R.string.detail_time), task.estimatedSolveMin?.let { formatDuration(it) } ?: unknown)
            MetaRow(
                stringResource(R.string.detail_topics),
                task.topics.map { topicTitles[it] ?: it }.sorted().joinToString(", ").ifEmpty { unknown },
            )
            MetaRow(
                stringResource(R.string.detail_patterns),
                task.patterns.map { patternTitles[it] ?: it }.sorted().joinToString(", ").ifEmpty { unknown },
            )
            MetaRow(stringResource(R.string.detail_role), task.roleLevel ?: unknown)
            task.complexity?.let {
                MetaRow(stringResource(R.string.detail_complexity), stringResource(R.string.result_complexity, it.time ?: "—", it.space ?: "—"))
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.35f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.65f))
    }
}

@Composable
private fun SolutionIdea(task: Task) {
    val idea = task.solutionIdea ?: return
    var shown by remember { mutableStateOf(false) }
    if (shown) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(stringResource(R.string.result_solution_idea), style = MaterialTheme.typography.titleSmall)
                Text(idea)
            }
        }
    } else {
        TextButton(onClick = { shown = true }) { Text(stringResource(R.string.result_show_solution)) }
    }
}

@Composable
private fun MentionsSection(mentions: List<Mention>) {
    if (mentions.isEmpty()) return
    val unknown = stringResource(R.string.data_unknown_source)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.detail_mentions, mentions.size), style = MaterialTheme.typography.titleMedium)
        mentions.forEach { m ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(m.source ?: unknown, style = MaterialTheme.typography.titleSmall)
                    val extra = listOfNotNull(m.companyTag, m.interviewStage, m.roleLevel, m.reportedDate?.toText())
                    if (extra.isNotEmpty()) {
                        Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    m.sourceUrl?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }
}
