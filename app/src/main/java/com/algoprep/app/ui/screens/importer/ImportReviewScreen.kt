package com.algoprep.app.ui.screens.importer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.algoprep.app.R
import com.algoprep.app.domain.importer.CandidateDraft
import com.algoprep.app.domain.importer.DuplicateChoice
import com.algoprep.app.domain.importer.DuplicateTarget
import com.algoprep.app.domain.importer.ParserConfidence
import com.algoprep.app.domain.model.Difficulty
import com.algoprep.app.domain.model.ImportBatch
import com.algoprep.app.domain.model.Topic
import com.algoprep.app.ui.components.difficultyLabel
import kotlin.math.roundToInt

/** Step 3: nothing recognised by the parser is saved as truth; every field can be corrected first. */
@Composable
fun ImportReviewScreen(
    onBack: () -> Unit,
    onSaved: (ImportBatch) -> Unit,
    viewModel: ImportReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onSaved(it) } }

    val session = state.session
    if (state.loading) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    if (session == null) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.review_no_session))
            TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            IconButton(onClick = {
                viewModel.cancel()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
            Text(stringResource(R.string.review_title), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(
                        pluralStringResource(R.plurals.review_found, session.drafts.size, session.drafts.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        stringResource(R.string.review_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = viewModel::selectAll) { Text(stringResource(R.string.review_select_all)) }
                        TextButton(onClick = viewModel::selectNone) { Text(stringResource(R.string.review_select_none)) }
                        TextButton(onClick = viewModel::selectHighConfidence) { Text(stringResource(R.string.review_select_high)) }
                    }
                }
            }
            items(session.drafts, key = { it.tempId }) { draft ->
                CandidateCard(draft, state.topics, viewModel)
            }
        }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (session.undecidedDuplicates > 0) {
                Text(
                    stringResource(R.string.review_undecided, session.undecidedDuplicates),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (state.saveFailed) {
                Text(stringResource(R.string.review_save_failed), color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = viewModel::save,
                enabled = session.selectedCount > 0 && !state.saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(pluralStringResource(R.plurals.review_save, session.selectedCount, session.selectedCount))
            }
        }
    }
}

@Composable
private fun CandidateCard(draft: CandidateDraft, topics: List<Topic>, vm: ImportReviewViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (draft.selected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = draft.selected, onCheckedChange = { vm.setSelected(draft.tempId, it) })
                Text(stringResource(R.string.review_import_this), style = MaterialTheme.typography.labelLarge)
            }
            Text(
                stringResource(confidenceLabel(draft.confidence)) + if (draft.titleGuessed) " · " + stringResource(R.string.review_title_guessed) else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (draft.confidence == ParserConfidence.LOW) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.title,
                onValueChange = { vm.setTitle(draft.tempId, it) },
                label = { Text(stringResource(R.string.review_field_title)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.text,
                onValueChange = { vm.setText(draft.tempId, it) },
                label = { Text(stringResource(R.string.review_field_text)) },
                minLines = 2,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(stringResource(R.string.review_field_topic), style = MaterialTheme.typography.labelLarge)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(topics, key = { it.id }) { t ->
                    FilterChip(
                        selected = t.id in draft.topics,
                        onClick = { vm.toggleTopic(draft.tempId, t.id) },
                        label = { Text(t.title) },
                    )
                }
            }
            Text(stringResource(R.string.review_field_difficulty), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = draft.difficulty == null,
                    onClick = { vm.setDifficulty(draft.tempId, null) },
                    label = { Text(stringResource(R.string.detail_unknown)) },
                )
                Difficulty.entries.forEach { d ->
                    FilterChip(
                        selected = draft.difficulty == d,
                        onClick = { vm.setDifficulty(draft.tempId, d) },
                        label = { Text(stringResource(difficultyLabel(d))) },
                    )
                }
            }
            OutlinedTextField(
                value = draft.source.orEmpty(),
                onValueChange = { vm.setSource(draft.tempId, it) },
                label = { Text(stringResource(R.string.review_field_source)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.companyTag.orEmpty(),
                onValueChange = { vm.setCompany(draft.tempId, it) },
                label = { Text(stringResource(R.string.review_field_company)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            draft.duplicate?.let { dup ->
                DuplicateBox(
                    target = dup.target,
                    score = dup.score,
                    choice = draft.choice,
                    onChoice = { vm.setChoice(draft.tempId, it) },
                )
            }
        }
    }
}

@Composable
private fun DuplicateBox(target: DuplicateTarget, score: Double, choice: DuplicateChoice, onChoice: (DuplicateChoice) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.review_duplicate_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.review_duplicate_body), style = MaterialTheme.typography.bodyMedium)
            val percent = (score * 100).roundToInt()
            Text(
                when (target) {
                    is DuplicateTarget.Bank -> stringResource(R.string.review_duplicate_bank, target.title, percent)
                    is DuplicateTarget.Batch -> stringResource(R.string.review_duplicate_batch, target.title, percent)
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = choice == DuplicateChoice.MERGE,
                    onClick = { onChoice(DuplicateChoice.MERGE) },
                    label = { Text(stringResource(R.string.review_merge)) },
                )
                FilterChip(
                    selected = choice == DuplicateChoice.KEEP_SEPARATE,
                    onClick = { onChoice(DuplicateChoice.KEEP_SEPARATE) },
                    label = { Text(stringResource(R.string.review_keep_separate)) },
                )
            }
            if (choice == DuplicateChoice.MERGE) {
                Text(stringResource(R.string.review_merge_note), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun confidenceLabel(c: ParserConfidence) = when (c) {
    ParserConfidence.HIGH -> R.string.review_confidence_high
    ParserConfidence.MEDIUM -> R.string.review_confidence_medium
    ParserConfidence.LOW -> R.string.review_confidence_low
}

@Composable
fun ImportDoneScreen(saved: Int, merged: Int, skipped: Int, onOpenBank: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.done_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.done_saved, saved))
        Text(stringResource(R.string.done_merged, merged))
        Text(stringResource(R.string.done_skipped, skipped))
        Button(onClick = onOpenBank) { Text(stringResource(R.string.done_open_bank)) }
    }
}
