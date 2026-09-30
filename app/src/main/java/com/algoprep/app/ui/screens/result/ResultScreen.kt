package com.algoprep.app.ui.screens.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoprep.app.R
import com.algoprep.app.domain.model.ErrorType
import com.algoprep.app.domain.model.SolveOutcome
import com.algoprep.app.ui.screens.onboarding.OptionCard
import com.algoprep.app.ui.screens.session.formatTimer

@Composable
fun ResultScreen(onDone: () -> Unit, viewModel: ResultViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.done.collect { onDone() } }

    if (state.loading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }
        return
    }

    var showSolution by remember { mutableStateOf(false) }
    val form = state.form

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.result_title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(state.taskTitle, style = MaterialTheme.typography.headlineSmall)

        Text(stringResource(R.string.result_time), style = MaterialTheme.typography.titleSmall)
        Text(formatTimer(state.durationSec), style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)

        ResultFormFields(
            form = form,
            onOutcome = viewModel::setOutcome,
            onConfidence = viewModel::setConfidence,
            onToggleError = viewModel::toggleError,
            onOtherNote = viewModel::setOtherNote,
        )

        if (state.solutionIdea != null) {
            if (showSolution) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.result_solution_idea), style = MaterialTheme.typography.titleSmall)
                        Text(state.solutionIdea.orEmpty())
                        state.complexity?.let {
                            Text(
                                stringResource(R.string.result_complexity, it.time ?: "—", it.space ?: "—"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            } else {
                TextButton(onClick = { showSolution = true }) { Text(stringResource(R.string.result_show_solution)) }
            }
        }

        Button(
            onClick = viewModel::complete,
            enabled = form.canComplete && !state.saving,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(stringResource(R.string.result_complete)) }
    }
}

private fun outcomeLabel(o: SolveOutcome) = when (o) {
    SolveOutcome.INDEPENDENT -> R.string.outcome_independent
    SolveOutcome.SMALL_HINT -> R.string.outcome_small_hint
    SolveOutcome.BIG_HINT -> R.string.outcome_big_hint
    SolveOutcome.SAW_SOLUTION -> R.string.outcome_saw_solution
    SolveOutcome.NOT_SOLVED -> R.string.outcome_not_solved
}

fun errorTypeLabel(t: ErrorType) = when (t) {
    ErrorType.PATTERN -> R.string.error_type_pattern
    ErrorType.IMPLEMENTATION -> R.string.error_type_implementation
    ErrorType.EDGE_CASES -> R.string.error_type_edge_cases
    ErrorType.COMPLEXITY -> R.string.error_type_complexity
    ErrorType.OTHER -> R.string.error_type_other
}

/** How did you solve it / confidence / mistakes. Shared by the normal result screen and the mock interview. */
@Composable
fun ResultFormFields(
    form: ResultForm,
    onOutcome: (SolveOutcome) -> Unit,
    onConfidence: (Int) -> Unit,
    onToggleError: (ErrorType) -> Unit,
    onOtherNote: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.result_how), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        SolveOutcome.entries.forEach { outcome ->
            OptionCard(
                title = stringResource(outcomeLabel(outcome)),
                selected = form.outcome == outcome,
                onClick = { onOutcome(outcome) },
            )
        }

        Text(stringResource(R.string.result_confidence), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { value ->
                FilterChip(
                    selected = form.confidence == value,
                    onClick = { onConfidence(value) },
                    label = { Text(value.toString()) },
                )
            }
        }
        Text(
            stringResource(R.string.result_confidence_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(stringResource(R.string.result_errors), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        ErrorType.entries.forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = type in form.errors,
                        role = Role.Checkbox,
                        onValueChange = { onToggleError(type) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = type in form.errors, onCheckedChange = null)
                Text(stringResource(errorTypeLabel(type)), modifier = Modifier.padding(start = 8.dp))
            }
        }
        if (ErrorType.OTHER in form.errors) {
            OutlinedTextField(
                value = form.otherNote,
                onValueChange = onOtherNote,
                label = { Text(stringResource(R.string.result_other_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

    }
}
