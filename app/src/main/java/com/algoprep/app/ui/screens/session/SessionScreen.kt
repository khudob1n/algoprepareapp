package com.algoprep.app.ui.screens.session

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoprep.app.R
import com.algoprep.app.domain.model.SolveSession
import com.algoprep.app.domain.model.Task
import com.algoprep.app.ui.components.difficultyLabel
import com.algoprep.app.ui.components.formatDuration

@Composable
fun SessionScreen(
    onBack: () -> Unit,
    onResult: (sessionId: Long) -> Unit,
    onOpenOther: (SolveSession) -> Unit,
    viewModel: SessionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val topicTitles by viewModel.topicTitles.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SessionEvent.ToResult -> onResult(event.sessionId)
                is SessionEvent.OpenOther -> onOpenOther(event.session)
                SessionEvent.Closed -> onBack()
            }
        }
    }

    // Keep the screen on while the timer runs.
    val view = LocalView.current
    DisposableEffect(state.running) {
        view.keepScreenOn = state.running
        onDispose { view.keepScreenOn = false }
    }

    val task = state.task
    if (task == null || state.loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProblemSection(task, topicTitles)
            TimerCard(state)

            if (state.running) {
                HintSection(state, onReveal = viewModel::revealHint)
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                label = { Text(stringResource(R.string.session_notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            if (!state.running) {
                Button(
                    onClick = viewModel::start,
                    enabled = state.conflictingSession == null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.session_start)) }
            } else {
                Button(onClick = viewModel::finish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.session_finish))
                }
                TextButton(onClick = viewModel::cancel, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.session_cancel))
                }
            }
        }
    }

    state.conflictingSession?.let {
        AlertDialog(
            onDismissRequest = onBack,
            title = { Text(stringResource(R.string.session_conflict_title)) },
            text = { Text(stringResource(R.string.session_conflict_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::resumeConflicting) { Text(stringResource(R.string.session_conflict_resume)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::discardConflicting) { Text(stringResource(R.string.session_conflict_discard)) }
            },
        )
    }
}

@Composable
private fun ProblemSection(task: Task, topicTitles: Map<String, String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(task.title, style = MaterialTheme.typography.headlineSmall)
        val meta = buildList {
            task.difficulty?.let { add(stringResource(difficultyLabel(it))) }
            task.estimatedSolveMin?.let { add("≈ " + formatDuration(it)) }
            addAll(task.topics.map { topicTitles[it] ?: it })
        }
        if (meta.isNotEmpty()) {
            Text(
                meta.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(task.originalText, style = MaterialTheme.typography.bodyLarge)
                if (task.constraints.isNotEmpty()) {
                    Text(stringResource(R.string.session_constraints), style = MaterialTheme.typography.titleSmall)
                    task.constraints.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                }
                task.examples.forEach { ex ->
                    Text(stringResource(R.string.session_example), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.session_example_input, ex.input), fontFamily = FontFamily.Monospace)
                    Text(stringResource(R.string.session_example_output, ex.output), fontFamily = FontFamily.Monospace)
                    ex.explanation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun TimerCard(state: SessionUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatTimer(state.elapsedSec),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace,
                color = if (state.isOvertime) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            )
            val estimate = state.task?.estimatedSolveMin
            Text(
                text = when {
                    !state.running && estimate != null -> stringResource(R.string.session_timer_ready_estimate, formatDuration(estimate))
                    !state.running -> stringResource(R.string.session_timer_ready)
                    state.isOvertime -> stringResource(R.string.session_timer_overtime)
                    else -> stringResource(R.string.session_timer_running)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HintSection(state: SessionUiState, onReveal: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.revealedHints.forEachIndexed { index, hint ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.session_hint_label, index + 1),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(hint, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (state.allHints.isEmpty()) {
            Text(
                stringResource(R.string.session_no_hints),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onReveal, enabled = state.canRevealHint) {
                    Text(stringResource(R.string.session_need_hint, state.hintsRevealed, state.allHints.size))
                }
            }
        }
    }
}
