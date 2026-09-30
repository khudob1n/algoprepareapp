package com.algoprep.app.ui.screens.mock

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoprep.app.R
import com.algoprep.app.domain.mock.MockFormat
import com.algoprep.app.ui.components.formatDuration
import com.algoprep.app.ui.screens.onboarding.OptionCard
import com.algoprep.app.ui.screens.result.ResultFormFields
import com.algoprep.app.ui.screens.session.ProblemSection
import com.algoprep.app.ui.screens.session.formatTimer

@Composable
fun MockSetupScreen(
    onBack: () -> Unit,
    onStart: (taskIds: List<Long>, limitMinutes: Int, startedAtMillis: Long, plannedItemId: Long?) -> Unit,
    viewModel: MockSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is MockSetupEvent.Start -> onStart(e.taskIds, e.limitMinutes, e.startedAtMillis, viewModel.plannedItemId)
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.mock_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.mock_rules), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MockFormat.entries.forEach { format ->
                OptionCard(
                    title = stringResource(formatTitle(format)),
                    subtitle = stringResource(formatDescription(format)),
                    selected = state.format == format,
                    onClick = { viewModel.setFormat(format) },
                )
            }
            if (state.notEnoughTasks) {
                Text(stringResource(R.string.mock_not_enough, state.bankSize), color = MaterialTheme.colorScheme.tertiary)
            }
            Button(
                onClick = viewModel::start,
                enabled = state.bankSize > 0 && !state.starting,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text(stringResource(R.string.mock_begin)) }
        }
    }
}

@Composable
fun MockSessionScreen(
    onFinished: (sessionIds: List<Long>, limitMinutes: Int, totalSeconds: Long) -> Unit,
    onLeave: () -> Unit,
    viewModel: MockSessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val topicTitles by viewModel.topicTitles.collectAsStateWithLifecycle()
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmFinish by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { e ->
            when (e) {
                is MockSessionEvent.Finished -> onFinished(e.sessionIds, e.limitMinutes, e.totalSeconds)
            }
        }
    }
    BackHandler { confirmLeave = true }

    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val task = state.current
    if (state.loading || task == null) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatCountdown(state.remainingSec),
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = FontFamily.Monospace,
                    color = if (state.overtime) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(if (state.overtime) R.string.mock_overtime else R.string.mock_time_left),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.tasks.size > 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.tasks.forEachIndexed { i, _ ->
                    FilterChip(
                        selected = state.index == i,
                        onClick = { viewModel.select(i) },
                        label = { Text(stringResource(R.string.mock_task_n, i + 1)) },
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProblemSection(task, topicTitles)
            OutlinedTextField(
                value = state.notes.getOrElse(state.index) { "" },
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.session_notes)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.mock_no_hints),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { if (state.isLast) confirmFinish = true else viewModel.select(state.index + 1) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(if (state.isLast) R.string.mock_finish else R.string.mock_next_task)) }
        if (!state.isLast) {
            TextButton(onClick = { confirmFinish = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.mock_finish_now))
            }
        }
    }

    if (confirmFinish) {
        AlertDialog(
            onDismissRequest = { confirmFinish = false },
            title = { Text(stringResource(R.string.mock_finish_title)) },
            text = { Text(stringResource(R.string.mock_finish_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmFinish = false
                    viewModel.finish()
                }) { Text(stringResource(R.string.mock_finish)) }
            },
            dismissButton = { TextButton(onClick = { confirmFinish = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.mock_leave_title)) },
            text = { Text(stringResource(R.string.mock_leave_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    onLeave()
                }) { Text(stringResource(R.string.mock_leave)) }
            },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.mock_stay)) } },
        )
    }
}

@Composable
fun MockResultScreen(onDone: () -> Unit, viewModel: MockResultViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    if (state.loading) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.mock_result_title), style = MaterialTheme.typography.headlineMedium)
        Text(
            stringResource(R.string.mock_total_time, formatTimer(state.totalSeconds), formatTimer(state.limitMinutes * 60L)),
            style = MaterialTheme.typography.titleMedium,
        )
        if (state.overtimeSeconds > 0) {
            Text(
                stringResource(R.string.mock_over_limit, formatTimer(state.overtimeSeconds)),
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        if (state.saved) {
            Text(stringResource(R.string.mock_saved_note), style = MaterialTheme.typography.bodyMedium)
            state.rows.forEach { row ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(row.task.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            formatTimer(row.durationSec) + " · " + (row.form.outcome?.let { stringResource(outcomeText(it)) } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.mock_back_today)) }
        } else {
            Text(stringResource(R.string.mock_result_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.rows.forEachIndexed { i, row ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.mock_task_n, i + 1) + ": " + row.task.title, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.mock_task_time, formatTimer(row.durationSec)), style = MaterialTheme.typography.bodyMedium)
                        ResultFormFields(
                            form = row.form,
                            onOutcome = { viewModel.setOutcome(row.sessionId, it) },
                            onConfidence = { viewModel.setConfidence(row.sessionId, it) },
                            onToggleError = { viewModel.toggleError(row.sessionId, it) },
                            onOtherNote = { viewModel.setOtherNote(row.sessionId, it) },
                        )
                    }
                }
            }
            if (state.failed) Text(stringResource(R.string.review_save_failed), color = MaterialTheme.colorScheme.error)
            Button(
                onClick = viewModel::complete,
                enabled = state.canComplete && !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.result_complete)) }
        }
    }
}

private fun formatTitle(f: MockFormat) = when (f) {
    MockFormat.QUICK -> R.string.mock_format_quick
    MockFormat.FULL -> R.string.mock_format_full
}

private fun formatDescription(f: MockFormat) = when (f) {
    MockFormat.QUICK -> R.string.mock_format_quick_desc
    MockFormat.FULL -> R.string.mock_format_full_desc
}

private fun outcomeText(o: com.algoprep.app.domain.model.SolveOutcome) = when (o) {
    com.algoprep.app.domain.model.SolveOutcome.INDEPENDENT -> R.string.outcome_independent
    com.algoprep.app.domain.model.SolveOutcome.SMALL_HINT -> R.string.outcome_small_hint
    com.algoprep.app.domain.model.SolveOutcome.BIG_HINT -> R.string.outcome_big_hint
    com.algoprep.app.domain.model.SolveOutcome.SAW_SOLUTION -> R.string.outcome_saw_solution
    com.algoprep.app.domain.model.SolveOutcome.NOT_SOLVED -> R.string.outcome_not_solved
}
